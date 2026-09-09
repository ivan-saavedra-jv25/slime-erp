# Módulo Inventario (vista con filtros) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Agregar una pantalla de solo lectura (`/inventario`) para consultar y filtrar el stock de productos por bodega, familia (categoría) y subfamilia (subcategoría), con búsqueda por código de barra/SKU/nombre, orden por columna, paginación y exportación a CSV/XLSX.

**Architecture:** Nuevo endpoint backend `GET /api/inventario` (+ `/exportar.csv`, `/exportar.xlsx`) en el package `cl.slimerp.inventario`, que combina `Producto` (filtrado con `Specification`) y `StockProductoBodega` (cantidad por bodega o sumada entre bodegas) en memoria, ordena y pagina. Un componente Angular único `InventarioComponent`, siguiendo el mismo patrón que `ProductosComponent`/`BodegasComponent`, con tabla `mat-table` + `matSort` + `mat-paginator`.

**Tech Stack:** Spring Boot (Java 21), Spring Data JPA (`Specification`), Apache POI (`poi-ooxml`, ya en `pom.xml`), Angular 17+ standalone components, Angular Material (`table`, `paginator`, `sort`).

**Spec:** `docs/superpowers/specs/2026-09-09-modulo-inventario-design.md`

## Global Constraints

- El `id` de toda tabla es autoincremental generado por la base de datos — nunca se envía desde el frontend (regla del proyecto, `CLAUDE.md`).
- **No se agregan columnas ni migraciones nuevas.** Todo el módulo lee datos que ya existen.
- No usar el patrón JPQL `(:param IS NULL OR ...)` para filtros opcionales — Postgres no logra inferir el tipo del parámetro y falla (`could not determine data type`). Usar `Specification` dinámica en su lugar, como ya hace `TransaccionPagoService.buscar(...)`.
- Todo endpoint nuevo sigue el patrón multi-tenant existente (`TenantContext.getTenantId()`) y se protege con `@PreAuthorize("hasAuthority('BODEGAS_VER')")` — se reutiliza este permiso, no se crea uno nuevo.
- Errores de negocio → `IllegalArgumentException`, capturados por `GlobalExceptionHandler` (mensajes amigables, nunca detalle técnico crudo).
- Frontend: HTML nativo (`.form-group`, `input`/`select`) con los tokens de diseño existentes; Material solo para tabla, paginador, botones e íconos — mismo patrón que `productos.component.html`/`bodegas.component.html`. Sintaxis de control de flujo `@if`/`@for` (no `*ngIf`/`*ngFor`).
- Reutilizar `mostrarCargando`/`cerrarCargando` (`frontend/src/app/core/utils/swal-loading.ts`) para las descargas CSV/XLSX.
- Excepciones de generación de Excel: capturar `IOException` y relanzar como `UncheckedIOException` (mismo patrón que `LibroVentasExcelService.generar`).

---

## Backend

### Task 1: Habilitar `Specification` en `ProductoRepository`

**Files:**
- Modify: `backend/src/main/java/cl/slimerp/catalogo/ProductoRepository.java`
- Test: `backend/src/test/java/cl/slimerp/catalogo/ProductoRepositorySpecificationTest.java` — **no se crea**: este repo no tiene tests con base de datos real para `ProductoRepository` (los tests existentes mockean el repositorio); esta interfaz solo agrega una capacidad de Spring Data, no lógica propia que testear aquí. Se verifica indirectamente en el Task 2 (mockeando `findAll(any(Specification.class))`).

**Interfaces:**
- Produces: `ProductoRepository` ahora también expone `findAll(Specification<Producto> spec)` y `findAll(Specification<Producto> spec, Sort sort)` (heredados de `JpaSpecificationExecutor`), usados por `InventarioConsultaService` (Task 2).

- [ ] **Step 1: Agregar la interfaz `JpaSpecificationExecutor<Producto>`**

En `backend/src/main/java/cl/slimerp/catalogo/ProductoRepository.java`, cambiar:

```java
public interface ProductoRepository extends JpaRepository<Producto, Long> {
```

por:

```java
public interface ProductoRepository extends JpaRepository<Producto, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Producto> {
```

(Se usa el nombre completamente calificado en la declaración `extends` para no añadir un import adicional en un archivo que ya tiene varios; si se prefiere, se puede agregar `import org.springframework.data.jpa.repository.JpaSpecificationExecutor;` arriba y dejar `JpaSpecificationExecutor<Producto>` en la firma — cualquiera de las dos formas es válida, se recomienda el import explícito para legibilidad.)

- [ ] **Step 2: Verificar que compila**

Run: `cd backend && mvn -q compile`
Expected: BUILD SUCCESS (sin cambios de comportamiento, solo la interfaz nueva).

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/cl/slimerp/catalogo/ProductoRepository.java
git commit -m "feat: habilitar Specification en ProductoRepository para filtros de Inventario"
```

---

### Task 2: `TipoBusquedaInventario` + `InventarioConsultaItem`

**Files:**
- Create: `backend/src/main/java/cl/slimerp/inventario/TipoBusquedaInventario.java`
- Create: `backend/src/main/java/cl/slimerp/inventario/InventarioConsultaItem.java`

**Interfaces:**
- Produces: `enum TipoBusquedaInventario { CODIGO_BARRA, SKU, NOMBRE }` y `record InventarioConsultaItem(Long productoId, String nombre, String sku, String codigoBarra, BigDecimal stock)`, usados por `InventarioConsultaService`, `InventarioExportService` y `InventarioConsultaController` (Tasks 3-6).

- [ ] **Step 1: Crear el enum**

`backend/src/main/java/cl/slimerp/inventario/TipoBusquedaInventario.java`:

```java
package cl.slimerp.inventario;

// Campo de Producto contra el que se compara el texto de búsqueda en el
// módulo de consulta de Inventario.
public enum TipoBusquedaInventario {
    CODIGO_BARRA,
    SKU,
    NOMBRE
}
```

- [ ] **Step 2: Crear el record de respuesta**

`backend/src/main/java/cl/slimerp/inventario/InventarioConsultaItem.java`:

```java
package cl.slimerp.inventario;

