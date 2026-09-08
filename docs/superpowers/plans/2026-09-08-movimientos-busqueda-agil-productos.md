# Búsqueda ágil de productos en Movimientos Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** En Inventario > Movimientos, permitir agregar productos por código de barra (creándolo al vuelo si no existe), buscar por nombre/código/código de barra con el mismo patrón que ya usa Ventas, y cargar movimientos masivamente desde un Excel.

**Architecture:** Backend agrega un campo `codigoBarra` a `Producto` (único por tenant, opcional) y lo suma a la búsqueda paginada existente; un nuevo `MovimientoImportService` parsea un `.xlsx` con Apache POI, resuelve cada fila contra productos/bodegas existentes, agrupa filas equivalentes y reutiliza `MovimientoInventarioService.crear` (la misma ruta que la carga manual) para que las reglas de negocio vivan en un solo lugar. Frontend reemplaza la búsqueda en memoria de Movimientos por el patrón debounce+backend de Ventas, hace que seleccionar un resultado agregue la línea al instante (cantidad editable inline en la tabla), agrega un diálogo de creación rápida de producto cuando no hay match, y agrega la UI de carga de Excel con reporte de resultado.

**Tech Stack:** Spring Boot 3.3 / Java 21 / JPA+Flyway (backend), Angular 18 standalone components + RxJS + Angular Material (frontend), Apache POI 5.2.5 (parsing de `.xlsx`, dependencia nueva).

**Spec:** docs/superpowers/specs/2026-09-08-movimientos-busqueda-agil-productos-design.md

## Global Constraints

- El `id` de toda tabla es autoincremental y lo genera la base de datos — nunca se envía ni genera manualmente desde el frontend ni desde lógica de negocio (regla del proyecto).
- Multi-tenant: todo endpoint/query nuevo filtra por `TenantContext.getTenantId()`; toda entidad nueva/consultada lleva `tenantId`.
- Errores de negocio se lanzan como `IllegalArgumentException` (400, ya manejado por `GlobalExceptionHandler`) o una excepción de dominio dedicada mapeada a 409 para conflictos (patrón `UsuarioConflictException`/`EmpresaConflictException`).
- Formularios nuevos usan HTML nativo (`.form-group`, `input`/`select`/`textarea`) con los tokens de diseño existentes; Material solo para botones, tarjetas, tablas e íconos (`frontend/src/styles/_components.scss`). No se introduce ningún `<mat-form-field>`.
- No modificar comportamiento existente de Movimientos/Productos más allá de lo descrito acá; no eliminar campos.
- Todos los endpoints nuevos/relevantes en `MovimientoInventarioController`/`ProductoController` mantienen `@PreAuthorize` con las authorities ya usadas (`MOVIMIENTOS_VER`, `MOVIMIENTOS_EDITAR`, `PRODUCTOS_VER`, `PRODUCTOS_EDITAR`).

---

### Task 1: Backend — código de barra en `Producto`

**Files:**
- Create: `backend/src/main/resources/db/migration/V19__producto_codigo_barra.sql`
- Modify: `backend/src/main/java/cl/slimerp/catalogo/Producto.java`
- Modify: `backend/src/main/java/cl/slimerp/catalogo/ProductoRequest.java`
- Modify: `backend/src/main/java/cl/slimerp/catalogo/ProductoRepository.java`
- Modify: `backend/src/main/java/cl/slimerp/catalogo/ProductoController.java`
- Create: `backend/src/main/java/cl/slimerp/catalogo/ProductoConflictException.java`
- Modify: `backend/src/main/java/cl/slimerp/config/GlobalExceptionHandler.java`
- Modify: `backend/src/test/java/cl/slimerp/catalogo/ProductoControllerTest.java`

**Interfaces:**
- Consumes: nada nuevo — este task es la base para todos los demás.
- Produces:
  - `Producto.getCodigoBarra(): String` / `setCodigoBarra(String)`.
  - `ProductoRequest(String sku, String nombre, String descripcion, Long categoriaId, Long subcategoriaId, BigDecimal precioVenta, BigDecimal precioCompra, BigDecimal stockMinimo, String codigoBarra)` — **`codigoBarra` es el último parámetro**, para no romper otros call sites posicionales existentes.
  - `ProductoRepository.findFirstByTenantIdAndCodigoBarra(Long tenantId, String codigoBarra): Optional<Producto>` — usado en Task 5 (importador Excel) para resolver el "Código" de una fila cuando no matchea por SKU.
  - `ProductoRepository.buscar(...)` ahora también matchea `codigoBarra` — usado por el frontend en Task 3.
  - `ProductoConflictException(String message)` — `RuntimeException`, mapeada a HTTP 409 en `GlobalExceptionHandler`.

- [ ] **Step 1: Migración de base de datos**

```sql
-- backend/src/main/resources/db/migration/V19__producto_codigo_barra.sql
ALTER TABLE producto ADD COLUMN codigo_barra VARCHAR(64);

-- Único por tenant solo cuando está presente: un índice único parcial deja
-- que muchos productos sigan sin código de barra sin chocar entre sí.
CREATE UNIQUE INDEX uq_producto_tenant_codigo_barra
    ON producto (tenant_id, codigo_barra)
    WHERE codigo_barra IS NOT NULL;
```

- [ ] **Step 2: Agregar el campo a la entidad `Producto`**

En `backend/src/main/java/cl/slimerp/catalogo/Producto.java`, agregar después del campo `sku` (antes de `nombre`):

```java
    @Column(name = "codigo_barra", length = 64)
    private String codigoBarra;
```

- [ ] **Step 3: Agregar el campo a `ProductoRequest`**

Reemplazar el contenido completo de `backend/src/main/java/cl/slimerp/catalogo/ProductoRequest.java` por:

```java
package cl.slimerp.catalogo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProductoRequest(
        String sku,
        @NotBlank String nombre,
        String descripcion,
        Long categoriaId,
        Long subcategoriaId,
        @NotNull BigDecimal precioVenta,
        BigDecimal precioCompra,
        BigDecimal stockMinimo,
        String codigoBarra
) {
}
```

- [ ] **Step 4: Nueva excepción de dominio `ProductoConflictException`**

```java
// backend/src/main/java/cl/slimerp/catalogo/ProductoConflictException.java
package cl.slimerp.catalogo;

// Conflictos al crear/editar productos (código de barra ya usado por otro producto del tenant) → HTTP 409
public class ProductoConflictException extends RuntimeException {
    public ProductoConflictException(String message) {
        super(message);
    }
}
```

- [ ] **Step 5: Mapear la excepción en `GlobalExceptionHandler`**

En `backend/src/main/java/cl/slimerp/config/GlobalExceptionHandler.java`, agregar el import `cl.slimerp.catalogo.ProductoConflictException` junto a los otros imports de excepciones de dominio, y agregar el handler junto a los de `UsuarioConflictException`/`EmpresaConflictException`:

```java
    @ExceptionHandler(ProductoConflictException.class)
    public ResponseEntity<Map<String, Object>> handleProductoConflict(ProductoConflictException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }
```

- [ ] **Step 6: Queries nuevas en `ProductoRepository`**

Reemplazar el contenido completo de `backend/src/main/java/cl/slimerp/catalogo/ProductoRepository.java` por:

