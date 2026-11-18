package com.stocktag.config;

import com.stocktag.model.Movimiento;
import com.stocktag.model.Producto;
import com.stocktag.model.TipoMovimiento;
import com.stocktag.model.Usuario;
import com.stocktag.repository.MovimientoRepository;
import com.stocktag.repository.ProductoRepository;
import com.stocktag.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La empresa de ejemplo (activada por defecto para la presentación) tiene productos y un historial
 * de entradas y salidas de los últimos días, coherente con el stock de cada producto.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stocktag-datos-ejemplo;DB_CLOSE_DELAY=-1"
})
@Transactional
class DatosEjemploTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private MovimientoRepository movimientoRepository;

    @Test
    void empresaDeEjemploTieneProductosAlertasYMovimientosRecientes() {
        Usuario tienda = usuarioRepository.findByUsername(DataInitializer.USUARIO_EJEMPLO).orElseThrow();
        Long empresaId = tienda.getEmpresa().getId();

        List<Producto> productos = productoRepository.findByEmpresaIdOrderByNombreAsc(empresaId);
        assertThat(productos).hasSize(8);
        assertThat(productoRepository.contarConStockBajo(empresaId)).isEqualTo(4);
        assertThat(productos).anyMatch(p -> p.getCantidad() == 0);

        List<Movimiento> movimientos = movimientoRepository.findByProductoEmpresaIdOrderByFechaDesc(empresaId);
        assertThat(movimientos).extracting(Movimiento::getTipo)
                .contains(TipoMovimiento.ENTRADA, TipoMovimiento.SALIDA);

        LocalDateTime ahora = LocalDateTime.now();
        assertThat(movimientos).allSatisfy(m -> assertThat(m.getFecha())
                .isBefore(ahora.plusSeconds(1))
                .isAfter(ahora.minusDays(15)));
        // Hay movimientos repartidos en varios días, no todos el mismo día.
        assertThat(movimientos.stream().map(m -> m.getFecha().toLocalDate()).distinct().count())
                .isGreaterThan(7);

        // El stock de cada producto es la suma de sus entradas menos sus salidas.
        for (Producto producto : productos) {
            int saldo = movimientos.stream()
                    .filter(m -> m.getProducto().getId().equals(producto.getId()))
                    .mapToInt(m -> m.getTipo() == TipoMovimiento.ENTRADA ? m.getCantidad() : -m.getCantidad())
                    .sum();
            assertThat(saldo).as(producto.getNombre()).isEqualTo(producto.getCantidad());
        }
    }
}