import java.math.BigDecimal;

// Fila de la tabla de consulta de Inventario: un producto con su stock ya
// resuelto (cantidad en la bodega filtrada, o sumado entre todas las
// bodegas activas si no se filtró por una en particular).
public record InventarioConsultaItem(Long productoId, String nombre, String sku, String codigoBarra, BigDecimal stock) {
}
```

- [ ] **Step 3: Verificar que compila**

Run: `cd backend && mvn -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/cl/slimerp/inventario/TipoBusquedaInventario.java backend/src/main/java/cl/slimerp/inventario/InventarioConsultaItem.java
git commit -m "feat: tipos base para la consulta de Inventario"
```

---

### Task 3: `InventarioConsultaService`

**Files:**
- Create: `backend/src/main/java/cl/slimerp/inventario/InventarioConsultaService.java`
- Test: `backend/src/test/java/cl/slimerp/inventario/InventarioConsultaServiceTest.java`

**Interfaces:**
- Consumes: `ProductoRepository.findAll(Specification<Producto>)` (Task 1), `StockProductoBodegaRepository.findByTenantId(Long)` / `.findByTenantIdAndBodegaId(Long, Long)` (ya existen), `BodegaRepository.findByTenantIdAndActivoTrue(Long)` (ya existe), `TipoBusquedaInventario`, `InventarioConsultaItem` (Task 2), `PaginaResponse<T>` (`cl.slimerp.common.PaginaResponse`, ya existe).
- Produces:
  ```java
  public PaginaResponse<InventarioConsultaItem> consultar(
          Long tenantId, Long bodegaId, Long familiaId, Long subfamiliaId,
          boolean verDeshabilitados, TipoBusquedaInventario tipoBusqueda, String busqueda,
          String sort, String dir, int pagina, int tamano)

  public List<InventarioConsultaItem> consultarTodo(
          Long tenantId, Long bodegaId, Long familiaId, Long subfamiliaId,
          boolean verDeshabilitados, TipoBusquedaInventario tipoBusqueda, String busqueda)
  ```
  Usados por `InventarioConsultaController` (Task 5).

- [ ] **Step 1: Escribir el test que falla**

`backend/src/test/java/cl/slimerp/inventario/InventarioConsultaServiceTest.java`:

```java
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
```

- [ ] **Step 2: Ejecutar el test para verificar que falla (no compila: la clase no existe)**

Run: `cd backend && mvn -q -Dtest=InventarioConsultaServiceTest test`
Expected: FAIL (error de compilación — `InventarioConsultaService`, `StockProductoBodega`, `Bodega`, `TipoBodega`, `StockProductoBodegaRepository`, `BodegaRepository` ya existen en `cl.slimerp.inventario`; solo falta `InventarioConsultaService`).

- [ ] **Step 3: Implementar `InventarioConsultaService`**

`backend/src/main/java/cl/slimerp/inventario/InventarioConsultaService.java`:

```java
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

        int total = items.size();
        int desde = Math.min(pagina * tamano, total);
        int hasta = Math.min(desde + tamano, total);
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
```

- [ ] **Step 4: Ejecutar el test y verificar que pasa**

Run: `cd backend && mvn -q -Dtest=InventarioConsultaServiceTest test`
Expected: PASS (7 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/cl/slimerp/inventario/InventarioConsultaService.java backend/src/test/java/cl/slimerp/inventario/InventarioConsultaServiceTest.java
git commit -m "feat: InventarioConsultaService (filtros + orden + paginacion en memoria)"
```

---

### Task 4: `InventarioExportService` (CSV + XLSX)

**Files:**
- Create: `backend/src/main/java/cl/slimerp/inventario/InventarioExportService.java`
- Test: `backend/src/test/java/cl/slimerp/inventario/InventarioExportServiceTest.java`

**Interfaces:**
- Consumes: `InventarioConsultaItem` (Task 2).
- Produces:
  ```java
  public byte[] generarCsv(List<InventarioConsultaItem> items)
  public byte[] generarXlsx(List<InventarioConsultaItem> items)
  ```
  Usados por `InventarioConsultaController` (Task 5).

- [ ] **Step 1: Escribir el test que falla**

`backend/src/test/java/cl/slimerp/inventario/InventarioExportServiceTest.java`:

```java
package cl.slimerp.inventario;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InventarioExportServiceTest {

    private final InventarioExportService service = new InventarioExportService();

    private List<InventarioConsultaItem> itemsDeEjemplo() {
        return List.of(
                new InventarioConsultaItem(1L, "Mouse", "SKU-1", "7801234567890", new BigDecimal("8")),
                new InventarioConsultaItem(2L, "Teclado, USB", null, null, BigDecimal.ZERO)
        );
    }

    @Test
    void generaUnCsvConEncabezadoYFilasEscapandoComas() {
        byte[] csv = service.generarCsv(itemsDeEjemplo());
        String texto = new String(csv, StandardCharsets.UTF_8);
        String[] lineas = texto.split("\n");

        assertEquals("Producto,Código,Código Barra,Stock", lineas[0].trim());
        assertEquals("Mouse,SKU-1,7801234567890,8", lineas[1].trim());
        assertTrue(lineas[2].startsWith("\"Teclado, USB\","));
    }

    @Test
    void generaUnXlsxLeeibleConEncabezadoYFilas() throws IOException {
        byte[] xlsx = service.generarXlsx(itemsDeEjemplo());
        assertTrue(xlsx.length > 0);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet hoja = workbook.getSheet("Inventario");
            assertNotNull(hoja);

            Row encabezado = hoja.getRow(0);
            assertEquals("Producto", encabezado.getCell(0).getStringCellValue());
            assertEquals("Stock", encabezado.getCell(3).getStringCellValue());

            Row fila1 = hoja.getRow(1);
            assertEquals("Mouse", fila1.getCell(0).getStringCellValue());
            assertEquals("SKU-1", fila1.getCell(1).getStringCellValue());
            assertEquals(8.0, fila1.getCell(3).getNumericCellValue(), 0.001);

            Row fila2 = hoja.getRow(2);
            assertEquals("", fila2.getCell(1).getStringCellValue());
        }
    }

    @Test
    void unaListaVaciaGeneraArchivosValidosSinFilasDeDatos() throws IOException {
        byte[] csv = service.generarCsv(List.of());
        assertEquals("Producto,Código,Código Barra,Stock", new String(csv, StandardCharsets.UTF_8).trim());

        byte[] xlsx = service.generarXlsx(List.of());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertNotNull(workbook.getSheet("Inventario"));
            assertNull(workbook.getSheet("Inventario").getRow(1));
        }
    }
}
```

