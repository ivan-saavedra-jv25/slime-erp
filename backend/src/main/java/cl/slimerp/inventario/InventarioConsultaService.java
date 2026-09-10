package cl.slimerp.inventario;

import cl.slimerp.catalogo.Producto;
import cl.slimerp.catalogo.ProductoRepository;
import cl.slimerp.common.PaginaResponse;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// Combina Producto (filtrado con Specification) y StockProductoBodega
// (cantidad por bodega, o sumada entre las bodegas activas del tenant si no
// se filtró por una bodega puntual) para la pantalla de consulta de
// Inventario. El orden (incluyendo por "stock", que no es una columna de
// Producto) y la paginación se resuelven en memoria: a la escala esperada
// (catálogo de un tenant PyME) es simple y correcto; si se vuelve un cuello
// de botella, se puede migrar a una consulta nativa con GROUP BY/ORDER BY.
@Service
public class InventarioConsultaService {

    private final ProductoRepository productoRepository;
    private final StockProductoBodegaRepository stockRepository;
    private final BodegaRepository bodegaRepository;

    public InventarioConsultaService(ProductoRepository productoRepository,
                                      StockProductoBodegaRepository stockRepository,
                                      BodegaRepository bodegaRepository) {
        this.productoRepository = productoRepository;
        this.stockRepository = stockRepository;
        this.bodegaRepository = bodegaRepository;
    }

    public PaginaResponse<InventarioConsultaItem> consultar(
            Long tenantId, Long bodegaId, Long familiaId, Long subfamiliaId,
            boolean verDeshabilitados, TipoBusquedaInventario tipoBusqueda, String busqueda,
            String sort, String dir, int pagina, int tamano) {
        List<InventarioConsultaItem> items = obtenerItems(tenantId, bodegaId, familiaId, subfamiliaId,
                verDeshabilitados, tipoBusqueda, busqueda);
        items.sort(comparador(sort, dir));

        int paginaSegura = Math.max(0, pagina);
        int tamanoSeguro = Math.max(0, tamano);

        int total = items.size();
        int desde = Math.min(paginaSegura * tamanoSeguro, total);
        int hasta = Math.min(desde + tamanoSeguro, total);
        return new PaginaResponse<>(items.subList(desde, hasta), total);
    }

    public List<InventarioConsultaItem> consultarTodo(
            Long tenantId, Long bodegaId, Long familiaId, Long subfamiliaId,
            boolean verDeshabilitados, TipoBusquedaInventario tipoBusqueda, String busqueda) {
        List<InventarioConsultaItem> items = obtenerItems(tenantId, bodegaId, familiaId, subfamiliaId,
                verDeshabilitados, tipoBusqueda, busqueda);
        items.sort(comparador("nombre", "asc"));
        return items;
    }

    private List<InventarioConsultaItem> obtenerItems(
            Long tenantId, Long bodegaId, Long familiaId, Long subfamiliaId,
            boolean verDeshabilitados, TipoBusquedaInventario tipoBusqueda, String busqueda) {
        Specification<Producto> spec = especificacion(tenantId, familiaId, subfamiliaId, verDeshabilitados,
                tipoBusqueda, busqueda);
        List<Producto> productos = productoRepository.findAll(spec);
        Map<Long, BigDecimal> stockPorProducto = calcularStock(tenantId, bodegaId);

        return productos.stream()
                .map(p -> new InventarioConsultaItem(p.getId(), p.getNombre(), p.getSku(), p.getCodigoBarra(),
                        stockPorProducto.getOrDefault(p.getId(), BigDecimal.ZERO)))
                .collect(Collectors.toCollection(java.util.ArrayList::new));
    }

    private Specification<Producto> especificacion(Long tenantId, Long familiaId, Long subfamiliaId,
            boolean verDeshabilitados, TipoBusquedaInventario tipoBusqueda, String busqueda) {
        Specification<Producto> spec = (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
        if (!verDeshabilitados) {
            spec = spec.and((root, query, cb) -> cb.isTrue(root.get("activo")));
        }
        if (familiaId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("categoriaId"), familiaId));
        }
        if (subfamiliaId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("subcategoriaId"), subfamiliaId));
        }
        if (busqueda != null && !busqueda.isBlank()) {
            String patron = "%" + busqueda.trim().toLowerCase() + "%";
            String campo = switch (tipoBusqueda == null ? TipoBusquedaInventario.NOMBRE : tipoBusqueda) {
                case SKU -> "sku";
                case CODIGO_BARRA -> "codigoBarra";
                case NOMBRE -> "nombre";
            };
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(cb.coalesce(root.get(campo), "")), patron));
        }
        return spec;
    }

    // Cuando bodegaId es null ("Todos"), suma la cantidad de cada producto
    // entre todas las bodegas activas del tenant; si viene un bodegaId
    // puntual, usa directamente la cantidad en esa bodega.
    private Map<Long, BigDecimal> calcularStock(Long tenantId, Long bodegaId) {
        if (bodegaId != null) {
            return stockRepository.findByTenantIdAndBodegaId(tenantId, bodegaId).stream()
                    .collect(Collectors.toMap(StockProductoBodega::getProductoId, StockProductoBodega::getCantidad));
        }
        Set<Long> bodegasActivasIds = bodegaRepository.findByTenantIdAndActivoTrue(tenantId).stream()
                .map(Bodega::getId).collect(Collectors.toSet());
        return stockRepository.findByTenantId(tenantId).stream()
                .filter(s -> bodegasActivasIds.contains(s.getBodegaId()))
                .collect(Collectors.groupingBy(StockProductoBodega::getProductoId,
                        Collectors.reducing(BigDecimal.ZERO, StockProductoBodega::getCantidad, BigDecimal::add)));
    }

    private Comparator<InventarioConsultaItem> comparador(String sort, String dir) {
        Comparator<InventarioConsultaItem> comparador = switch (sort == null ? "nombre" : sort) {
            case "sku" -> Comparator.comparing(
                    (InventarioConsultaItem i) -> Optional.ofNullable(i.sku()).orElse(""), String.CASE_INSENSITIVE_ORDER);
            case "codigoBarra" -> Comparator.comparing(
                    (InventarioConsultaItem i) -> Optional.ofNullable(i.codigoBarra()).orElse(""), String.CASE_INSENSITIVE_ORDER);
            case "stock" -> Comparator.comparing(InventarioConsultaItem::stock);
            default -> Comparator.comparing(InventarioConsultaItem::nombre, String.CASE_INSENSITIVE_ORDER);
        };
        return "desc".equalsIgnoreCase(dir) ? comparador.reversed() : comparador;
    }
}
