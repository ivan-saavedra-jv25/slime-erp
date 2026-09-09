package cl.slimerp.inventario;

import cl.slimerp.catalogo.Producto;
import cl.slimerp.catalogo.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InventarioConsultaServiceTest {

    private ProductoRepository productoRepository;
    private StockProductoBodegaRepository stockRepository;
    private BodegaRepository bodegaRepository;
    private InventarioConsultaService service;

    private Producto producto(long id, String nombre, String sku, String codigoBarra) {
        return Producto.builder().id(id).tenantId(1L).nombre(nombre).sku(sku)
                .codigoBarra(codigoBarra).precioVenta(BigDecimal.TEN).activo(true).build();
    }

    private StockProductoBodega stock(long productoId, long bodegaId, String cantidad) {
        return StockProductoBodega.builder().tenantId(1L).productoId(productoId).bodegaId(bodegaId)
                .cantidad(new BigDecimal(cantidad)).build();
    }

    @BeforeEach
    void setUp() {
        productoRepository = mock(ProductoRepository.class);
        stockRepository = mock(StockProductoBodegaRepository.class);
        bodegaRepository = mock(BodegaRepository.class);
        service = new InventarioConsultaService(productoRepository, stockRepository, bodegaRepository);

        when(bodegaRepository.findByTenantIdAndActivoTrue(1L)).thenReturn(List.of(
                Bodega.builder().id(10L).tenantId(1L).nombre("Bodega A").tipo(TipoBodega.PRINCIPAL).activo(true).build(),
                Bodega.builder().id(20L).tenantId(1L).nombre("Bodega B").tipo(TipoBodega.BODEGAJE).activo(true).build()
        ));
    }

    @Test
    void sinBodegaSumaElStockDeTodasLasBodegasActivas() {
        Producto p1 = producto(1L, "Mouse", "SKU-1", null);
        when(productoRepository.findAll(any(Specification.class))).thenReturn(List.of(p1));
        when(stockRepository.findByTenantId(1L)).thenReturn(List.of(
                stock(1L, 10L, "3"),
                stock(1L, 20L, "5")
        ));

        var resultado = service.consultar(1L, null, null, null, false, TipoBusquedaInventario.NOMBRE, "",
                "nombre", "asc", 0, 10);

        assertEquals(1, resultado.contenido().size());
        assertEquals(new BigDecimal("8"), resultado.contenido().get(0).stock());
    }

    @Test
    void conBodegaEspecificaUsaSoloLaCantidadDeEsaBodega() {
        Producto p1 = producto(1L, "Mouse", "SKU-1", null);
        when(productoRepository.findAll(any(Specification.class))).thenReturn(List.of(p1));
        when(stockRepository.findByTenantIdAndBodegaId(1L, 10L)).thenReturn(List.of(stock(1L, 10L, "3")));

        var resultado = service.consultar(1L, 10L, null, null, false, TipoBusquedaInventario.NOMBRE, "",
                "nombre", "asc", 0, 10);

        assertEquals(new BigDecimal("3"), resultado.contenido().get(0).stock());
    }

    @Test
    void unProductoSinRegistroDeStockQuedaEnCero() {
        Producto p1 = producto(1L, "Mouse", "SKU-1", null);
        when(productoRepository.findAll(any(Specification.class))).thenReturn(List.of(p1));
        when(stockRepository.findByTenantId(1L)).thenReturn(List.of());

        var resultado = service.consultar(1L, null, null, null, false, TipoBusquedaInventario.NOMBRE, "",
                "nombre", "asc", 0, 10);

        assertEquals(BigDecimal.ZERO, resultado.contenido().get(0).stock());
    }

    @Test
    void ordenaPorStockDescendente() {
        Producto p1 = producto(1L, "A", "SKU-1", null);
        Producto p2 = producto(2L, "B", "SKU-2", null);
        when(productoRepository.findAll(any(Specification.class))).thenReturn(List.of(p1, p2));
        when(stockRepository.findByTenantId(1L)).thenReturn(List.of(stock(1L, 10L, "2"), stock(2L, 10L, "9")));

        var resultado = service.consultar(1L, null, null, null, false, TipoBusquedaInventario.NOMBRE, "",
                "stock", "desc", 0, 10);

        assertEquals(2L, resultado.contenido().get(0).productoId());
        assertEquals(1L, resultado.contenido().get(1).productoId());
    }

    @Test
    void laPaginacionRecortaSobreLaListaYaOrdenada() {
        Producto p1 = producto(1L, "A", null, null);
        Producto p2 = producto(2L, "B", null, null);
        Producto p3 = producto(3L, "C", null, null);
        when(productoRepository.findAll(any(Specification.class))).thenReturn(List.of(p1, p2, p3));
        when(stockRepository.findByTenantId(1L)).thenReturn(List.of());

        var pagina0 = service.consultar(1L, null, null, null, false, TipoBusquedaInventario.NOMBRE, "",
                "nombre", "asc", 0, 2);
        var pagina1 = service.consultar(1L, null, null, null, false, TipoBusquedaInventario.NOMBRE, "",
                "nombre", "asc", 1, 2);

        assertEquals(2, pagina0.contenido().size());
        assertEquals(3, pagina0.total());
        assertEquals(1, pagina1.contenido().size());
        assertEquals("C", pagina1.contenido().get(0).nombre());
    }

    @Test
    void unaPaginaFueraDeRangoDevuelveListaVacia() {
        Producto p1 = producto(1L, "A", null, null);
        when(productoRepository.findAll(any(Specification.class))).thenReturn(List.of(p1));
        when(stockRepository.findByTenantId(1L)).thenReturn(List.of());

        var resultado = service.consultar(1L, null, null, null, false, TipoBusquedaInventario.NOMBRE, "",
                "nombre", "asc", 5, 10);

        assertTrue(resultado.contenido().isEmpty());
        assertEquals(1, resultado.total());
    }

    @Test
    void consultarTodoIgnoraPaginacionYOrdenaPorNombre() {
        Producto p1 = producto(1L, "Zapato", null, null);
        Producto p2 = producto(2L, "Alambre", null, null);
        when(productoRepository.findAll(any(Specification.class))).thenReturn(List.of(p1, p2));
        when(stockRepository.findByTenantId(1L)).thenReturn(List.of());

        List<InventarioConsultaItem> items = service.consultarTodo(1L, null, null, null, false,
                TipoBusquedaInventario.NOMBRE, "");

        assertEquals(2, items.size());
        assertEquals("Alambre", items.get(0).nombre());
    }
}