- [ ] **Step 2: Ejecutar el test para verificar que falla**

Run: `cd backend && mvn -q -Dtest=InventarioExportServiceTest test`
Expected: FAIL (la clase `InventarioExportService` no existe)

- [ ] **Step 3: Implementar `InventarioExportService`**

`backend/src/main/java/cl/slimerp/inventario/InventarioExportService.java`:

```java
package cl.slimerp.inventario;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

// Exporta el resultado de InventarioConsultaService a CSV o XLSX, siempre
// con las columnas Producto/Código/Código Barra/Stock, en el mismo orden
// que llegue la lista (el llamador ya la ordena antes de exportar).
@Service
public class InventarioExportService {

    private static final String[] ENCABEZADO = {"Producto", "Código", "Código Barra", "Stock"};

    public byte[] generarCsv(List<InventarioConsultaItem> items) {
        StringBuilder sb = new StringBuilder(String.join(",", ENCABEZADO)).append('\n');
        for (InventarioConsultaItem item : items) {
            sb.append(csvEscape(item.nombre())).append(',')
                    .append(csvEscape(item.sku())).append(',')
                    .append(csvEscape(item.codigoBarra())).append(',')
                    .append(item.stock().stripTrailingZeros().toPlainString())
                    .append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csvEscape(String valor) {
        if (valor == null) return "";
        if (valor.contains(",") || valor.contains("\"") || valor.contains("\n")) {
            return "\"" + valor.replace("\"", "\"\"") + "\"";
        }
        return valor;
    }

    public byte[] generarXlsx(List<InventarioConsultaItem> items) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet hoja = workbook.createSheet("Inventario");

            Row encabezado = hoja.createRow(0);
            for (int i = 0; i < ENCABEZADO.length; i++) {
                encabezado.createCell(i).setCellValue(ENCABEZADO[i]);
            }

            int filaIndex = 1;
            for (InventarioConsultaItem item : items) {
                Row fila = hoja.createRow(filaIndex++);
                fila.createCell(0).setCellValue(item.nombre());
                fila.createCell(1).setCellValue(item.sku() == null ? "" : item.sku());
                fila.createCell(2).setCellValue(item.codigoBarra() == null ? "" : item.codigoBarra());
                fila.createCell(3).setCellValue(item.stock().doubleValue());
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el Excel de Inventario", e);
        }
    }
}
```

- [ ] **Step 4: Ejecutar el test y verificar que pasa**

Run: `cd backend && mvn -q -Dtest=InventarioExportServiceTest test`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/cl/slimerp/inventario/InventarioExportService.java backend/src/test/java/cl/slimerp/inventario/InventarioExportServiceTest.java
git commit -m "feat: exportar Inventario a CSV y XLSX"
```

---

### Task 5: `InventarioConsultaController`

**Files:**
- Create: `backend/src/main/java/cl/slimerp/inventario/InventarioConsultaController.java`
- Test: `backend/src/test/java/cl/slimerp/inventario/InventarioConsultaControllerTest.java`

**Interfaces:**
- Consumes: `InventarioConsultaService.consultar(...)` y `.consultarTodo(...)` (Task 3), `InventarioExportService.generarCsv(...)` y `.generarXlsx(...)` (Task 4), `TenantContext.getTenantId()` (ya existe, `cl.slimerp.config.TenantContext`).
- Produces: endpoints HTTP `GET /api/inventario`, `GET /api/inventario/exportar.csv`, `GET /api/inventario/exportar.xlsx` — consumidos por el frontend (Task 6, `InventarioService`).

- [ ] **Step 1: Escribir el test que falla**

`backend/src/test/java/cl/slimerp/inventario/InventarioConsultaControllerTest.java`:

```java
package cl.slimerp.inventario;

