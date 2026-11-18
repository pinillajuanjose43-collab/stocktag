package com.stocktag.config;

import com.stocktag.model.Empresa;
import com.stocktag.model.Movimiento;
import com.stocktag.model.Producto;
import com.stocktag.model.Rol;
import com.stocktag.model.TipoMovimiento;
import com.stocktag.model.Usuario;
import com.stocktag.repository.EmpresaRepository;
import com.stocktag.repository.MovimientoRepository;
import com.stocktag.repository.ProductoRepository;
import com.stocktag.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Datos iniciales al arrancar la aplicación:
 * <ul>
 *   <li>El usuario ADMIN_PLATAFORMA inicial (credenciales en application.properties, stocktag.admin.*).</li>
 *   <li>Una empresa de ejemplo con usuario "tienda" / "tienda123" y 8 productos, para probar el inventario.
 *       Se desactiva con {@code stocktag.datos-ejemplo=false}.</li>
 * </ul>
 * Ambos se crean solo si no existen, así que reiniciar no duplica nada.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    static final String USUARIO_EJEMPLO = "tienda";
    private static final String CLAVE_EJEMPLO = "tienda123";

    /** Código de barras, nombre, stock mínimo, cantidad. Tres quedan con stock bajo y uno agotado. */
    private static final Object[][] PRODUCTOS_EJEMPLO = {
            {"7701000000011", "Arroz blanco 500 g", 10, 34},
            {"7701000000028", "Aceite de girasol 1 L", 6, 4},
            {"7701000000035", "Azúcar 1 kg", 8, 15},
            {"7701000000042", "Café molido 250 g", 5, 5},
            {"7701000000059", "Leche entera 1 L", 12, 0},
            {"7701000000066", "Panela 500 g", 6, 20},
            {"7701000000073", "Jabón de barra", 4, 9},
            {"7701000000080", "Huevos x 30", 3, 2},
    };

    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final ProductoRepository productoRepository;
    private final MovimientoRepository movimientoRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;
    private final boolean datosEjemplo;

    public DataInitializer(UsuarioRepository usuarioRepository,
                           EmpresaRepository empresaRepository,
                           ProductoRepository productoRepository,
                           MovimientoRepository movimientoRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${stocktag.admin.username}") String adminUsername,
                           @Value("${stocktag.admin.password}") String adminPassword,
                           @Value("${stocktag.datos-ejemplo:true}") boolean datosEjemplo) {
        this.usuarioRepository = usuarioRepository;
        this.empresaRepository = empresaRepository;
        this.productoRepository = productoRepository;
        this.movimientoRepository = movimientoRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.datosEjemplo = datosEjemplo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        crearAdminSiNoExiste();
        if (datosEjemplo) {
            crearEmpresaDeEjemploSiNoExiste();
        }
    }

    private void crearAdminSiNoExiste() {
        if (usuarioRepository.existsByRol(Rol.ADMIN_PLATAFORMA)) {
            return;
        }
        Usuario admin = new Usuario(adminUsername, passwordEncoder.encode(adminPassword),
                Rol.ADMIN_PLATAFORMA, null);
        usuarioRepository.save(admin);
        log.info("Usuario ADMIN_PLATAFORMA inicial creado: {}", adminUsername);
    }

    /** Empresa de ejemplo con sus productos. El stock inicial queda registrado como ENTRADA. */
    private void crearEmpresaDeEjemploSiNoExiste() {
        if (usuarioRepository.findByUsername(USUARIO_EJEMPLO).isPresent()) {
            return;
        }
        Empresa empresa = empresaRepository.save(new Empresa("Tienda La Esquina", "900555666-1"));
        usuarioRepository.save(new Usuario(USUARIO_EJEMPLO, passwordEncoder.encode(CLAVE_EJEMPLO),
                Rol.EMPRESA, empresa));

        for (Object[] datos : PRODUCTOS_EJEMPLO) {
            Producto producto = new Producto((String) datos[0], (String) datos[1], (Integer) datos[2], empresa);
            int cantidad = (Integer) datos[3];
            producto.setCantidad(cantidad);
            productoRepository.save(producto);
            if (cantidad > 0) {
                movimientoRepository.save(new Movimiento(producto, TipoMovimiento.ENTRADA, cantidad));
            }
        }
        log.info("Empresa de ejemplo creada: usuario '{}' con {} productos", USUARIO_EJEMPLO, PRODUCTOS_EJEMPLO.length);
    }
}