```java
package cl.slimerp.catalogo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
    List<Producto> findByTenantIdAndActivoTrue(Long tenantId);

    long countByTenantIdAndActivoTrue(Long tenantId);

    Optional<Producto> findByIdAndTenantIdAndActivoTrue(Long id, Long tenantId);

    Optional<Producto> findFirstByTenantIdAndSku(Long tenantId, String sku);

    Optional<Producto> findFirstByTenantIdAndCodigoBarra(Long tenantId, String codigoBarra);

    boolean existsByTenantIdAndCodigoBarra(Long tenantId, String codigoBarra);

    boolean existsByTenantIdAndCodigoBarraAndIdNot(Long tenantId, String codigoBarra, Long id);

    // "busqueda" siempre viene con el patrón LIKE ya armado (p.ej. "%mouse%",
    // o "%%" si no hay término) para no comparar contra un parámetro nulo:
    // Postgres no logra inferir el tipo de un parámetro que solo se usa en
    // "IS NULL" y falla con "could not determine data type of parameter".
    @Query("""
            SELECT p FROM Producto p
            WHERE p.tenantId = :tenantId AND p.activo = true
              AND (LOWER(p.nombre) LIKE :busqueda
                   OR LOWER(COALESCE(p.sku, '')) LIKE :busqueda
                   OR LOWER(COALESCE(p.codigoBarra, '')) LIKE :busqueda
                   OR EXISTS (
                       SELECT 1 FROM Categoria c
                       WHERE c.id = p.categoriaId AND LOWER(c.nombre) LIKE :busqueda
                   ))
            """)
    Page<Producto> buscar(@Param("tenantId") Long tenantId, @Param("busqueda") String busqueda, Pageable pageable);
}
```

- [ ] **Step 7: Actualizar `ProductoController` (crear/actualizar validan y setean `codigoBarra`)**

Reemplazar el contenido completo de `backend/src/main/java/cl/slimerp/catalogo/ProductoController.java` por:

```java
package cl.slimerp.catalogo;

import cl.slimerp.common.PaginaResponse;
import cl.slimerp.config.TenantContext;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoRepository productoRepository;

    public ProductoController(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCTOS_VER')")
    public List<Producto> listar() {
        return productoRepository.findByTenantIdAndActivoTrue(TenantContext.getTenantId());
    }

    // Listado paginado y con búsqueda server-side, usado por la pantalla de
    // mantenedor de Productos. El listado completo (arriba) se mantiene para
    // los buscadores en memoria de Ventas/Compras/Bodegas/etc.
    @GetMapping("/pagina")
    @PreAuthorize("hasAuthority('PRODUCTOS_VER')")
    public PaginaResponse<Producto> listarPagina(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano) {
        String busqueda = "%" + (q == null ? "" : q.trim().toLowerCase()) + "%";
        var pageable = PageRequest.of(pagina, tamano, Sort.by("nombre").ascending());
        return PaginaResponse.de(productoRepository.buscar(TenantContext.getTenantId(), busqueda, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCTOS_VER')")
    public ResponseEntity<Producto> obtener(@PathVariable Long id) {
        return productoRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCTOS_EDITAR')")
    public ResponseEntity<Producto> crear(@Valid @RequestBody ProductoRequest request) {
        Long tenantId = TenantContext.getTenantId();
        validarCodigoBarraUnico(tenantId, request.codigoBarra(), null);

        Producto producto = Producto.builder()
                .tenantId(tenantId)
                .sku(request.sku())
                .codigoBarra(normalizarCodigoBarra(request.codigoBarra()))
                .nombre(request.nombre())
                .descripcion(request.descripcion())
                .categoriaId(request.categoriaId())
                .subcategoriaId(request.subcategoriaId())
                .precioVenta(request.precioVenta())
                .precioCompra(request.precioCompra() != null ? request.precioCompra() : BigDecimal.ZERO)
                .stockMinimo(request.stockMinimo() != null ? request.stockMinimo() : BigDecimal.ZERO)
                .build();
        return ResponseEntity.ok(productoRepository.save(producto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCTOS_EDITAR')")
    public ResponseEntity<Producto> actualizar(@PathVariable Long id, @Valid @RequestBody ProductoRequest request) {
        Long tenantId = TenantContext.getTenantId();
        return productoRepository.findByIdAndTenantIdAndActivoTrue(id, tenantId)
                .map(producto -> {
                    validarCodigoBarraUnico(tenantId, request.codigoBarra(), id);
                    producto.setSku(request.sku());
                    producto.setCodigoBarra(normalizarCodigoBarra(request.codigoBarra()));
                    producto.setNombre(request.nombre());
                    producto.setDescripcion(request.descripcion());
                    producto.setCategoriaId(request.categoriaId());
                    producto.setSubcategoriaId(request.subcategoriaId());
                    producto.setPrecioVenta(request.precioVenta());
                    producto.setPrecioCompra(request.precioCompra() != null ? request.precioCompra() : BigDecimal.ZERO);
                    producto.setStockMinimo(request.stockMinimo() != null ? request.stockMinimo() : BigDecimal.ZERO);
                    return ResponseEntity.ok(productoRepository.save(producto));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCTOS_EDITAR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        return productoRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(producto -> {
                    producto.setActivo(false);
                    productoRepository.save(producto);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // El código de barra es opcional; solo se valida unicidad cuando viene con contenido.
    private String normalizarCodigoBarra(String codigoBarra) {
        if (codigoBarra == null) return null;
        String limpio = codigoBarra.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private void validarCodigoBarraUnico(Long tenantId, String codigoBarra, Long idExcluido) {
        String normalizado = normalizarCodigoBarra(codigoBarra);
        if (normalizado == null) return;
        boolean existe = idExcluido == null
                ? productoRepository.existsByTenantIdAndCodigoBarra(tenantId, normalizado)
                : productoRepository.existsByTenantIdAndCodigoBarraAndIdNot(tenantId, normalizado, idExcluido);
        if (existe) {
            throw new ProductoConflictException("Ya existe un producto con el código de barra " + normalizado);
        }
    }
}
```

- [ ] **Step 8: Actualizar los call sites existentes de `ProductoRequest` en el test**

En `backend/src/test/java/cl/slimerp/catalogo/ProductoControllerTest.java`, las dos construcciones posicionales existentes ganan un 9° argumento `null` (sin código de barra):

```java
// línea ~40: crearUsaCerosPorDefectoParaCamposOpcionalesNulos
var request = new ProductoRequest("SKU-1", "Producto Uno", "desc", null, null, new BigDecimal("1000"), null, null, null);

// línea ~65: actualizarDevuelve404SiNoExisteEnElTenant
var response = controller.actualizar(99L,
        new ProductoRequest("SKU-X", "X", null, null, null, BigDecimal.TEN, null, null, null));
```

- [ ] **Step 9: Tests nuevos para código de barra**

Agregar al final de la clase `ProductoControllerTest` (antes de la última llave de cierre):

```java
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
```

Agregar `import static org.mockito.ArgumentMatchers.anyLong;` y `import static org.mockito.ArgumentMatchers.any;` si no están ya (verificar los imports existentes del archivo antes de duplicarlos).

- [ ] **Step 10: Correr los tests del módulo `catalogo`**

Run: `cd backend && ./mvnw test -Dtest=ProductoControllerTest` (o `mvnw.cmd` en Windows)
Expected: todos los tests pasan, incluyendo los 3 nuevos.

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/resources/db/migration/V19__producto_codigo_barra.sql backend/src/main/java/cl/slimerp/catalogo/ backend/src/main/java/cl/slimerp/config/GlobalExceptionHandler.java backend/src/test/java/cl/slimerp/catalogo/ProductoControllerTest.java
git commit -m "Agregar codigo de barra opcional y unico a Producto"
```

---

### Task 2: Frontend — modelo, servicio y formulario de Productos

**Files:**
- Modify: `frontend/src/app/core/models/models.ts`
- Modify: `frontend/src/app/core/services/producto.service.ts`
- Modify: `frontend/src/app/features/productos/productos.component.ts`
- Modify: `frontend/src/app/features/productos/productos.component.html`

**Interfaces:**
- Consumes: el backend de Task 1 ya acepta/devuelve `codigoBarra` en `Producto`/`ProductoRequest`.
- Produces: `Producto.codigoBarra: string | null` disponible para Task 3/4 (Movimientos) y Task 6 (diálogo de creación rápida).

- [ ] **Step 1: Agregar el campo al modelo `Producto`**

En `frontend/src/app/core/models/models.ts`, dentro de `export interface Producto { ... }`, agregar después de `sku`:

```ts
  codigoBarra: string | null;
```

- [ ] **Step 2: Agregar el campo a `ProductoRequest`**

En `frontend/src/app/core/services/producto.service.ts`, dentro de `export interface ProductoRequest { ... }`, agregar:

```ts
  codigoBarra?: string | null;