import cl.slimerp.common.PaginaResponse;
import cl.slimerp.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventarioConsultaControllerTest {

    private InventarioConsultaService service;
    private InventarioExportService exportService;
    private InventarioConsultaController controller;

    @BeforeEach
    void setUp() {
        service = mock(InventarioConsultaService.class);
        exportService = mock(InventarioExportService.class);
        controller = new InventarioConsultaController(service, exportService);
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listarDelegaEnElServicioConElTenantActual() {
        var item = new InventarioConsultaItem(1L, "Mouse", "SKU-1", null, BigDecimal.TEN);
        when(service.consultar(1L, 2L, 3L, 4L, true, TipoBusquedaInventario.SKU, "abc", "stock", "desc", 1, 25))
                .thenReturn(new PaginaResponse<>(List.of(item), 1));

        var respuesta = controller.listar(2L, 3L, 4L, true, TipoBusquedaInventario.SKU, "abc", "stock", "desc", 1, 25);

        assertEquals(1, respuesta.total());
        assertEquals(item, respuesta.contenido().get(0));
    }

    @Test
    void exportarCsvDevuelveElArchivoConElContentTypeYNombreCorrectos() {
        var item = new InventarioConsultaItem(1L, "Mouse", "SKU-1", null, BigDecimal.TEN);
        when(service.consultarTodo(1L, null, null, null, false, TipoBusquedaInventario.NOMBRE, null))
                .thenReturn(List.of(item));
        when(exportService.generarCsv(List.of(item))).thenReturn("csv-bytes".getBytes());

        var respuesta = controller.exportarCsv(null, null, null, false, TipoBusquedaInventario.NOMBRE, null);

        assertEquals(200, respuesta.getStatusCode().value());
        assertEquals("text/csv", respuesta.getHeaders().getContentType().toString());
        assertTrue(respuesta.getHeaders().getContentDisposition().toString().contains("inventario.csv"));
        assertArrayEquals("csv-bytes".getBytes(), respuesta.getBody());
    }

    @Test
    void exportarXlsxDevuelveElArchivoConElContentTypeYNombreCorrectos() {
        when(service.consultarTodo(1L, 5L, null, null, false, TipoBusquedaInventario.NOMBRE, null))
                .thenReturn(List.of());
        when(exportService.generarXlsx(List.of())).thenReturn("xlsx-bytes".getBytes());

        var respuesta = controller.exportarXlsx(5L, null, null, false, TipoBusquedaInventario.NOMBRE, null);

        assertEquals(200, respuesta.getStatusCode().value());
        assertTrue(respuesta.getHeaders().getContentDisposition().toString().contains("inventario.xlsx"));
        assertArrayEquals("xlsx-bytes".getBytes(), respuesta.getBody());
    }
}
```

- [ ] **Step 2: Ejecutar el test para verificar que falla**

Run: `cd backend && mvn -q -Dtest=InventarioConsultaControllerTest test`
Expected: FAIL (la clase `InventarioConsultaController` no existe)

- [ ] **Step 3: Implementar `InventarioConsultaController`**

`backend/src/main/java/cl/slimerp/inventario/InventarioConsultaController.java`:

```java
package cl.slimerp.inventario;

import cl.slimerp.common.PaginaResponse;
import cl.slimerp.config.TenantContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventario")
public class InventarioConsultaController {

    private final InventarioConsultaService service;
    private final InventarioExportService exportService;

    public InventarioConsultaController(InventarioConsultaService service, InventarioExportService exportService) {
        this.service = service;
        this.exportService = exportService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('BODEGAS_VER')")
    public PaginaResponse<InventarioConsultaItem> listar(
            @RequestParam(required = false) Long bodegaId,
            @RequestParam(required = false) Long familiaId,
            @RequestParam(required = false) Long subfamiliaId,
            @RequestParam(defaultValue = "false") boolean verDeshabilitados,
            @RequestParam(required = false) TipoBusquedaInventario tipoBusqueda,
            @RequestParam(required = false) String busqueda,
            @RequestParam(defaultValue = "nombre") String sort,
            @RequestParam(defaultValue = "asc") String dir,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano) {
        return service.consultar(TenantContext.getTenantId(), bodegaId, familiaId, subfamiliaId,
                verDeshabilitados, tipoBusqueda, busqueda, sort, dir, pagina, tamano);
    }

    @GetMapping("/exportar.csv")
    @PreAuthorize("hasAuthority('BODEGAS_VER')")
    public ResponseEntity<byte[]> exportarCsv(
            @RequestParam(required = false) Long bodegaId,
            @RequestParam(required = false) Long familiaId,
            @RequestParam(required = false) Long subfamiliaId,
            @RequestParam(defaultValue = "false") boolean verDeshabilitados,
            @RequestParam(required = false) TipoBusquedaInventario tipoBusqueda,
            @RequestParam(required = false) String busqueda) {
        List<InventarioConsultaItem> items = service.consultarTodo(TenantContext.getTenantId(), bodegaId,
                familiaId, subfamiliaId, verDeshabilitados, tipoBusqueda, busqueda);
        byte[] csv = exportService.generarCsv(items);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=inventario.csv")
                .body(csv);
    }

    @GetMapping("/exportar.xlsx")
    @PreAuthorize("hasAuthority('BODEGAS_VER')")
    public ResponseEntity<byte[]> exportarXlsx(
            @RequestParam(required = false) Long bodegaId,
            @RequestParam(required = false) Long familiaId,
            @RequestParam(required = false) Long subfamiliaId,
            @RequestParam(defaultValue = "false") boolean verDeshabilitados,
            @RequestParam(required = false) TipoBusquedaInventario tipoBusqueda,
            @RequestParam(required = false) String busqueda) {
        List<InventarioConsultaItem> items = service.consultarTodo(TenantContext.getTenantId(), bodegaId,
                familiaId, subfamiliaId, verDeshabilitados, tipoBusqueda, busqueda);
        byte[] xlsx = exportService.generarXlsx(items);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=inventario.xlsx")
                .body(xlsx);
    }
}
```

- [ ] **Step 4: Ejecutar el test y verificar que pasa**

Run: `cd backend && mvn -q -Dtest=InventarioConsultaControllerTest test`
Expected: PASS (3 tests)

- [ ] **Step 5: Correr toda la suite del backend**

Run: `cd backend && mvn -q test`
Expected: BUILD SUCCESS, sin regresiones en los tests existentes.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/cl/slimerp/inventario/InventarioConsultaController.java backend/src/test/java/cl/slimerp/inventario/InventarioConsultaControllerTest.java
git commit -m "feat: endpoint GET /api/inventario y exportacion CSV/XLSX"
```

---

## Frontend

### Task 6: Modelos y `InventarioService`

**Files:**
- Modify: `frontend/src/app/core/models/models.ts`
- Create: `frontend/src/app/core/services/inventario.service.ts`

**Interfaces:**
- Produces: `InventarioConsultaItem`, `TipoBusquedaInventario` (interfaces TS), `InventarioService` con métodos `listar(...)`, `exportarCsv(...)`, `exportarXlsx(...)` — usados por `InventarioComponent` (Task 7).

- [ ] **Step 1: Agregar los tipos a `models.ts`**

Al final de `frontend/src/app/core/models/models.ts` (después de `StockPorBodega`, línea 161), agregar:

```ts
export type TipoBusquedaInventario = 'CODIGO_BARRA' | 'SKU' | 'NOMBRE';

export interface InventarioConsultaItem {
  productoId: number;
  nombre: string;
  sku: string | null;
  codigoBarra: string | null;
  stock: number;
}
```

- [ ] **Step 2: Crear `InventarioService`**

`frontend/src/app/core/services/inventario.service.ts`:

```ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { InventarioConsultaItem, PaginaResponse, TipoBusquedaInventario } from '../models/models';

export interface InventarioFiltro {
  bodegaId: number | null;
  familiaId: number | null;
  subfamiliaId: number | null;
  verDeshabilitados: boolean;
  tipoBusqueda: TipoBusquedaInventario;
  busqueda: string;
}

@Injectable({ providedIn: 'root' })
export class InventarioService {
  private readonly base = `${environment.apiUrl}/inventario`;

  constructor(private http: HttpClient) {}

  listar(
    filtro: InventarioFiltro,
    sort: string,
    dir: 'asc' | 'desc',
    pagina: number,
    tamano: number
  ): Observable<PaginaResponse<InventarioConsultaItem>> {
    const params = this.parametrosFiltro(filtro)
      .set('sort', sort)
      .set('dir', dir)
      .set('pagina', pagina)
      .set('tamano', tamano);
    return this.http.get<PaginaResponse<InventarioConsultaItem>>(this.base, { params });
  }

  exportarCsv(filtro: InventarioFiltro): Observable<Blob> {
    return this.http.get(`${this.base}/exportar.csv`, {
      params: this.parametrosFiltro(filtro),
      responseType: 'blob',
    });
  }

  exportarXlsx(filtro: InventarioFiltro): Observable<Blob> {
    return this.http.get(`${this.base}/exportar.xlsx`, {
      params: this.parametrosFiltro(filtro),
      responseType: 'blob',
    });
  }

  private parametrosFiltro(filtro: InventarioFiltro): HttpParams {
    let params = new HttpParams()
      .set('verDeshabilitados', filtro.verDeshabilitados)
      .set('tipoBusqueda', filtro.tipoBusqueda);
    if (filtro.bodegaId != null) params = params.set('bodegaId', filtro.bodegaId);
    if (filtro.familiaId != null) params = params.set('familiaId', filtro.familiaId);
    if (filtro.subfamiliaId != null) params = params.set('subfamiliaId', filtro.subfamiliaId);
    if (filtro.busqueda) params = params.set('busqueda', filtro.busqueda);
    return params;
  }
}
```

- [ ] **Step 3: Verificar que compila**

Run: `cd frontend && npx tsc --noEmit -p tsconfig.json`
Expected: sin errores.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/core/models/models.ts frontend/src/app/core/services/inventario.service.ts
git commit -m "feat: modelos y servicio Angular para la consulta de Inventario"
```

---

### Task 7: `InventarioComponent`

**Files:**
- Create: `frontend/src/app/features/inventario/inventario.component.ts`
- Create: `frontend/src/app/features/inventario/inventario.component.html`
- Create: `frontend/src/app/features/inventario/inventario.component.scss`
- Test: `frontend/src/app/features/inventario/inventario.component.spec.ts`

**Interfaces:**
- Consumes: `InventarioService` (Task 6), `BodegaService.listar()`, `CategoriaService.listar()`, `SubcategoriaService.listar(categoriaId?)` (ya existen, `core/services/`), `mostrarCargando`/`cerrarCargando` (`core/utils/swal-loading.ts`, ya existe).
- Produces: `InventarioComponent` (standalone), consumido por la ruta `/inventario` (Task 8).

- [ ] **Step 1: Escribir el test que falla**

`frontend/src/app/features/inventario/inventario.component.spec.ts`:

```ts
import { of, throwError } from 'rxjs';
import { InventarioComponent } from './inventario.component';
import { InventarioService } from '../../core/services/inventario.service';
import { BodegaService } from '../../core/services/bodega.service';
import { CategoriaService } from '../../core/services/categoria.service';
import { SubcategoriaService } from '../../core/services/subcategoria.service';
import { InventarioConsultaItem, PaginaResponse } from '../../core/models/models';

function paginaDeEjemplo(overrides: Partial<PaginaResponse<InventarioConsultaItem>> = {}): PaginaResponse<InventarioConsultaItem> {
  return { contenido: [], total: 0, ...overrides };
}

describe('InventarioComponent', () => {
  function crear() {
    const inventarioServiceStub = {
      listar: jasmine.createSpy('listar').and.returnValue(of(paginaDeEjemplo())),
      exportarCsv: jasmine.createSpy('exportarCsv').and.returnValue(of(new Blob())),
      exportarXlsx: jasmine.createSpy('exportarXlsx').and.returnValue(of(new Blob())),
    } as unknown as InventarioService;
    const bodegaServiceStub = {
      listar: jasmine.createSpy('listar').and.returnValue(of([])),
    } as unknown as BodegaService;
    const categoriaServiceStub = {
      listar: jasmine.createSpy('listar').and.returnValue(of([])),
    } as unknown as CategoriaService;
    const subcategoriaServiceStub = {
      listar: jasmine.createSpy('listar').and.returnValue(of([])),
    } as unknown as SubcategoriaService;

    const component = new InventarioComponent(
      inventarioServiceStub,
      bodegaServiceStub,
      categoriaServiceStub,
      subcategoriaServiceStub
    );
    return { component, inventarioServiceStub, subcategoriaServiceStub };
  }

  it('al iniciar consulta con los filtros por defecto', () => {
    const { component, inventarioServiceStub } = crear();

    component.ngOnInit();

    expect(inventarioServiceStub.listar).toHaveBeenCalledWith(
      { bodegaId: null, familiaId: null, subfamiliaId: null, verDeshabilitados: false, tipoBusqueda: 'NOMBRE', busqueda: '' },
      'nombre',
      'asc',
      0,
      10
    );
  });

  it('cambiar un filtro reinicia la pagina a 0', () => {
    const { component } = crear();
    component.ngOnInit();
    component.pagina = 3;

    component.bodegaId = 5;
    component.onFiltroChange();

    expect(component.pagina).toBe(0);
  });

  it('cambiar la familia limpia la subfamilia seleccionada y recarga las opciones', () => {
    const { component, subcategoriaServiceStub } = crear();
    component.ngOnInit();
    component.subfamiliaId = 9;

    component.familiaId = 4;
    component.onFamiliaChange();

    expect(component.subfamiliaId).toBeNull();
    expect(subcategoriaServiceStub.listar).toHaveBeenCalledWith(4);
  });

  it('un error de backend muestra un mensaje generico, no el detalle tecnico', () => {
    const { component, inventarioServiceStub } = crear();
    (inventarioServiceStub.listar as jasmine.Spy).and.returnValue(
      throwError(() => ({ error: { error: 'duplicate key value violates unique constraint' } }))
    );

    component.ngOnInit();

    expect(component.error).toBe('No fue posible cargar el inventario. Intente nuevamente.');
    expect(component.error).not.toContain('constraint');
  });

  it('sin resultados no hay error y la lista queda vacia', () => {
    const { component } = crear();

    component.ngOnInit();

    expect(component.error).toBe('');
    expect(component.items).toEqual([]);
    expect(component.total).toBe(0);
  });
});
```

- [ ] **Step 2: Ejecutar el test para verificar que falla**

Run: `cd frontend && npx ng test --watch=false --browsers=ChromeHeadless --include='**/inventario.component.spec.ts'`
Expected: FAIL (el componente no existe todavía)

- [ ] **Step 3: Crear el componente**

`frontend/src/app/features/inventario/inventario.component.ts`:

```ts
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { InventarioService } from '../../core/services/inventario.service';
import { BodegaService } from '../../core/services/bodega.service';
import { CategoriaService } from '../../core/services/categoria.service';
import { SubcategoriaService } from '../../core/services/subcategoria.service';
import { Bodega, Categoria, InventarioConsultaItem, Subcategoria, TipoBusquedaInventario } from '../../core/models/models';
import { cerrarCargando, mostrarCargando } from '../../core/utils/swal-loading';

function descargarBlob(blob: Blob, nombreArchivo: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = nombreArchivo;
  anchor.click();
  URL.revokeObjectURL(url);
}

@Component({
  selector: 'app-inventario',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
  ],
  templateUrl: './inventario.component.html',
  styleUrl: './inventario.component.scss',
})
export class InventarioComponent implements OnInit {
  columnas = ['nombre', 'sku', 'codigoBarra', 'stock'];
  readonly opcionesTamano = [10, 25, 50, 100];
  readonly tiposBusqueda: { value: TipoBusquedaInventario; label: string }[] = [
    { value: 'NOMBRE', label: 'Nombre' },
    { value: 'SKU', label: 'SKU' },
    { value: 'CODIGO_BARRA', label: 'Código de Barra' },
  ];

  bodegas: Bodega[] = [];
  familias: Categoria[] = [];
  subfamilias: Subcategoria[] = [];

  bodegaId: number | null = null;
  familiaId: number | null = null;
  subfamiliaId: number | null = null;
  verDeshabilitados = false;

  tipoBusqueda: TipoBusquedaInventario = 'NOMBRE';
  busqueda = '';

  sort = 'nombre';
  dir: 'asc' | 'desc' = 'asc';

  items: InventarioConsultaItem[] = [];
  total = 0;
  pagina = 0;
  tamano = 10;

  cargando = false;
  error = '';

  private readonly busqueda$ = new Subject<void>();

  constructor(
    private inventarioService: InventarioService,
    private bodegaService: BodegaService,
    private categoriaService: CategoriaService,
    private subcategoriaService: SubcategoriaService
  ) {}

  ngOnInit(): void {
    this.bodegaService.listar().subscribe((data) => (this.bodegas = data));
    this.categoriaService.listar().subscribe((data) => (this.familias = data));
    this.busqueda$.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => this.onFiltroChange());
    this.cargar();
  }

  get desde(): number {
    return this.total === 0 ? 0 : this.pagina * this.tamano + 1;
  }

  get hasta(): number {
    return Math.min((this.pagina + 1) * this.tamano, this.total);
  }

  onFamiliaChange(): void {
    this.subfamiliaId = null;
    this.subfamilias = [];
    if (this.familiaId != null) {
      this.subcategoriaService.listar(this.familiaId).subscribe((data) => (this.subfamilias = data));
    }
    this.onFiltroChange();
  }

  onBusquedaChange(): void {
    this.busqueda$.next();
  }

  onFiltroChange(): void {
    this.pagina = 0;
    this.cargar();
  }

  onSortChange(sort: Sort): void {
    this.sort = sort.direction ? sort.active : 'nombre';
    this.dir = sort.direction === 'desc' ? 'desc' : 'asc';
    this.pagina = 0;
    this.cargar();
  }

  onPageChange(event: PageEvent): void {
    this.pagina = event.pageIndex;
    this.tamano = event.pageSize;
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.error = '';
    this.inventarioService
      .listar(this.filtroActual(), this.sort, this.dir, this.pagina, this.tamano)
      .subscribe({
        next: (respuesta) => {
          this.items = respuesta.contenido;
          this.total = respuesta.total;
          this.cargando = false;
        },
        error: () => {
          this.error = 'No fue posible cargar el inventario. Intente nuevamente.';
          this.cargando = false;
        },
      });
  }

  exportarCsv(): void {
    mostrarCargando('Generando CSV');
    this.inventarioService.exportarCsv(this.filtroActual()).subscribe({
      next: (blob) => {
        cerrarCargando();
        descargarBlob(blob, 'inventario.csv');
      },
      error: () => {
        cerrarCargando();
        this.error = 'No se pudo exportar el CSV.';
      },
    });
  }

  exportarXlsx(): void {
    mostrarCargando('Generando Excel');
    this.inventarioService.exportarXlsx(this.filtroActual()).subscribe({
      next: (blob) => {
        cerrarCargando();
        descargarBlob(blob, 'inventario.xlsx');
      },
      error: () => {
        cerrarCargando();
        this.error = 'No se pudo exportar el Excel.';
      },
    });
  }

  private filtroActual() {
    return {
      bodegaId: this.bodegaId,
      familiaId: this.familiaId,
      subfamiliaId: this.subfamiliaId,
      verDeshabilitados: this.verDeshabilitados,
      tipoBusqueda: this.tipoBusqueda,
      busqueda: this.busqueda.trim(),
    };
  }
}
```

- [ ] **Step 4: Crear el template**

`frontend/src/app/features/inventario/inventario.component.html`:

```html
<h1>Inventario</h1>

