package cl.slimerp.catalogo;

import cl.slimerp.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductoControllerTest {

    private ProductoRepository productoRepository;
    private ProductoController controller;

    @BeforeEach
    void setUp() {
        productoRepository = mock(ProductoRepository.class);
        controller = new ProductoController(productoRepository);
        TenantContext.setTenantId(1L);
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void crearUsaCerosPorDefectoParaCamposOpcionalesNulos() {
        var request = new ProductoRequest("SKU-1", "Producto Uno", "desc", new BigDecimal("1000"), null, null, true);

        var response = controller.crear(request);

        assertEquals(BigDecimal.ZERO, response.getBody().getPrecioCompra());
        assertEquals(BigDecimal.ZERO, response.getBody().getStock());
        assertEquals(1L, response.getBody().getTenantId());
    }

    @Test
    void eliminarHaceSoftDelete() {
        Producto existente = Producto.builder().id(7L).tenantId(1L).nombre("Producto Dos").activo(true).build();
        when(productoRepository.findByIdAndTenantIdAndActivoTrue(7L, 1L)).thenReturn(Optional.of(existente));

        var response = controller.eliminar(7L);

        assertEquals(204, response.getStatusCode().value());
        assertFalse(existente.isActivo());
    }

    @Test
    void actualizarDevuelve404SiNoExisteEnElTenant() {
        when(productoRepository.findByIdAndTenantIdAndActivoTrue(99L, 1L)).thenReturn(Optional.empty());

        var response = controller.actualizar(99L,
                new ProductoRequest("SKU-X", "X", null, BigDecimal.TEN, null, null, true));

        assertEquals(404, response.getStatusCode().value());
    }
}