```

- [ ] **Step 3: Estado y wiring en `ProductosComponent`**

En `frontend/src/app/features/productos/productos.component.ts`:
- Agregar la propiedad `codigoBarra = '';` junto a `sku = '';`.
- En `guardar()`, agregar `codigoBarra: this.codigoBarra || null,` al objeto `request`.
- En `editar(producto)`, agregar `this.codigoBarra = producto.codigoBarra ?? '';`.
- En `limpiarFormulario()`, agregar `this.codigoBarra = '';`.

- [ ] **Step 4: Campo en el formulario**

En `frontend/src/app/features/productos/productos.component.html`, dentro del `form-grid`, agregar un `form-group` nuevo inmediatamente después del de SKU (mismo patrón, mismo tamaño de columna):

```html
        <div class="form-group">
          <label for="codigoBarra">Código de barra <span class="hint">(opcional)</span></label>
          <input id="codigoBarra" name="codigoBarra" [(ngModel)]="codigoBarra" />
        </div>
```

- [ ] **Step 5: Mostrar el error 409 del backend**

Confirmar que `guardar()` ya usa `err?.error?.error ?? 'Ocurrió un error. Intenta nuevamente.'` en el bloque `error:` del `subscribe` (ya lo hace hoy) — el mensaje `"Ya existe un producto con el código de barra ..."` que devuelve el backend en Task 1 se mostrará tal cual sin cambios adicionales. No se requiere código nuevo para esto, solo confirmarlo al probar manualmente.

- [ ] **Step 6: Columna opcional en la tabla**

En `frontend/src/app/features/productos/productos.component.html`, junto a la columna `sku` de la `mat-table`, agregar:

```html
  <ng-container matColumnDef="codigoBarra">
    <th mat-header-cell *matHeaderCellDef>Código de barra</th>
    <td mat-cell *matCellDef="let p">{{ p.codigoBarra || '—' }}</td>
  </ng-container>
