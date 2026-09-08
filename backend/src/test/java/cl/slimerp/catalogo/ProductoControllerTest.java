package cl.slimerp.catalogo;

import cl.slimerp.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
        var request = new ProductoRequest("SKU-1", "Producto Uno", "desc", null, null, new BigDecimal("1000"), null, null, null);

        var response = controller.crear(request);

        assertEquals(BigDecimal.ZERO, response.getBody().getPrecioCompra());
        assertEquals(BigDecimal.ZERO, response.getBody().getStockMinimo());
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
                new ProductoRequest("SKU-X", "X", null, null, null, BigDecimal.TEN, null, null, null));

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void listarPaginaArmaElPatronLikeAPartirDelTermino() {
        Producto producto = Producto.builder().id(1L).tenantId(1L).nombre("Mouse Inalámbrico").activo(true).build();
        when(productoRepository.buscar(eq(1L), eq("%mouse%"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(producto), PageRequest.of(0, 10), 1));

        var respuesta = controller.listarPagina("Mouse", 0, 10);

        assertEquals(List.of(producto), respuesta.contenido());
        assertEquals(1, respuesta.total());
    }

    @Test
    void listarPaginaSinTerminoDeBusquedaUsaUnPatronQueCoincideConTodo() {
        when(productoRepository.buscar(eq(1L), eq("%%"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        var respuesta = controller.listarPagina(null, 0, 10);

        assertEquals(0, respuesta.total());
    }

    @Test
    void crearConCodigoBarraDuplicadoLanzaConflicto() {
        when(productoRepository.existsByTenantIdAndCodigoBarra(1L, "7801234567890")).thenReturn(true);
        var request = new ProductoRequest("SKU-2", "Producto Dos", null, null, null,
                new BigDecimal("500"), null, null, "7801234567890");

        assertThrows(ProductoConflictException.class, () -> controller.crear(request));
    }

    @Test
    void crearSinCodigoBarraNoValidaUnicidad() {
        var request = new ProductoRequest("SKU-3", "Producto Tres", null, null, null,
                new BigDecimal("500"), null, null, null);

        var response = controller.crear(request);

        assertEquals(200, response.getStatusCode().value());
        verify(productoRepository, never()).existsByTenantIdAndCodigoBarra(anyLong(), any());
    }

    @Test
    void listarPaginaBuscaTambienPorCodigoBarra() {
        Producto producto = Producto.builder().id(1L).tenantId(1L).nombre("Mouse").codigoBarra("7801234567890").activo(true).build();
        when(productoRepository.buscar(eq(1L), eq("%780123%"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(producto), PageRequest.of(0, 10), 1));

        var respuesta = controller.listarPagina("780123", 0, 10);

        assertEquals(1, respuesta.total());
    }
}