@if (error) {
  <p class="page-error">{{ error }}</p>
}

<mat-card class="form-panel">
  <h2>Filtro de inventario específico</h2>
  <div class="form-grid">
    <div class="form-group">
      <label for="bodegaId">Bodega</label>
      <select id="bodegaId" name="bodegaId" [(ngModel)]="bodegaId" (ngModelChange)="onFiltroChange()">
        <option [ngValue]="null">Todos</option>
        @for (b of bodegas; track b.id) {
          <option [ngValue]="b.id">{{ b.nombre }}</option>
        }
      </select>
    </div>

    <div class="form-group">
      <label for="familiaId">Familia</label>
      <select id="familiaId" name="familiaId" [(ngModel)]="familiaId" (ngModelChange)="onFamiliaChange()">
        <option [ngValue]="null">Todos</option>
        @for (f of familias; track f.id) {
          <option [ngValue]="f.id">{{ f.nombre }}</option>
        }
      </select>
    </div>

    <div class="form-group">
      <label for="subfamiliaId">Sub Familia</label>
      <select
        id="subfamiliaId"
        name="subfamiliaId"
        [(ngModel)]="subfamiliaId"
        (ngModelChange)="onFiltroChange()"
        [disabled]="!familiaId"
      >
        <option [ngValue]="null">Todos</option>
        @for (s of subfamilias; track s.id) {
          <option [ngValue]="s.id">{{ s.nombre }}</option>
        }
      </select>
    </div>

    <div class="form-group form-group--checkbox">
      <label>
        <input type="checkbox" name="verDeshabilitados" [(ngModel)]="verDeshabilitados" (ngModelChange)="onFiltroChange()" />
        Ver Deshabilitados
      </label>
    </div>
  </div>

  <div class="form-actions">
    <button mat-stroked-button (click)="exportarCsv()">Descargar CSV</button>
    <button mat-stroked-button (click)="exportarXlsx()">Descargar XLSX</button>
  </div>