```

Y agregar `'codigoBarra'` al arreglo `columnas` en `productos.component.ts`, inmediatamente después de `'sku'`.

- [ ] **Step 7: Verificación manual**

Run: `cd frontend && npx ng build --configuration development`
Expected: build sin errores. Luego, con el backend corriendo (`docker compose up -d`), verificar en el navegador: crear un producto con código de barra, intentar crear otro con el mismo código (debe mostrar el error 409), editar un producto y quitarle el código de barra (debe permitirlo).

- [ ] **Step 8: Commit**

```bash
git add frontend/src/app/core/models/models.ts frontend/src/app/core/services/producto.service.ts frontend/src/app/features/productos/
git commit -m "Agregar codigo de barra al formulario y listado de Productos"
```

---

### Task 3: Frontend — búsqueda de Movimientos con debounce contra el backend

**Files:**
- Modify: `frontend/src/app/features/movimientos/movimientos.component.ts`
- Modify: `frontend/src/app/features/movimientos/movimientos.component.html`
- Modify: `frontend/src/app/features/movimientos/movimientos.component.scss`

**Interfaces:**
- Consumes: `ProductoService.listarPagina(q, pagina, tamano)` (ya existe, usado tal cual lo usa `VentasComponent`); `Producto.codigoBarra` de Task 2 (ya es buscable por el backend de Task 1, no requiere código adicional acá).
- Produces:
  - `MovimientosComponent.productosConocidos: Map<number, Producto>` — Task 4 y Task 6 lo usan para registrar productos recién creados/seleccionados.
  - `MovimientosComponent.seleccionarProducto(producto: Producto): void` — pasa a agregar/incrementar la línea al instante (comportamiento nuevo); Task 6 lo llama tras crear un producto en el diálogo.
  - `MovimientosComponent.sinResultados: boolean` (getter) — Task 6 lo usa para mostrar el botón "Crear producto nuevo".

- [ ] **Step 1: Reemplazar el estado de búsqueda**

En `frontend/src/app/features/movimientos/movimientos.component.ts`, reemplazar:

```ts
import { Component, OnInit } from '@angular/core';
```
por
```ts
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
```

Cambiar `export class MovimientosComponent implements OnInit {` por
`export class MovimientosComponent implements OnInit, OnDestroy {`.

Reemplazar:
```ts
  bodegas: Bodega[] = [];
  productos: Producto[] = [];
```
por
```ts
  bodegas: Bodega[] = [];
  productosResultados: Producto[] = [];
  productosTotal = 0;
  private readonly productosConocidos = new Map<number, Producto>();
  private readonly busquedaProducto$ = new Subject<string>();
  private busquedaEnCurso = false;
```

- [ ] **Step 2: `ngOnInit`/`ngOnDestroy` y la búsqueda debounced**

Reemplazar:
```ts
  ngOnInit(): void {
    this.bodegaService.listar().subscribe((data) => (this.bodegas = data));
    this.productoService.listar().subscribe((data) => (this.productos = data));
  }
```
por
```ts
  ngOnInit(): void {
    this.bodegaService.listar().subscribe((data) => (this.bodegas = data));
    this.busquedaProducto$.pipe(debounceTime(300), distinctUntilChanged()).subscribe((q) => this.buscarProductos(q));
  }

  ngOnDestroy(): void {
    this.busquedaProducto$.complete();
  }

  private buscarProductos(q: string): void {
    const texto = q.trim();
    if (!texto) {
      this.productosResultados = [];
      this.productosTotal = 0;
      this.busquedaEnCurso = false;
      return;
    }
    this.productoService.listarPagina(texto, 0, 8).subscribe((resp) => {
      this.productosResultados = resp.contenido;
      this.productosTotal = resp.total;
      resp.contenido.forEach((p) => this.productosConocidos.set(p.id, p));
      this.busquedaEnCurso = false;
    });
  }

  onFiltroProductoChange(): void {
    this.busquedaEnCurso = true;
    this.busquedaProducto$.next(this.filtroProducto);
  }

  get sinResultados(): boolean {
    return this.filtroProducto.trim().length > 0 && !this.busquedaEnCurso && this.productosResultados.length === 0;
  }
```

- [ ] **Step 3: Quitar el getter en memoria `productosFiltrados`**

Eliminar por completo:
```ts
  get productosFiltrados(): Producto[] {
    const q = this.filtroProducto.trim().toLowerCase();
    if (!q) return [];
    return this.productos.filter(
      (p) => p.nombre.toLowerCase().includes(q) || (p.sku ?? '').toLowerCase().includes(q)
    );
  }
```

- [ ] **Step 4: Actualizar `nombreProducto`/`skuProducto` para usar `productosConocidos`**

Reemplazar:
```ts
  nombreProducto(id: number): string {
    return this.productos.find((p) => p.id === id)?.nombre ?? String(id);
  }

  skuProducto(id: number): string {
    return this.productos.find((p) => p.id === id)?.sku ?? '—';
  }
```
por
```ts
  nombreProducto(id: number): string {
    return this.productosConocidos.get(id)?.nombre ?? String(id);
  }

  skuProducto(id: number): string {
    return this.productosConocidos.get(id)?.sku ?? '—';
  }
```

(`seleccionarPrimero()` sigue igual pero ahora lee `this.productosResultados[0]` en vez de `this.productosFiltrados[0]` — ver Task 4, que reemplaza el cuerpo completo de `seleccionarProducto`/`seleccionarPrimero`.)

- [ ] **Step 5: Wiring en la plantilla — el input dispara la búsqueda debounced**

En `frontend/src/app/features/movimientos/movimientos.component.html`, en el `<input id="filtroProducto" ...>`, agregar `(ngModelChange)="onFiltroProductoChange()"` (se mantiene `(keydown.enter)="seleccionarPrimero()"`), y cambiar `@if (filtroProducto && productosFiltrados.length)` por `@if (filtroProducto && productosResultados.length)`, y el `@for (p of productosFiltrados; ...)` por `@for (p of productosResultados; ...)`. Esto se hace junto con Task 4 (que reescribe esta sección completa); no dejar el archivo en un estado intermedio roto — aplicar Task 3 y Task 4 sobre `movimientos.component.html` como una sola edición coherente (ver Task 4 Step 3 para el bloque final completo).

- [ ] **Step 6: Verificación**

Run: `cd frontend && npx ng build --configuration development`
Expected: en este punto el build **fallará** porque `movimientos.component.html` todavía referencia `productosFiltrados` en algunos lugares que Task 4 termina de resolver — es esperado, Task 3 y Task 4 se implementan y commitean juntas como una sola unidad (ver nota en el Task 4 Step 6: "commitear junto con Task 3"). Si se prefiere un commit intermedio verde, aplicar también los cambios de plantilla del Task 4 antes de este build.

---

### Task 4: Frontend — selección instantánea y cantidad editable inline

**Files:**
- Modify: `frontend/src/app/features/movimientos/movimientos.component.ts` (continúa sobre Task 3)
- Modify: `frontend/src/app/features/movimientos/movimientos.component.html`
- Modify: `frontend/src/app/features/movimientos/movimientos.component.scss`

**Interfaces:**
- Consumes: todo lo producido por Task 3 (`productosResultados`, `productosConocidos`, `sinResultados`, `onFiltroProductoChange`).
- Produces: `seleccionarProducto(producto: Producto): void` con el comportamiento final (agrega o incrementa al instante) — Task 6 lo consume tras crear un producto nuevo.

- [ ] **Step 1: Reemplazar el flujo de selección/agregado**

En `movimientos.component.ts`, eliminar por completo estas propiedades y métodos:
```ts
  itemProductoId: number | null = null;
  itemCantidad = 1;
  itemError: string | null = null;
  ...
  seleccionarProducto(producto: Producto): void { ... }
  seleccionarPrimero(): void { ... }
  get productoSeleccionado(): Producto | null { ... }
  agregarItem(): void { ... }
```

Reemplazarlos por:

```ts
  itemError: string | null = null;

  seleccionarProducto(producto: Producto): void {
    this.productosConocidos.set(producto.id, producto);
    const existente = this.items.find((it) => it.productoId === producto.id);
    if (existente) {
      existente.cantidad += 1;
    } else {
      this.items.push({ productoId: producto.id, cantidad: 1 });
    }
    this.filtroProducto = '';
    this.productosResultados = [];
    this.itemError = null;
  }

  seleccionarPrimero(): void {
    const primero = this.productosResultados[0];
    if (primero) this.seleccionarProducto(primero);
  }

  actualizarCantidad(index: number, valor: number): void {
    if (!valor || valor <= 0) return;
    this.items[index].cantidad = valor;
  }
```

`filtroProducto = '';` y `quitarItem(index)` se mantienen sin cambios.

- [ ] **Step 2: Quitar `filtroProducto = '';` duplicado**

`agregarItem()` (eliminado en el Step 1) era el único lugar que reseteaba `filtroProducto` al agregar; `seleccionarProducto` ya lo hace ahora, así que no queda ningún otro lugar que necesite tocarse.

- [ ] **Step 3: Reescribir la sección "Detalle de productos" de la plantilla**

En `frontend/src/app/features/movimientos/movimientos.component.html`, reemplazar el bloque completo desde `<div class="item-entry">` hasta el `</table>` de `items-table` (líneas ~79 a ~156 del archivo original) por:

```html
  <div class="item-entry">
    <div class="form-group item-entry__search">
      <label for="filtroProducto">Buscar o escanear producto (nombre, SKU o código de barra)</label>
      <input
        id="filtroProducto"
        type="text"
        placeholder="Buscar producto..."
        [(ngModel)]="filtroProducto"
        name="filtroProducto"
        (ngModelChange)="onFiltroProductoChange()"
        (keydown.enter)="seleccionarPrimero()"
      />
      @if (filtroProducto && productosResultados.length) {
        <div class="search-results">
          @for (p of productosResultados; track p.id) {
            <div class="search-results__item" (click)="seleccionarProducto(p)">
              <span class="search-results__nombre">{{ p.nombre }}</span>
              <span class="search-results__sku">
                SKU: {{ p.sku || '—' }} @if (p.codigoBarra) { · Cód. barra: {{ p.codigoBarra }} }
              </span>
            </div>
          }
          @if (productosTotal > productosResultados.length) {
            <div class="search-results__hint">
              Mostrando {{ productosResultados.length }} de {{ productosTotal }} · sigue escribiendo para acotar
            </div>
          }
        </div>
      }
      @if (sinResultados) {
        <div class="search-results">
          <div class="search-results__hint">No se encontró ningún producto para "{{ filtroProducto }}".</div>
        </div>
      }
    </div>
    @if (itemError) {
      <p class="field-error">{{ itemError }}</p>
    }
  </div>

  <table class="items-table">
    <thead>
      <tr>
        <th>#</th>
        <th>SKU</th>
        <th>Producto</th>
        <th class="right">Cantidad</th>
        <th class="right"></th>
      </tr>
    </thead>
    <tbody>
      @for (it of items; track it.productoId; let i = $index) {
        <tr>
          <td>{{ i + 1 }}</td>
          <td>{{ skuProducto(it.productoId) }}</td>
          <td>{{ nombreProducto(it.productoId) }}</td>
          <td class="right">
            <input
              type="number"
              min="1"
              class="items-table__qty"
              [ngModel]="it.cantidad"
              (ngModelChange)="actualizarCantidad(i, $event)"
              [name]="'cantidad-' + i"
            />
          </td>
          <td class="right">
            <button type="button" mat-icon-button (click)="quitarItem(i)"><mat-icon>delete</mat-icon></button>
          </td>
        </tr>
      }
      @if (!items.length) {
        <tr>
          <td colspan="5" class="empty-state">Agrega productos desde el buscador de arriba.</td>
        </tr>
      }
    </tbody>
  </table>
```

Notar que el botón "Crear producto nuevo" dentro de `@if (sinResultados)` se agrega en Task 6 (este Task 4 deja el mensaje de "no encontrado" sin esa acción todavía, para no adelantar un componente que no existe hasta Task 6).

También reemplazar el párrafo de ayuda justo antes (`<p class="form-section-hint">Busca por <strong>SKU o nombre</strong>...`) por:

```html
  <p class="form-section-hint">
    Busca por <strong>nombre, SKU o código de barra</strong>. Selecciona un resultado (o presiona
    <strong>Enter</strong>) para agregarlo de inmediato; si ya está en la lista, suma una unidad más.
  </p>
```

- [ ] **Step 4: Estilos nuevos/ajustados**

En `frontend/src/app/features/movimientos/movimientos.component.scss`:
- Eliminar las reglas `.item-entry__row`, `.item-entry__col`, `.item-entry__col--qty`, `.item-entry__add-btn` (ya no aplican, la fila de "producto seleccionado + cantidad + botón +" desaparece).
- Agregar:

```scss
.item-entry__search {
  position: relative;
  max-width: 480px;
}

.items-table__qty {
  width: 72px;
  height: 32px;
  padding: 0 var(--space-2);
  text-align: right;
  border: var(--border-width-default) solid var(--border-strong);
  border-radius: var(--radius-2);
  font: var(--font-body-sm);
  background: var(--surface-card);
  color: var(--text-title);
}

.items-table__qty:focus {
  outline: none;
  border-width: var(--border-width-medium);
  border-color: var(--border-interactive);
}
```

- [ ] **Step 5: Verificación manual**

Run: `cd frontend && npx ng build --configuration development`
Expected: build limpio. Con Docker corriendo, en Movimientos: buscar un producto por nombre, por SKU y (si ya cargaste uno con código de barra en Task 2) por código de barra; confirmar que seleccionar un resultado agrega la línea de inmediato con cantidad 1; seleccionar el mismo producto de nuevo y confirmar que la cantidad sube a 2 en vez de duplicar la fila; editar la cantidad directamente en la tabla; confirmar el movimiento y verificar que llega bien al backend.

- [ ] **Step 6: Commit (junto con Task 3)**

```bash
git add frontend/src/app/features/movimientos/
git commit -m "Movimientos: busqueda con debounce contra el backend y seleccion instantanea"
```

---

### Task 5: Backend — importación masiva de movimientos desde Excel

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/java/cl/slimerp/inventario/BodegaRepository.java`
- Create: `backend/src/main/java/cl/slimerp/inventario/MovimientoImportService.java`
- Modify: `backend/src/main/java/cl/slimerp/inventario/MovimientoInventarioController.java`
- Create: `backend/src/test/java/cl/slimerp/inventario/MovimientoImportServiceTest.java`

**Interfaces:**
- Consumes: `MovimientoInventarioService.crear(Long tenantId, Long usuarioId, MovimientoRequest request)` (ya existe, sin cambios) para efectivamente crear cada grupo; `ProductoRepository.findFirstByTenantIdAndSku`/`findFirstByTenantIdAndCodigoBarra` (Task 1) para resolver la columna "Código".
- Produces:
  - `MovimientoImportService.importar(Long tenantId, Long usuarioId, InputStream xlsx): ImportResultado` — método único de entrada, usado por el controller.
  - `record ImportResultado(int totalFilas, List<Long> movimientosCreados, List<FilaError> errores)`.
  - `record FilaError(int numeroFila, String mensaje)`.
  - Endpoint `POST /api/movimientos/importar` (multipart, campo `archivo`) — usado por Task 6 (frontend).

- [ ] **Step 1: Agregar Apache POI al `pom.xml`**

En `backend/pom.xml`, dentro de `<dependencies>`, agregar (después de la dependencia `openpdf`):

```xml
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
            <version>5.2.5</version>
        </dependency>
```

- [ ] **Step 2: Nuevo método en `BodegaRepository`**

En `backend/src/main/java/cl/slimerp/inventario/BodegaRepository.java`, agregar junto a los otros `findBy...`:

```java
    Optional<Bodega> findFirstByTenantIdAndNombreIgnoreCaseAndActivoTrue(Long tenantId, String nombre);
```

- [ ] **Step 3: Escribir el test de `MovimientoImportService` primero**

```java
// backend/src/test/java/cl/slimerp/inventario/MovimientoImportServiceTest.java
package cl.slimerp.inventario;

import cl.slimerp.catalogo.Producto;
import cl.slimerp.catalogo.ProductoRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MovimientoImportServiceTest {

    private ProductoRepository productoRepository;
    private BodegaRepository bodegaRepository;
    private MovimientoInventarioService movimientoService;
    private MovimientoImportService importService;

    private final Long tenantId = 1L;
    private final Long usuarioId = 5L;
    private final Producto productoA = Producto.builder().id(10L).tenantId(1L).sku("SKU-A").nombre("Producto A").activo(true).build();
    private final Producto productoB = Producto.builder().id(20L).tenantId(1L).sku("SKU-B").codigoBarra("7801234567890").nombre("Producto B").activo(true).build();
    private final Bodega bodegaPrincipal = Bodega.builder().id(1L).tenantId(1L).nombre("Principal").activo(true).build();

    @BeforeEach
    void setUp() {
        productoRepository = mock(ProductoRepository.class);
        bodegaRepository = mock(BodegaRepository.class);
        movimientoService = mock(MovimientoInventarioService.class);
        importService = new MovimientoImportService(productoRepository, bodegaRepository, movimientoService);

        when(productoRepository.findFirstByTenantIdAndSku(tenantId, "SKU-A")).thenReturn(Optional.of(productoA));
        when(productoRepository.findFirstByTenantIdAndSku(tenantId, "SKU-B")).thenReturn(Optional.empty());
        when(productoRepository.findFirstByTenantIdAndCodigoBarra(tenantId, "SKU-B")).thenReturn(Optional.empty());
        when(productoRepository.findFirstByTenantIdAndSku(tenantId, "7801234567890")).thenReturn(Optional.empty());
        when(productoRepository.findFirstByTenantIdAndCodigoBarra(tenantId, "7801234567890")).thenReturn(Optional.of(productoB));
        when(bodegaRepository.findFirstByTenantIdAndNombreIgnoreCaseAndActivoTrue(tenantId, "Principal"))
                .thenReturn(Optional.of(bodegaPrincipal));

        MovimientoInventarioHeader header = new MovimientoInventarioHeader();
        header.setId(999L);
        when(movimientoService.crear(any(), any(), any())).thenReturn(header);
    }

    private InputStream workbook(String... filas) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Movimientos");
            String[] encabezado = {"Codigo", "Cantidad", "Tipo", "Bodega", "Bodega Origen", "Bodega Destino", "Observacion"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < encabezado.length; i++) header.createCell(i).setCellValue(encabezado[i]);

            for (int f = 0; f < filas.length; f++) {
                Row row = sheet.createRow(f + 1);
                String[] valores = filas[f].split("\\|", -1);
                for (int i = 0; i < valores.length; i++) row.createCell(i).setCellValue(valores[i]);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    @Test
    void agrupaFilasConMismoTipoBodegaYObservacionEnUnSoloMovimiento() throws IOException {
        InputStream xlsx = workbook(
                "SKU-A|2|ENTRADA|Principal|||Reposicion",
                "SKU-B|3|ENTRADA|Principal|||Reposicion"
        );

        var resultado = importService.importar(tenantId, usuarioId, xlsx);

        assertEquals(2, resultado.totalFilas());
        assertEquals(1, resultado.movimientosCreados().size());
        assertTrue(resultado.errores().isEmpty());
        verify(movimientoService, times(1)).crear(eq(tenantId), eq(usuarioId), any());
    }

    @Test
    void filasConDistintaObservacionQuedanEnMovimientosSeparados() throws IOException {
        InputStream xlsx = workbook(
                "SKU-A|2|ENTRADA|Principal|||Motivo uno",
                "SKU-A|1|ENTRADA|Principal|||Motivo dos"
        );

        var resultado = importService.importar(tenantId, usuarioId, xlsx);

        assertEquals(2, resultado.movimientosCreados().size());
        verify(movimientoService, times(2)).crear(eq(tenantId), eq(usuarioId), any());
    }

    @Test
    void filaConCodigoDesconocidoQuedaComoError() throws IOException {
        InputStream xlsx = workbook("NO-EXISTE|1|ENTRADA|Principal|||");

        var resultado = importService.importar(tenantId, usuarioId, xlsx);

        assertEquals(0, resultado.movimientosCreados().size());
        assertEquals(1, resultado.errores().size());
        assertEquals(2, resultado.errores().get(0).numeroFila());
    }

    @Test
    void trasladoSinBodegaOrigenNiDestinoQuedaComoError() throws IOException {
        InputStream xlsx = workbook("SKU-A|1|TRASLADO|||| ");

        var resultado = importService.importar(tenantId, usuarioId, xlsx);

        assertEquals(1, resultado.errores().size());
        assertTrue(resultado.errores().get(0).mensaje().toLowerCase().contains("traslado"));
    }

    @Test
    void grupoQueFallaEnMovimientoServiceQuedaComoErrorSinAbortarElResto() throws IOException {
        InputStream xlsx = workbook(
                "SKU-A|1|SALIDA|Principal|||",
                "SKU-B|1|ENTRADA|Principal|||"
        );
        when(movimientoService.crear(eq(tenantId), eq(usuarioId), argThat(r -> r.tipo() == TipoMovimiento.SALIDA)))
                .thenThrow(new IllegalArgumentException("Stock insuficiente"));

        var resultado = importService.importar(tenantId, usuarioId, xlsx);

        assertEquals(1, resultado.movimientosCreados().size());
        assertEquals(1, resultado.errores().size());
        assertTrue(resultado.errores().get(0).mensaje().contains("Stock insuficiente"));
    }
}
```

Run: `cd backend && ./mvnw test -Dtest=MovimientoImportServiceTest`
Expected: falla — `MovimientoImportService` no existe todavía.

- [ ] **Step 4: Implementar `MovimientoImportService`**

```java
// backend/src/main/java/cl/slimerp/inventario/MovimientoImportService.java
package cl.slimerp.inventario;

import cl.slimerp.catalogo.Producto;
import cl.slimerp.catalogo.ProductoRepository;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class MovimientoImportService {

    // Orden fijo de columnas esperado en la fila de encabezado (fila 1 del Excel).
    private static final List<String> COLUMNAS = List.of(
            "Codigo", "Cantidad", "Tipo", "Bodega", "Bodega Origen", "Bodega Destino", "Observacion");

    private final ProductoRepository productoRepository;
    private final BodegaRepository bodegaRepository;
    private final MovimientoInventarioService movimientoService;
    private final DataFormatter dataFormatter = new DataFormatter();

    public MovimientoImportService(ProductoRepository productoRepository,
                                    BodegaRepository bodegaRepository,
                                    MovimientoInventarioService movimientoService) {
        this.productoRepository = productoRepository;
        this.bodegaRepository = bodegaRepository;
        this.movimientoService = movimientoService;
    }

    public record ImportResultado(int totalFilas, List<Long> movimientosCreados, List<FilaError> errores) {}

    public record FilaError(int numeroFila, String mensaje) {}

    // Fila ya resuelta contra el catálogo, lista para agruparse.
    private record FilaResuelta(int numeroFila, Long productoId, BigDecimal cantidad,
                                 TipoMovimiento tipo, Long bodegaOrigenId, Long bodegaDestinoId,
                                 String observacion) {}

    private record ClaveGrupo(TipoMovimiento tipo, Long bodegaOrigenId, Long bodegaDestinoId, String observacion) {}

    public ImportResultado importar(Long tenantId, Long usuarioId, InputStream xlsx) {
        List<String[]> filasCrudas = leerFilas(xlsx);
        List<FilaError> errores = new ArrayList<>();
        Map<ClaveGrupo, List<FilaResuelta>> grupos = new LinkedHashMap<>();

        for (int i = 0; i < filasCrudas.size(); i++) {
            int numeroFila = i + 2; // fila 1 es encabezado
            try {
                FilaResuelta fila = resolverFila(tenantId, numeroFila, filasCrudas.get(i));
                ClaveGrupo clave = new ClaveGrupo(fila.tipo(), fila.bodegaOrigenId(), fila.bodegaDestinoId(), fila.observacion());
                grupos.computeIfAbsent(clave, k -> new ArrayList<>()).add(fila);
            } catch (IllegalArgumentException e) {
                errores.add(new FilaError(numeroFila, e.getMessage()));
            }
        }

        List<Long> creados = new ArrayList<>();
        for (Map.Entry<ClaveGrupo, List<FilaResuelta>> entry : grupos.entrySet()) {
            ClaveGrupo clave = entry.getKey();
            List<FilaResuelta> filasDelGrupo = entry.getValue();
            try {
                var request = new MovimientoInventarioService.MovimientoRequest(
                        clave.tipo(), clave.bodegaOrigenId(), clave.bodegaDestinoId(), clave.observacion(),
                        combinarItems(filasDelGrupo));
                MovimientoInventarioHeader header = movimientoService.crear(tenantId, usuarioId, request);
                creados.add(header.getId());
            } catch (IllegalArgumentException e) {
                for (FilaResuelta fila : filasDelGrupo) {
                    errores.add(new FilaError(fila.numeroFila(), e.getMessage()));
                }
            }
        }

        errores.sort((a, b) -> Integer.compare(a.numeroFila(), b.numeroFila()));
        return new ImportResultado(filasCrudas.size(), creados, errores);
    }

    // Si dos filas del mismo grupo referencian el mismo producto, se suman en un solo ítem.
    private List<MovimientoInventarioService.MovimientoItemRequest> combinarItems(List<FilaResuelta> filas) {
        Map<Long, BigDecimal> porProducto = new LinkedHashMap<>();
        for (FilaResuelta fila : filas) {
            porProducto.merge(fila.productoId(), fila.cantidad(), BigDecimal::add);
        }
        return porProducto.entrySet().stream()
                .map(e -> new MovimientoInventarioService.MovimientoItemRequest(e.getKey(), e.getValue()))
                .toList();
    }

    private FilaResuelta resolverFila(Long tenantId, int numeroFila, String[] valores) {
        String codigo = valor(valores, 0);
        String cantidadTexto = valor(valores, 1);
        String tipoTexto = valor(valores, 2);
        String bodegaTexto = valor(valores, 3);
        String bodegaOrigenTexto = valor(valores, 4);
        String bodegaDestinoTexto = valor(valores, 5);
        String observacion = valor(valores, 6);

        if (codigo == null) throw new IllegalArgumentException("Falta el código del producto");
        Producto producto = productoRepository.findFirstByTenantIdAndSku(tenantId, codigo)
                .or(() -> productoRepository.findFirstByTenantIdAndCodigoBarra(tenantId, codigo))
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado para el código \"" + codigo + "\""));

        BigDecimal cantidad = parseCantidad(cantidadTexto);
        TipoMovimiento tipo = parseTipo(tipoTexto);

        Long bodegaOrigenId = resolverBodega(tenantId, bodegaOrigenTexto != null ? bodegaOrigenTexto : bodegaTexto);
        Long bodegaDestinoId = resolverBodega(tenantId, bodegaDestinoTexto != null ? bodegaDestinoTexto : bodegaTexto);

        if (tipo == TipoMovimiento.TRASLADO && (bodegaOrigenTexto == null || bodegaDestinoTexto == null)) {
            throw new IllegalArgumentException("Traslado requiere Bodega Origen y Bodega Destino");
        }

        return new FilaResuelta(numeroFila, producto.getId(), cantidad, tipo, bodegaOrigenId, bodegaDestinoId, observacion);
    }

    private Long resolverBodega(Long tenantId, String nombre) {
        if (nombre == null) return null;
        return bodegaRepository.findFirstByTenantIdAndNombreIgnoreCaseAndActivoTrue(tenantId, nombre)
                .map(Bodega::getId)
                .orElseThrow(() -> new IllegalArgumentException("Bodega no encontrada: \"" + nombre + "\""));
    }

    private BigDecimal parseCantidad(String texto) {
        if (texto == null) throw new IllegalArgumentException("Falta la cantidad");
        try {
            BigDecimal cantidad = new BigDecimal(texto.trim().replace(",", "."));
            if (cantidad.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
            return cantidad;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cantidad inválida: \"" + texto + "\"");
        }
    }

    private TipoMovimiento parseTipo(String texto) {
        if (texto == null) throw new IllegalArgumentException("Falta el tipo de movimiento");
        try {
            return TipoMovimiento.valueOf(texto.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de movimiento inválido: \"" + texto + "\" (use ENTRADA, SALIDA, TRASLADO o AJUSTE)");
        }
    }

    private String valor(String[] valores, int indice) {
        if (indice >= valores.length) return null;
        String v = valores[indice];
        if (v == null) return null;
        String limpio = v.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private List<String[]> leerFilas(InputStream xlsx) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(xlsx)) {
            Sheet sheet = workbook.getSheetAt(0);
            List<String[]> filas = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || esFilaVacia(row)) continue;
                String[] valores = new String[COLUMNAS.size()];
                for (int c = 0; c < COLUMNAS.size(); c++) {
                    valores[c] = row.getCell(c) == null ? null : dataFormatter.formatCellValue(row.getCell(c));
                }
                filas.add(valores);
            }
            return filas;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo Excel", e);
        }
    }

    private boolean esFilaVacia(Row row) {
        for (int c = 0; c < COLUMNAS.size(); c++) {
            var cell = row.getCell(c);
            if (cell != null && !dataFormatter.formatCellValue(cell).isBlank()) return false;
        }
        return true;
    }
}
```

- [ ] **Step 5: Correr el test de nuevo**

Run: `cd backend && ./mvnw test -Dtest=MovimientoImportServiceTest`
Expected: los 5 tests pasan.

- [ ] **Step 6: Endpoint en `MovimientoInventarioController`**

En `backend/src/main/java/cl/slimerp/inventario/MovimientoInventarioController.java`:
- Agregar los imports `org.springframework.web.multipart.MultipartFile` y `java.io.IOException`.
- Agregar el campo `private final MovimientoImportService importService;` junto a los otros campos `private final ...`.
- Reemplazar el constructor completo:

```java
    public MovimientoInventarioController(MovimientoInventarioService service,
                                           ProductoRepository productoRepository,
                                           BodegaRepository bodegaRepository,
                                           UsuarioRepository usuarioRepository,
                                           MovimientoImportService importService) {
        this.service = service;
        this.productoRepository = productoRepository;
        this.bodegaRepository = bodegaRepository;
        this.usuarioRepository = usuarioRepository;
        this.importService = importService;
    }
```

- Agregar el endpoint, junto a `crear`:

```java
    @PostMapping(value = "/importar", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('MOVIMIENTOS_EDITAR')")
    public MovimientoImportService.ImportResultado importar(@RequestParam("archivo") MultipartFile archivo) throws IOException {
        Long tenantId = TenantContext.getTenantId();
        Long usuarioId = resolveUsuarioId(tenantId);
        return importService.importar(tenantId, usuarioId, archivo.getInputStream());
    }
```

- [ ] **Step 7: Correr toda la suite del módulo `inventario`**

Run: `cd backend && ./mvnw test -Dtest=cl.slimerp.inventario.*`
Expected: todos los tests (los existentes de `MovimientoInventarioServiceTest` y los nuevos de `MovimientoImportServiceTest`) pasan.

- [ ] **Step 8: Commit**

```bash
git add backend/pom.xml backend/src/main/java/cl/slimerp/inventario/ backend/src/test/java/cl/slimerp/inventario/MovimientoImportServiceTest.java
git commit -m "Agregar importacion masiva de movimientos desde Excel"
```

---

### Task 6: Frontend — diálogo de creación rápida de producto + UI de carga de Excel

**Files:**
- Create: `frontend/src/app/features/movimientos/producto-rapido-dialog.component.ts`
- Modify: `frontend/src/app/features/movimientos/movimientos.component.ts`
- Modify: `frontend/src/app/features/movimientos/movimientos.component.html`
- Modify: `frontend/src/app/features/movimientos/movimientos.component.scss`
- Modify: `frontend/src/app/core/services/movimiento.service.ts`
- Create: `frontend/public/plantillas/movimientos-carga-masiva.xlsx` (ver Step 6 — no es código, es un asset binario)

**Interfaces:**
- Consumes: `MovimientosComponent.sinResultados`/`seleccionarProducto`/`productosConocidos` (Task 3/4); `ProductoService.crear` (ya existe); endpoint `POST /api/movimientos/importar` (Task 5).
- Produces: nada consumido por otro task — es el último eslabón de la cadena.

- [ ] **Step 1: Diálogo de creación rápida**

```ts
// frontend/src/app/features/movimientos/producto-rapido-dialog.component.ts
import { Component, Inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { CategoriaService } from '../../core/services/categoria.service';
import { ProductoService, ProductoRequest } from '../../core/services/producto.service';
import { Categoria, Producto } from '../../core/models/models';

export interface ProductoRapidoDialogData {
  textoBusqueda: string;
}

// Un código de barra real (EAN-8/EAN-13/UPC) es siempre numérico y de 6+ dígitos;
// si el texto buscado calza con ese patrón, se precarga como código de barra.
function pareceCodigoBarra(texto: string): boolean {
  return /^\d{6,}$/.test(texto.trim());
}

@Component({
  selector: 'app-producto-rapido-dialog',
  standalone: true,
  imports: [FormsModule, MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Crear producto nuevo</h2>
    <mat-dialog-content class="rapido-dialog-content">
      <form class="form-grid">
        <div class="form-group span-2">
          <label for="rapidoNombre">Nombre<span class="required-mark">*</span></label>
          <input id="rapidoNombre" [(ngModel)]="nombre" name="nombre" required />
        </div>

        <div class="form-group">
          <label for="rapidoSku">SKU <span class="hint">(opcional)</span></label>
          <input id="rapidoSku" [(ngModel)]="sku" name="sku" />
        </div>

        <div class="form-group">
          <label for="rapidoCodigoBarra">Código de barra <span class="hint">(opcional)</span></label>
          <input id="rapidoCodigoBarra" [(ngModel)]="codigoBarra" name="codigoBarra" />
        </div>

        <div class="form-group">
          <label for="rapidoCategoria">Categoría <span class="hint">(opcional)</span></label>
          <select id="rapidoCategoria" [(ngModel)]="categoriaId" name="categoriaId">
            <option [ngValue]="null">Sin categoría</option>
            @for (c of categorias(); track c.id) {
              <option [ngValue]="c.id">{{ c.nombre }}</option>
            }
          </select>
        </div>

        <div class="form-group">
          <label for="rapidoPrecioVenta">Precio venta<span class="required-mark">*</span></label>
          <input id="rapidoPrecioVenta" type="number" min="0" [(ngModel)]="precioVenta" name="precioVenta" required />
        </div>

        <div class="form-group">
          <label for="rapidoPrecioCompra">Precio compra <span class="hint">(opcional)</span></label>
          <input id="rapidoPrecioCompra" type="number" min="0" [(ngModel)]="precioCompra" name="precioCompra" />
        </div>

        <div class="form-group">
          <label for="rapidoStockMinimo">Stock mínimo <span class="hint">(opcional)</span></label>
          <input id="rapidoStockMinimo" type="number" min="0" [(ngModel)]="stockMinimo" name="stockMinimo" />
        </div>
      </form>
      @if (error()) {
        <p class="field-error">{{ error() }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button type="button" mat-button mat-dialog-close [disabled]="guardando()">Cancelar</button>
      <button type="button" mat-flat-button color="primary" [disabled]="!nombre || !precioVenta || guardando()" (click)="guardar()">
        {{ guardando() ? 'Creando...' : 'Crear y agregar' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .rapido-dialog-content {
        min-width: min(520px, 80vw);
      }
    `,
  ],
})
export class ProductoRapidoDialogComponent implements OnInit {
  nombre = '';
  sku = '';
  codigoBarra = '';
  categoriaId: number | null = null;
  precioVenta: number | null = null;
  precioCompra: number | null = null;
  stockMinimo: number | null = null;

