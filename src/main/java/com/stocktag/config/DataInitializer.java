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

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.stocktag.model.TipoMovimiento.ENTRADA;
import static com.stocktag.model.TipoMovimiento.SALIDA;

/**
 * Datos iniciales al arrancar la aplicación:
 * <ul>
 *   <li>El usuario ADMIN_PLATAFORMA inicial (credenciales en application.properties, stocktag.admin.*).</li>
 *   <li>Una empresa de ejemplo ("Tienda La Esquina", usuario "tienda" / "tienda123") con 8 productos y
 *       el historial de entradas y salidas de las últimas dos semanas, para que el dashboard, las alertas,
 *       el historial y el CSV se vean con datos. Se desactiva con {@code stocktag.datos-ejemplo=false}.</li>
 * </ul>
 * Ambos se crean solo si no existen, así que reiniciar no duplica nada.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    static final String USUARIO_EJEMPLO = "tienda";
    private static final String CLAVE_EJEMPLO = "tienda123";

    /**
     * Movimiento de ejemplo: hace {@code dias} días a la hora {@code hora} ("HH:mm"). Con {@code dias = 0}
     * la hora se ignora y el movimiento queda {@code minutos} minutos antes del arranque, para que nunca
     * quede en el futuro.
     */
    private record MovimientoEjemplo(int dias, String hora, int minutos, TipoMovimiento tipo, int cantidad) {

        LocalDateTime fecha(LocalDateTime ahora) {
            if (dias == 0) {
                return ahora.minusMinutes(minutos);
            }
            return ahora.toLocalDate().minusDays(dias).atTime(LocalTime.parse(hora));
        }
    }

    private static MovimientoEjemplo hace(int dias, String hora, TipoMovimiento tipo, int cantidad) {
        return new MovimientoEjemplo(dias, hora, 0, tipo, cantidad);
    }

    private static MovimientoEjemplo hoyHaceMinutos(int minutos, TipoMovimiento tipo, int cantidad) {
        return new MovimientoEjemplo(0, null, minutos, tipo, cantidad);
    }

    /**
     * Producto de ejemplo con su historial. La cantidad final sale de aplicar los movimientos,
     * así el stock y el historial siempre cuadran.
     */
    private record ProductoEjemplo(String codigoBarras, String nombre, int stockMinimo, List<MovimientoEjemplo> movimientos) {
    }

    /**
     * Ocho productos con dos semanas de historial. Al final quedan: tres con stock bajo
     * (Aceite, Café, Huevos), uno agotado (Leche) y cuatro con stock normal.
     */
    private static final List<ProductoEjemplo> PRODUCTOS_EJEMPLO = List.of(
            new ProductoEjemplo("7701000000011", "Arroz blanco 500 g", 10, List.of(
                    hace(13, "08:15", ENTRADA, 40), hace(11, "10:30", SALIDA, 6), hace(9, "16:45", SALIDA, 8),
                    hace(7, "09:00", ENTRADA, 24), hace(5, "11:20", SALIDA, 10), hace(3, "17:10", SALIDA, 4),
                    hace(1, "12:05", SALIDA, 2))),                                                     // 34
            new ProductoEjemplo("7701000000028", "Aceite de girasol 1 L", 6, List.of(
                    hace(13, "08:20", ENTRADA, 18), hace(10, "15:00", SALIDA, 5), hace(8, "11:40", SALIDA, 4),
                    hace(4, "18:30", SALIDA, 3), hace(2, "10:15", SALIDA, 2))),                        // 4, stock bajo
            new ProductoEjemplo("7701000000035", "Azúcar 1 kg", 8, List.of(
                    hace(12, "09:10", ENTRADA, 20), hace(9, "14:25", SALIDA, 6), hace(6, "08:50", ENTRADA, 10),
                    hace(3, "13:35", SALIDA, 5), hoyHaceMinutos(140, SALIDA, 4))),                     // 15
            new ProductoEjemplo("7701000000042", "Café molido 250 g", 5, List.of(
                    hace(12, "09:30", ENTRADA, 12), hace(8, "17:00", SALIDA, 3), hace(5, "10:10", SALIDA, 2),
                    hace(2, "16:20", SALIDA, 2))),                                                     // 5, stock bajo
            new ProductoEjemplo("7701000000059", "Leche entera 1 L", 12, List.of(
                    hace(13, "07:45", ENTRADA, 24), hace(10, "08:30", SALIDA, 10), hace(7, "07:50", ENTRADA, 12),
                    hace(6, "18:15", SALIDA, 9), hace(4, "08:05", SALIDA, 8), hace(1, "19:30", SALIDA, 9))), // 0, agotado
            new ProductoEjemplo("7701000000066", "Panela 500 g", 6, List.of(
                    hace(11, "10:00", ENTRADA, 15), hace(8, "12:30", SALIDA, 4), hace(5, "09:20", ENTRADA, 15),
                    hace(2, "11:45", SALIDA, 6))),                                                     // 20
            new ProductoEjemplo("7701000000073", "Jabón de barra", 4, List.of(
                    hace(10, "14:00", ENTRADA, 12), hace(6, "16:40", SALIDA, 2), hoyHaceMinutos(90, SALIDA, 1))), // 9
            new ProductoEjemplo("7701000000080", "Huevos x 30", 3, List.of(
                    hace(13, "08:00", ENTRADA, 6), hace(9, "09:45", SALIDA, 2), hace(7, "08:40", ENTRADA, 6),
                    hace(4, "10:30", SALIDA, 5), hoyHaceMinutos(25, SALIDA, 3)))                       // 2, stock bajo
    );

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

    /** Empresa de ejemplo con sus productos y su historial de entradas y salidas. */
    private void crearEmpresaDeEjemploSiNoExiste() {
        if (usuarioRepository.findByUsername(USUARIO_EJEMPLO).isPresent()) {
            return;
        }
        Empresa empresa = empresaRepository.save(new Empresa("Tienda La Esquina", "900555666-1"));
        usuarioRepository.save(new Usuario(USUARIO_EJEMPLO, passwordEncoder.encode(CLAVE_EJEMPLO),
                Rol.EMPRESA, empresa));

        LocalDateTime ahora = LocalDateTime.now();
        List<Movimiento> historial = new ArrayList<>();
        for (ProductoEjemplo datos : PRODUCTOS_EJEMPLO) {
            Producto producto = new Producto(datos.codigoBarras(), datos.nombre(), datos.stockMinimo(), empresa);
            int cantidad = 0;
            for (MovimientoEjemplo m : datos.movimientos()) {
                cantidad += m.tipo() == ENTRADA ? m.cantidad() : -m.cantidad();
                if (cantidad < 0) {
                    throw new IllegalStateException("Datos de ejemplo inválidos: stock negativo en " + datos.nombre());
                }
                historial.add(new Movimiento(producto, m.tipo(), m.cantidad(), m.fecha(ahora)));
            }
            producto.setCantidad(cantidad);
            productoRepository.save(producto);
        }
        // Se guardan en orden cronológico para que los ids sigan el mismo orden que las fechas.
        historial.sort(Comparator.comparing(Movimiento::getFecha));
        movimientoRepository.saveAll(historial);
        log.info("Empresa de ejemplo creada: usuario '{}' con {} productos y {} movimientos",
                USUARIO_EJEMPLO, PRODUCTOS_EJEMPLO.size(), historial.size());
    }
}