</mat-card>

<mat-card class="form-panel">
  <h2>Filtro por tipo de búsqueda</h2>
  <div class="form-grid">
    <div class="form-group">
      <label for="tipoBusqueda">Tipo de búsqueda</label>
      <select id="tipoBusqueda" name="tipoBusqueda" [(ngModel)]="tipoBusqueda" (ngModelChange)="onFiltroChange()">
        @for (t of tiposBusqueda; track t.value) {
          <option [ngValue]="t.value">{{ t.label }}</option>
        }
      </select>
    </div>

    <div class="form-group span-2">
      <label for="busqueda">Buscador de Productos</label>
      <input id="busqueda" name="busqueda" [(ngModel)]="busqueda" (ngModelChange)="onBusquedaChange()" />
    </div>

    <div class="form-actions">
      <button mat-flat-button color="primary" (click)="onFiltroChange()">Buscar</button>
    </div>
  </div>
</mat-card>

<mat-card class="form-panel">
  <h2>Inventario</h2>

  <div class="form-group form-group--inline">
    <label for="tamano">Registros</label>
    <select id="tamano" name="tamano" [(ngModel)]="tamano" (ngModelChange)="onFiltroChange()">
      @for (t of opcionesTamano; track t) {
        <option [ngValue]="t">{{ t }}</option>
      }
    </select>
  </div>

  @if (cargando) {
    <p class="empty-state">Cargando…</p>
  } @else if (!items.length) {
    <p class="empty-state">No se encontraron productos para los filtros seleccionados.</p>
  } @else {
    <div class="table-scroll">
      <table mat-table [dataSource]="items" matSort (matSortChange)="onSortChange($event)" class="mat-elevation-z1">
        <ng-container matColumnDef="nombre">
          <th mat-header-cell *matHeaderCellDef mat-sort-header="nombre">Producto</th>
          <td mat-cell *matCellDef="let i">{{ i.nombre }}</td>
        </ng-container>

        <ng-container matColumnDef="sku">
          <th mat-header-cell *matHeaderCellDef mat-sort-header="sku">Código</th>
          <td mat-cell *matCellDef="let i">{{ i.sku || '—' }}</td>
        </ng-container>

        <ng-container matColumnDef="codigoBarra">
          <th mat-header-cell *matHeaderCellDef mat-sort-header="codigoBarra">Código Barra</th>
          <td mat-cell *matCellDef="let i">{{ i.codigoBarra || '—' }}</td>
        </ng-container>

        <ng-container matColumnDef="stock">
          <th mat-header-cell *matHeaderCellDef mat-sort-header="stock">Stock</th>
          <td mat-cell *matCellDef="let i">{{ i.stock }}</td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="columnas"></tr>
        <tr mat-row *matRowDef="let row; columns: columnas"></tr>
      </table>
    </div>

    <div class="pagination-info">
      <p>Registros del {{ desde }} al {{ hasta }} de un total de {{ total }} Registros</p>
      <mat-paginator
        [length]="total"
        [pageIndex]="pagina"
        [pageSize]="tamano"
        [pageSizeOptions]="opcionesTamano"
        [hidePageSize]="true"
        (page)="onPageChange($event)"
      ></mat-paginator>
    </div>
  }