  categorias = signal<Categoria[]>([]);
  error = signal<string | null>(null);
  guardando = signal(false);

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: ProductoRapidoDialogData,
    public dialogRef: MatDialogRef<ProductoRapidoDialogComponent, Producto | undefined>,
    private categoriaService: CategoriaService,
    private productoService: ProductoService
  ) {
    this.nombre = data.textoBusqueda && !pareceCodigoBarra(data.textoBusqueda) ? data.textoBusqueda : '';
    this.codigoBarra = pareceCodigoBarra(data.textoBusqueda ?? '') ? data.textoBusqueda.trim() : '';
  }

  ngOnInit(): void {
    this.categoriaService.listar().subscribe((data) => this.categorias.set(data));
  }

  guardar(): void {
    if (!this.nombre || !this.precioVenta) return;
    this.guardando.set(true);
    this.error.set(null);
    const request: ProductoRequest = {
      nombre: this.nombre,
      sku: this.sku || null,
      codigoBarra: this.codigoBarra || null,
      categoriaId: this.categoriaId,
      precioVenta: this.precioVenta,
      precioCompra: this.precioCompra ?? 0,
      stockMinimo: this.stockMinimo ?? 0,
    };
    this.productoService.crear(request).subscribe({
      next: (producto) => {
        this.guardando.set(false);
        this.dialogRef.close(producto);
      },
      error: (err) => {
        this.guardando.set(false);
        this.error.set(err?.error?.error ?? 'No se pudo crear el producto.');
      },
    });
  }
}
```

- [ ] **Step 2: Wiring en `MovimientosComponent`**

En `movimientos.component.ts`:
- Agregar imports: `import { MatDialog, MatDialogModule } from '@angular/material/dialog';` y
  `import { ProductoRapidoDialogComponent, ProductoRapidoDialogData } from './producto-rapido-dialog.component';`.
- Agregar `MatDialogModule` al arreglo `imports` del `@Component`.
- Agregar `private dialog: MatDialog` al constructor (junto a los otros servicios inyectados).
- Agregar el método:

```ts
  abrirCreacionRapida(): void {
    const data: ProductoRapidoDialogData = { textoBusqueda: this.filtroProducto };
    this.dialog
      .open(ProductoRapidoDialogComponent, { data })
      .afterClosed()
      .subscribe((producto) => {
        if (!producto) return;
        this.seleccionarProducto(producto);
      });
  }
