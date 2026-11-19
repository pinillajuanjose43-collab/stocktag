package com.stocktag.service;

import com.stocktag.model.Empresa;
import com.stocktag.model.Rol;
import com.stocktag.model.Usuario;
import com.stocktag.repository.EmpresaRepository;
import com.stocktag.repository.EmpresaResumen;
import com.stocktag.repository.UsuarioRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Gestión de empresas del panel /admin (solo ADMIN_PLATAFORMA, protegido en SecurityConfig).
 * Aquí viven todas las reglas: el controller solo arma la vista.
 */
@Service
@Transactional
public class EmpresaService {

    public static final int LONGITUD_MINIMA_CLAVE = 8;

    /** BCrypt solo usa los primeros 72 bytes de la contraseña. */
    public static final int MAXIMO_BYTES_CLAVE = 72;

    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventos;

    public EmpresaService(EmpresaRepository empresaRepository,
                          UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder,
                          ApplicationEventPublisher eventos) {
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventos = eventos;
    }

    /**
     * Crea, en una sola transacción, la empresa (activa, con fecha de alta de hoy) y su usuario
     * con rol EMPRESA y la contraseña cifrada con BCrypt. Si algo falla no se crea ninguno de los dos.
     *
     * @throws EmpresaException si falta un dato, la contraseña es muy corta, o el NIT o el usuario ya existen
     */
    public Empresa registrarEmpresa(String nombre, String nit, String username, String password) {
        String nombreLimpio = obligatorio(nombre, "nombre", "Escribe el nombre de la empresa.");
        String nitLimpio = normalizarNit(obligatorio(nit, "nit", "Escribe el NIT de la empresa."));
        String usuarioLimpio = obligatorio(username, "username", "Escribe el usuario de la empresa.");
        validarClave(password);

        if (empresaRepository.existsByNit(nitLimpio)) {
            throw new EmpresaException("nit", "Ya existe una empresa registrada con el NIT " + nitLimpio + ".");
        }
        if (usuarioRepository.existsByUsernameIgnoreCase(usuarioLimpio)) {
            throw new EmpresaException("username",
                    "El usuario \"" + usuarioLimpio + "\" ya está en uso. Elige otro.");
        }

        try {
            Empresa empresa = empresaRepository.save(new Empresa(nombreLimpio, nitLimpio));
            usuarioRepository.saveAndFlush(new Usuario(usuarioLimpio, passwordEncoder.encode(password),
                    Rol.EMPRESA, empresa));
            return empresa;
        } catch (DataIntegrityViolationException e) {
            // Otro registro con el mismo NIT o usuario entró entre la verificación y el guardado.
            throw new EmpresaException(null,
                    "No se pudo registrar la empresa: el NIT o el usuario se acaban de registrar. Revisa los datos.");
        }
    }

    /**
     * Activa o desactiva una empresa. Una empresa desactivada no puede iniciar sesión y, al
     * confirmarse la transacción, sus sesiones abiertas quedan expiradas (pierden el acceso
     * en la siguiente petición).
     *
     * @throws EmpresaException si la empresa no existe
     */
    public Empresa cambiarEstadoEmpresa(Long empresaId, boolean activa) {
        Empresa empresa = (empresaId == null ? null : empresaRepository.findById(empresaId).orElse(null));
        if (empresa == null) {
            throw new EmpresaException(null, "La empresa indicada no existe.");
        }
        empresa.setActiva(activa);
        if (!activa) {
            // Se publica aunque ya estuviera desactivada: cerrar sesiones de más nunca sobra.
            eventos.publishEvent(new EmpresaDesactivadaEvent(empresa.getId()));
        }
        return empresa;
    }

    /**
     * Cambia la contraseña del usuario de una empresa (la guarda con BCrypt) y, al confirmarse
     * la transacción, cierra sus sesiones abiertas: la contraseña anterior deja de servir de
     * inmediato. Funciona también con empresas desactivadas, que siguen sin poder entrar.
     *
     * <p>Solo el ADMIN_PLATAFORMA puede usarlo. La ruta ya está protegida en SecurityConfig;
     * se revisa también aquí para que ningún otro llamado pueda cambiar contraseñas.
     *
     * @return el usuario con la contraseña nueva
     * @throws EmpresaException si la empresa no existe, no tiene usuario o la contraseña no es válida
     * @throws AccessDeniedException si quien lo pide no es el administrador de la plataforma
     */
    public Usuario restablecerClave(Long empresaId, String nuevaClave) {
        exigirAdministrador();
        Empresa empresa = (empresaId == null ? null : empresaRepository.findById(empresaId).orElse(null));
        if (empresa == null) {
            throw new EmpresaException(null, "La empresa indicada no existe.");
        }
        Usuario usuario = usuarioRepository.findFirstByEmpresaIdAndRol(empresa.getId(), Rol.EMPRESA)
                .orElseThrow(() -> new EmpresaException(null,
                        "La empresa \"" + empresa.getNombre() + "\" no tiene un usuario para iniciar sesión."));
        validarClave(nuevaClave);

        usuario.setPassword(passwordEncoder.encode(nuevaClave));
        eventos.publishEvent(new ClaveRestablecidaEvent(usuario.getUsername()));
        return usuario;
    }

    /** Empresas con su usuario y número de productos, ordenadas por nombre. */
    @Transactional(readOnly = true)
    public List<EmpresaResumen> listarEmpresas() {
        return empresaRepository.listarResumen();
    }

    private static void exigirAdministrador() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean esAdministrador = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + Rol.ADMIN_PLATAFORMA.name()).equals(a.getAuthority()));
        if (!esAdministrador) {
            throw new AccessDeniedException("Solo el administrador de la plataforma puede restablecer contraseñas.");
        }
    }

    private static String obligatorio(String valor, String campo, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw new EmpresaException(campo, mensaje);
        }
        return valor.trim();
    }

    /** "900.123.456 - 7" y "900123456-7" son el mismo NIT: se guarda sin puntos ni espacios. */
    static String normalizarNit(String nit) {
        return nit.replaceAll("[\\s.]", "");
    }

    private static void validarClave(String password) {
        if (password == null || password.length() < LONGITUD_MINIMA_CLAVE) {
            throw new EmpresaException("password",
                    "La contraseña debe tener al menos " + LONGITUD_MINIMA_CLAVE + " caracteres.");
        }
        if (password.isBlank()) {
            throw new EmpresaException("password", "La contraseña no puede ser solo espacios.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES_CLAVE) {
            throw new EmpresaException("password", "La contraseña es demasiado larga (máximo 72 caracteres sin tildes).");
        }
    }
}