</mat-card>
```

- [ ] **Step 5: Crear los estilos**

`frontend/src/app/features/inventario/inventario.component.scss`:

```scss
.form-panel {
  margin-bottom: var(--space-4);
}

.form-actions {
  display: flex;
  gap: var(--space-3);
  margin-top: var(--space-3);
  flex-wrap: wrap;
}

.form-group--checkbox {
  display: flex;
  align-items: center;
}

.form-group--inline {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  max-width: 220px;
  margin-bottom: var(--space-3);
}

.table-scroll {
  overflow-x: auto;
}

.pagination-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-3);

  p {
    margin: 0;
    font: var(--font-body-sm);
    color: var(--text-muted);
  }
}

@media (max-width: 720px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
```

(Usa las mismas clases `.form-panel`/`.form-grid`/`.form-group`/`.form-actions`/`.empty-state`/`.page-error` que ya definen los estilos globales de formularios — ver `productos.component.scss`/`bodegas.component.scss` para confirmar los nombres exactos si alguno no calza.)

- [ ] **Step 6: Ejecutar el test y verificar que pasa**

Run: `cd frontend && npx ng test --watch=false --browsers=ChromeHeadless --include='**/inventario.component.spec.ts'`
Expected: PASS (5 tests)

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/features/inventario/
git commit -m "feat: InventarioComponent (filtros, tabla, paginacion, export)"
```

---

### Task 8: Registrar ruta y navegación

**Files:**
- Modify: `frontend/src/app/app.routes.ts`
- Modify: `frontend/src/app/layout/layout.component.ts`

**Interfaces:**
- Consumes: `InventarioComponent` (Task 7).

- [ ] **Step 1: Agregar la ruta**

En `frontend/src/app/app.routes.ts`, junto a las rutas de `productos`/`bodegas` (dentro del mismo array de rutas hijas del layout autenticado), agregar:

```ts
      {
        path: 'inventario',
        loadComponent: () => import('./features/inventario/inventario.component').then((m) => m.InventarioComponent),
      },
```

- [ ] **Step 2: Agregar el ítem de navegación**

En `frontend/src/app/layout/layout.component.ts`, dentro del grupo `inventario` (línea ~51-59), agregar el nuevo ítem **antes** de `Movimientos`:

```ts
  {
    key: 'inventario',
    titulo: 'Inventario',
    icono: 'inventory',
    items: [
      { ruta: '/inventario', label: 'Stock', icono: 'search', permiso: 'BODEGAS_VER', exact: true },
      { ruta: '/movimientos', label: 'Movimientos', icono: 'swap_horiz', permiso: 'MOVIMIENTOS_VER', exact: true },
      { ruta: '/movimientos/historial', label: 'Historial', icono: 'history', permiso: 'MOVIMIENTOS_VER' },
    ],
  },
```

- [ ] **Step 3: Verificar que compila**

Run: `cd frontend && npx tsc --noEmit -p tsconfig.json`
Expected: sin errores.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/app.routes.ts frontend/src/app/layout/layout.component.ts
git commit -m "feat: ruta /inventario y item de navegacion Stock"
```

---

### Task 9: Verificación manual end-to-end

**Files:** ninguno (solo verificación en Docker/navegador).

- [ ] **Step 1: Reconstruir imágenes**

Run: `docker compose build backend frontend`
Expected: ambas imágenes compilan sin errores.

- [ ] **Step 2: Reiniciar contenedores**

Run: `docker compose up -d backend frontend`
Expected: ambos contenedores arrancan (`docker logs slime-erp-backend-1 --tail 20` sin excepciones al iniciar).

- [ ] **Step 3: Verificar el endpoint directamente**

Run (ajustar credenciales si difieren):
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"email":"admin@demo.cl","password":"admin123"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
curl -s "http://localhost:8080/api/inventario?pagina=0&tamano=5" -H "Authorization: Bearer $TOKEN"
```
Expected: JSON `{"contenido":[...],"total":N}` sin error 500.

- [ ] **Step 4: Verificar en el navegador**

Con el flujo ya usado en esta sesión (login → `http://localhost:4200/inventario`):
- El ítem "Stock" aparece en el grupo "Inventario" del menú lateral.
- La tabla carga con los filtros por defecto.
- Cambiar Bodega/Familia/Subfamilia/Ver Deshabilitados actualiza la tabla y vuelve a la página 0.
- Buscar por Nombre/SKU/Código de Barra filtra correctamente.
- Click en los encabezados Producto/Código/Código Barra/Stock ordena asc → desc → sin orden.
- Cambiar "Registros" (10/25/50/100) y navegar de página actualiza el texto "Registros del X al Y de un total de Z".
- "Descargar CSV" y "Descargar XLSX" muestran el loading y descargan un archivo no vacío con los filtros activos.
- Sin resultados: mensaje "No se encontraron productos para los filtros seleccionados."
- Forzar un error (p.ej. detener el backend un momento) muestra el mensaje genérico, no un stack trace.

- [ ] **Step 5: Limpiar cualquier dato de prueba usado en la verificación manual** (si se creó algún producto/bodega nuevo solo para probar filtros).

- [ ] **Step 6: Commit final si hubo ajustes**

Si el paso 4 revela algún ajuste necesario (typo, alineación, etc.), corregir, volver a construir y hacer un commit puntual describiendo el fix.

---

## Self-Review

**Cobertura de la spec** (`docs/superpowers/specs/2026-09-09-modulo-inventario-design.md`):
- Sección 1.1 (endpoint principal + todos los filtros) → Tasks 3, 5.
- Sección 1.3 (exportación CSV/XLSX) → Tasks 4, 5.
- Sección 1.4/1.5 (implementación en memoria, nueva query) → Task 3.
- Sección 2.1 (ruta y nav) → Task 8.
- Sección 2.2-2.7 (componente, paneles, tabla, estados, responsive) → Task 7.
- Sección 3 (combinación de filtros) → cubierto por `filtroActual()` en Task 7 + los tests de Task 3.
- Sección 5 (testing) → Tasks 3, 4, 5, 7 incluyen tests; Task 9 cubre la verificación manual mencionada.

**Placeholder scan:** sin "TBD"/"TODO"; todos los pasos de código tienen el contenido completo a escribir.

**Consistencia de tipos:** `TipoBusquedaInventario` (`CODIGO_BARRA | SKU | NOMBRE`) igual en backend (enum) y frontend (union type); `InventarioConsultaItem` con los mismos 5 campos en Java y TS; `sort` acepta `'nombre' | 'sku' | 'codigoBarra' | 'stock'` de forma consistente entre `InventarioConsultaService.comparador(...)`, el controller y `InventarioComponent`/`InventarioService`.