```

- [ ] **Step 3: Botón en la plantilla, dentro de `@if (sinResultados)`**

En `movimientos.component.html`, dentro del bloque `@if (sinResultados) { <div class="search-results"> ... }` agregado en Task 4 Step 3, agregar el botón después del mensaje:

```html
      @if (sinResultados) {
        <div class="search-results">
          <div class="search-results__hint">No se encontró ningún producto para "{{ filtroProducto }}".</div>
          <button type="button" mat-button (click)="abrirCreacionRapida()">Crear producto nuevo</button>
        </div>
      }
```

- [ ] **Step 4: `MovimientoService.importarExcel`**

En `frontend/src/app/core/services/movimiento.service.ts`, agregar:

```ts
export interface ImportFilaError {
  numeroFila: number;
  mensaje: string;
}

export interface ImportResultado {
  totalFilas: number;
  movimientosCreados: number[];
  errores: ImportFilaError[];
}
```

y dentro de la clase `MovimientoService`:

```ts
  importarExcel(archivo: File): Observable<ImportResultado> {
    const formData = new FormData();
    formData.append('archivo', archivo);
    return this.http.post<ImportResultado>(`${this.base}/importar`, formData);
  }
```

- [ ] **Step 5: UI de carga de Excel en `MovimientosComponent`**

En `movimientos.component.ts`:
- Importar `ImportResultado` desde `movimiento.service`.
- Agregar estado: `resultadoImportacion: ImportResultado | null = null; importando = false;`.
- Agregar métodos:

```ts
  onArchivoExcelSeleccionado(event: Event): void {
    const input = event.target as HTMLInputElement;
    const archivo = input.files?.[0];
    input.value = '';
    if (!archivo) return;
    this.importando = true;
    this.resultadoImportacion = null;
    this.movimientoService.importarExcel(archivo).subscribe({
      next: (resultado) => {
        this.resultadoImportacion = resultado;
        this.importando = false;
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'No se pudo importar el archivo.';
        this.importando = false;
      },
    });
  }
```

En `movimientos.component.html`, agregar una tarjeta nueva después de la de "Detalle de productos" (antes de la de resumen):

```html
<mat-card class="form-panel">
  <h2>Carga masiva por Excel</h2>
  <p class="form-section-hint">
    Sube un archivo con columnas <strong>Codigo, Cantidad, Tipo, Bodega, Observacion</strong>
    (para traslados, usa <strong>Bodega Origen</strong> y <strong>Bodega Destino</strong> en vez de Bodega).
    Cada movimiento importado queda registrado de inmediato, sin pasar por el formulario de arriba.
  </p>

  <div class="form-actions form-actions--start">
    <a href="/plantillas/movimientos-carga-masiva.xlsx" download mat-stroked-button>
      <mat-icon>download</mat-icon>
      Descargar plantilla
    </a>
    <button type="button" mat-stroked-button (click)="excelInput.click()" [disabled]="importando">
      <mat-icon>upload</mat-icon>
      {{ importando ? 'Importando...' : 'Cargar Excel' }}
    </button>
    <input #excelInput type="file" accept=".xlsx" hidden (change)="onArchivoExcelSeleccionado($event)" />
  </div>

  @if (resultadoImportacion; as r) {
    <p class="page-mensaje">
      {{ r.movimientosCreados.length }} movimiento(s) creado(s) de {{ r.totalFilas }} fila(s) leídas.
      @if (r.errores.length) {
        {{ r.errores.length }} fila(s) con error.
      }
    </p>
    @if (r.movimientosCreados.length) {
      <a mat-button routerLink="/movimientos/historial">Ver en el historial</a>
    }
    @if (r.errores.length) {
      <table class="items-table">
        <thead>
          <tr>
            <th>Fila</th>
            <th>Motivo</th>
          </tr>
        </thead>
        <tbody>
          @for (e of r.errores; track e.numeroFila) {
            <tr>
              <td>{{ e.numeroFila }}</td>
              <td>{{ e.mensaje }}</td>
            </tr>
          }
        </tbody>
      </table>
    }
  }
</mat-card>
```

`form-actions--start` no existe todavía en este componente: agregar en `movimientos.component.scss`:

```scss
.form-actions--start {
  justify-content: flex-start;
}
```

(`.form-actions` global ya trae `justify-content: flex-end` — este modificador local lo alinea a la izquierda para esta tarjeta.)

- [ ] **Step 6: Generar la plantilla Excel estática**

Crear `frontend/public/plantillas/movimientos-carga-masiva.xlsx` con las columnas
`Codigo | Cantidad | Tipo | Bodega | Bodega Origen | Bodega Destino | Observacion`
en la fila 1, y dos filas de ejemplo:
- `SKU-001 | 10 | ENTRADA | Bodega Principal | | | Reposición de stock`
- `SKU-002 | 5 | TRASLADO | | Bodega Principal | Bodega Secundaria | Traslado entre sucursales`

Generarlo con un script corto de una sola vez (no queda en el repo como código fuente, solo el `.xlsx` resultante), por ejemplo con `python3` + `openpyxl` si está disponible en el entorno, o con cualquier herramienta a mano (Excel/LibreOffice) — lo único que importa es que el archivo final tenga exactamente esos encabezados en la fila 1. Verificar que `frontend/angular.json` sirva `public/` como raíz de assets (ya debería, es el comportamiento por defecto de Angular 18 con la carpeta `public/`); si no fuera así, copiarlo a `frontend/src/assets/plantillas/` y ajustar el `href` del Step 5 a `/assets/plantillas/movimientos-carga-masiva.xlsx`.

- [ ] **Step 7: Verificación manual completa**

Run: `cd frontend && npx ng build --configuration development`
Expected: build limpio.

Con Docker corriendo (`docker compose build frontend && docker compose up -d frontend`):
1. En Movimientos, buscar un código que no exista → aparece "Crear producto nuevo" → abrir el diálogo, completar Nombre y Precio venta, guardar → el producto se crea y se agrega de inmediato a la lista con cantidad 1.
2. Descargar la plantilla, completarla con un producto y bodega reales del tenant de prueba, subirla con "Cargar Excel" → confirmar el resumen y que el movimiento aparece en el historial.
3. Subir un Excel con una fila de código inexistente → confirmar que aparece en la tabla de errores con el número de fila correcto y que el resto del archivo (si tiene más filas válidas) sí se importa.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/app/features/movimientos/ frontend/src/app/core/services/movimiento.service.ts frontend/public/plantillas/
git commit -m "Movimientos: crear producto al vuelo y carga masiva por Excel"
```

---

## Nota sobre el orden de ejecución

Task 3 y Task 4 tocan los mismos tres archivos de `movimientos.component.*` y están pensadas para aplicarse como una sola unidad de trabajo (el Task 3 Step 6 lo señala explícitamente): un ejecutor de este plan puede fusionarlas en un solo dispatch si su herramienta de ejecución lo permite, en vez de dispatchear Task 4 como si Task 3 ya hubiera dejado el árbol en un estado buildable de forma independiente. Task 6 depende de que Task 3+4 y Task 5 estén terminadas (usa `sinResultados`/`seleccionarProducto` del primero y el endpoint `/importar` del segundo).
