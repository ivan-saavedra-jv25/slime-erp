# Libro de Ventas consolidado — Design

## Contexto

El sistema registra ventas de tres tipos internos (`TipoDocumentoVenta`:
`BOLETA`, `FACTURA`, `VOUCHER`) más el flag `exento`, que juntos equivalen
conceptualmente a los tipos de documento SII 33 (Factura), 34 (Factura
Exenta), 39 (Boleta), 41 (Boleta Exenta), más el voucher interno (sin
equivalente SII). Hoy no existe ninguna vista consolidada de estas ventas
por período: `GET /api/ventas` trae *todo* el historial sin filtro de
fecha ni agrupación, y la única pantalla parecida
(`ventas-historial.component`) es una lista simple sin subtotales ni
exportación.

Este documento describe un **Libro de Ventas**: un reporte por rango de
fechas que junta las ventas de todos los tipos de documento, con
subtotales por tipo (Neto/IVA/Total) y un total general, más exportación a
Excel. Es el primer módulo de reportería del proyecto — no existe paquete
`reporteria` ni convención previa que seguir más allá del patrón general
de controladores delgados que ya usa `VentaController`.

**Decisiones ya tomadas con el usuario:**
- El "tipo de documento" se muestra como **etiqueta interna** (Factura,
  Factura Exenta, Boleta, Boleta Exenta, Voucher) — **no** se exponen
  códigos SII (33/34/39/41) porque el modelo no guarda folio ni código
  numérico real, y agregarlo está fuera de alcance de este reporte.
- Filtro de período: **rango de fechas libre** (`desde`/`hasta`), no un
  selector de mes/año.
- Exportación: **Excel** (`.xlsx`) únicamente, no CSV ni PDF.
- Totales: **subtotales por tipo de documento + total general**, no solo
  un total consolidado.

## Global Constraints

- El `id` de toda tabla es autoincremental y lo genera la base de datos —
  el frontend nunca lo envía (regla del proyecto, `CLAUDE.md`).
- No se agrega ningún campo nuevo a `Venta` ni a `Cliente`, y **no hay
  migración de base de datos** en este feature — todo el reporte se arma
  a partir de datos ya existentes.
- Todo endpoint nuevo sigue el patrón multi-tenant existente
  (`TenantContext.getTenantId()`, `@PreAuthorize("hasAuthority(...)")`,
  errores de negocio como `IllegalArgumentException` capturados por
  `GlobalExceptionHandler`).
- El endpoint reutiliza el permiso `VENTAS_VER` — no se crea un permiso
  `REPORTES_*` nuevo (no hay otro reporte en el sistema hoy que lo
  justifique).
- Formularios/pantallas nuevas siguen el sistema de diseño existente:
  HTML nativo (`.form-group`, inputs) con Material solo para botones,
  tarjetas, tablas e íconos.
- `CalculadoraMontosVenta` ya garantiza que, para documentos exentos,
  `montoNeto == montoTotal` y `montoIva == 0` — el reporte no debe
  recalcular montos, solo leer los que ya trae `Venta`.
- **No se debe generar ningún comando de git** durante la implementación
  de este feature (ni `git add`, ni `git commit`, ni ningún otro) — ni el
  plan ni los subagentes que lo ejecuten deben incluir pasos de git. Los
  cambios quedan en el árbol de trabajo para que el usuario los revise y
  comitee manualmente cuando lo decida.

---

## 1. Backend — `cl.slimerp.reporteria`

### 1.1 Repositorios

**`VentaRepository`** (`backend/src/main/java/cl/slimerp/ventas/VentaRepository.java`)
gana un método nuevo (sin `@EntityGraph` de `detalle` — el reporte no
necesita líneas de detalle, solo los totales de cabecera):

```java
List<Venta> findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(
        Long tenantId, LocalDateTime desde, LocalDateTime hasta);
```

**`ClienteRepository`** (`backend/src/main/java/cl/slimerp/catalogo/ClienteRepository.java`)
gana un método nuevo para resolver nombre/RUT de todos los clientes
involucrados en una sola consulta (evita N+1):

```java
List<Cliente> findByTenantIdAndIdIn(Long tenantId, List<Long> ids);
```

### 1.2 DTOs (records, en el nuevo paquete `cl.slimerp.reporteria`)

```java
public record LibroVentasFila(
        Long ventaId,
        LocalDateTime fecha,
        String tipoDocumento,     // etiqueta: "Factura", "Factura Exenta", "Boleta", "Boleta Exenta", "Voucher"
        String clienteRut,        // puede ser null si el cliente no tiene RUT cargado
        String clienteNombre,
        BigDecimal montoNeto,
        BigDecimal montoIva,
        BigDecimal montoTotal) {
}

public record LibroVentasSubtotal(
        String tipoDocumento,
        int cantidad,
        BigDecimal montoNeto,
        BigDecimal montoIva,
        BigDecimal montoTotal) {
}

public record LibroVentasResponse(
        LocalDate desde,
        LocalDate hasta,
        List<LibroVentasFila> filas,
        List<LibroVentasSubtotal> subtotales,
        LibroVentasSubtotal totalGeneral) {
}
```

`totalGeneral` usa `tipoDocumento = "Total"`.

### 1.3 `LibroVentasService`

```java
package cl.slimerp.reporteria;

@Service
public class LibroVentasService {

    private final VentaRepository ventaRepository;
    private final ClienteRepository clienteRepository;

    // ... constructor con inyección ...

    public LibroVentasResponse generar(Long tenantId, LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'");
        }
        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin = hasta.atTime(LocalTime.MAX);

        List<Venta> ventas = ventaRepository
                .findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(tenantId, inicio, fin);

        List<Long> clienteIds = ventas.stream().map(Venta::getClienteId).distinct().toList();
        Map<Long, Cliente> clientesPorId = clienteIds.isEmpty()
                ? Map.of()
                : clienteRepository.findByTenantIdAndIdIn(tenantId, clienteIds).stream()
                        .collect(Collectors.toMap(Cliente::getId, c -> c));

        List<LibroVentasFila> filas = ventas.stream()
                .map(v -> mapearFila(v, clientesPorId.get(v.getClienteId())))
                .toList();

        List<LibroVentasSubtotal> subtotales = agruparSubtotales(filas);
        LibroVentasSubtotal totalGeneral = totalizar(filas);

        return new LibroVentasResponse(desde, hasta, filas, subtotales, totalGeneral);
    }

    private LibroVentasFila mapearFila(Venta venta, Cliente cliente) {
        return new LibroVentasFila(
                venta.getId(),
                venta.getFecha(),
                etiquetaTipoDocumento(venta.getTipoDocumento(), venta.isExento()),
                cliente != null ? cliente.getRut() : null,
                cliente != null ? cliente.getNombre() : "—",
                venta.getMontoNeto(),
                venta.getMontoIva(),
                venta.getMontoTotal());
    }

    // Orden fijo Factura -> Factura Exenta -> Boleta -> Boleta Exenta -> Voucher,
    // no orden alfabético ni de aparición, para que el libro sea legible siempre
    // en el mismo orden aunque un tipo no tenga ventas en el período.
    static String etiquetaTipoDocumento(TipoDocumentoVenta tipo, boolean exento) {
        return switch (tipo) {
            case FACTURA -> exento ? "Factura Exenta" : "Factura";
            case BOLETA -> exento ? "Boleta Exenta" : "Boleta";
            case VOUCHER -> "Voucher";
        };
    }

    private List<LibroVentasSubtotal> agruparSubtotales(List<LibroVentasFila> filas) {
        List<String> orden = List.of("Factura", "Factura Exenta", "Boleta", "Boleta Exenta", "Voucher");
        Map<String, List<LibroVentasFila>> porTipo = filas.stream()
                .collect(Collectors.groupingBy(LibroVentasFila::tipoDocumento));

        return orden.stream()
                .filter(porTipo::containsKey)
                .map(tipo -> {
                    List<LibroVentasFila> delTipo = porTipo.get(tipo);
                    return new LibroVentasSubtotal(
                            tipo,
                            delTipo.size(),
                            sumar(delTipo, LibroVentasFila::montoNeto),
                            sumar(delTipo, LibroVentasFila::montoIva),
                            sumar(delTipo, LibroVentasFila::montoTotal));
                })
                .toList();
    }

    private LibroVentasSubtotal totalizar(List<LibroVentasFila> filas) {
        return new LibroVentasSubtotal(
                "Total",
                filas.size(),
                sumar(filas, LibroVentasFila::montoNeto),
                sumar(filas, LibroVentasFila::montoIva),
                sumar(filas, LibroVentasFila::montoTotal));
    }

    private BigDecimal sumar(List<LibroVentasFila> filas, Function<LibroVentasFila, BigDecimal> extractor) {
        return filas.stream().map(extractor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
```

Nota: si `filas` está vacío, `subtotales` es una lista vacía y
`totalGeneral` es `("Total", 0, 0, 0, 0)` — el frontend debe manejar el
caso "sin ventas en el período" sin error.

### 1.4 `LibroVentasExcelService`

Genera el `.xlsx` con Apache POI (`poi-ooxml` 5.2.5, ya es dependencia del
proyecto — hoy solo se usa para *leer* en `MovimientoImportService`, esta
sería la primera vez que el backend *escribe* un Excel).

```java
package cl.slimerp.reporteria;

@Service
public class LibroVentasExcelService {

    public byte[] generar(LibroVentasResponse libro) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet hoja = workbook.createSheet("Libro de Ventas");
            CellStyle estiloTitulo = estiloNegrita(workbook);
            CellStyle estiloEncabezado = estiloEncabezado(workbook);

            int fila = 0;
            fila = escribirTitulo(hoja, fila, libro, estiloTitulo);
            fila = escribirSubtotales(hoja, fila, libro, estiloEncabezado);
            fila++; // fila en blanco entre bloques
            escribirDetalle(hoja, fila, libro, estiloEncabezado);

            for (int col = 0; col <= 6; col++) {
                hoja.autoSizeColumn(col);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el Excel del Libro de Ventas", e);
        }
    }

    // ... escribirTitulo / escribirSubtotales / escribirDetalle / estiloNegrita / estiloEncabezado:
    // helpers privados que arman filas con Row/Cell de POI. escribirSubtotales itera
    // libro.subtotales() + libro.totalGeneral() (columnas: Tipo, Cantidad, Neto, IVA, Total).
    // escribirDetalle itera libro.filas() con encabezado
    // (N°, Fecha, Tipo documento, RUT, Cliente, Neto, IVA, Total) y formato de fecha dd-MM-yyyy.
}
```

### 1.5 `LibroVentasController`

```java
package cl.slimerp.reporteria;

@RestController
@RequestMapping("/api/reportes")
public class LibroVentasController {

    private final LibroVentasService libroVentasService;
    private final LibroVentasExcelService libroVentasExcelService;

    // ... constructor ...

    @GetMapping("/libro-ventas")
    @PreAuthorize("hasAuthority('VENTAS_VER')")
    public LibroVentasResponse libroVentas(
            @RequestParam LocalDate desde,
            @RequestParam LocalDate hasta) {
        return libroVentasService.generar(TenantContext.getTenantId(), desde, hasta);
    }

    @GetMapping("/libro-ventas/excel")
    @PreAuthorize("hasAuthority('VENTAS_VER')")
    public ResponseEntity<byte[]> libroVentasExcel(
            @RequestParam LocalDate desde,
            @RequestParam LocalDate hasta) {
        LibroVentasResponse libro = libroVentasService.generar(TenantContext.getTenantId(), desde, hasta);
        byte[] excel = libroVentasExcelService.generar(libro);
        String nombreArchivo = "libro-ventas-" + desde + "-a-" + hasta + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + nombreArchivo)
                .body(excel);
    }
}
```

`IllegalArgumentException` (rango de fechas inválido) ya es manejada por
`GlobalExceptionHandler` existente — sin código nuevo de manejo de
errores.

---

## 2. Frontend — `features/reportes/libro-ventas`

### 2.1 Modelos y servicio

`frontend/src/app/core/models/models.ts` gana las interfaces espejo de
los DTOs backend (`LibroVentasFila`, `LibroVentasSubtotal`,
`LibroVentasResponse`).

Nuevo `frontend/src/app/core/services/reporte.service.ts`:

```ts
@Injectable({ providedIn: 'root' })
export class ReporteService {
  private readonly base = `${environment.apiUrl}/reportes`;

  constructor(private http: HttpClient) {}

  libroVentas(desde: string, hasta: string): Observable<LibroVentasResponse> {
    return this.http.get<LibroVentasResponse>(`${this.base}/libro-ventas`, { params: { desde, hasta } });
  }

  libroVentasExcel(desde: string, hasta: string): Observable<Blob> {
    return this.http.get(`${this.base}/libro-ventas/excel`, {
      params: { desde, hasta },
      responseType: 'blob',
    });
  }
}
```

`desde`/`hasta` viajan como `string` en formato `yyyy-MM-dd` (valor nativo
de `<input type="date">`).

### 2.2 `LibroVentasComponent`

Ruta nueva `/reportes/libro-ventas`, agregada al módulo de rutas y al
menú lateral (`frontend/src/app/layout/layout.component.ts`) como un
grupo nuevo, siguiendo exactamente el patrón de los grupos existentes
(`GRUPOS: NavGroup[]`):

```ts
{
  key: 'reportes',
  titulo: 'Reportes',
  icono: 'bar_chart',
  items: [
    { ruta: '/reportes/libro-ventas', label: 'Libro de Ventas', icono: 'receipt_long', permiso: 'VENTAS_VER' },
  ],
},
```

Se agrega como grupo propio (no dentro de "Operación") porque es el
primer módulo de reportería y sienta el lugar natural para futuros
reportes (libro de compras, etc.), en vez de mezclar reportería con
transacciones dentro de "Operación".

Estado y comportamiento:

```ts
export class LibroVentasComponent implements OnInit {
  desde = primerDiaDelMes();   // yyyy-MM-dd
  hasta = hoy();                // yyyy-MM-dd
  libro: LibroVentasResponse | null = null;
  cargando = false;
  exportando = false;
  error = '';

  ngOnInit(): void {
    this.consultar();
  }

  consultar(): void {
    if (this.desde > this.hasta) {
      this.error = 'La fecha "desde" no puede ser posterior a "hasta".';
      return;
    }
    this.error = '';
    this.cargando = true;
    this.reporteService.libroVentas(this.desde, this.hasta).subscribe({
      next: (libro) => { this.libro = libro; this.cargando = false; },
      error: (err) => { this.error = err?.error?.error ?? 'No se pudo generar el libro de ventas.'; this.cargando = false; },
    });
  }

  exportarExcel(): void {
    this.exportando = true;
    this.reporteService.libroVentasExcel(this.desde, this.hasta).subscribe({
      next: (blob) => { descargarBlob(blob, `libro-ventas-${this.desde}-a-${this.hasta}.xlsx`); this.exportando = false; },
      error: () => { this.error = 'No se pudo exportar el Excel.'; this.exportando = false; },
    });
  }
}
```

`descargarBlob` es un helper simple (crea `<a>` con `URL.createObjectURL`,
click programático, revoke) — no existe todavía en el proyecto, se agrega
como función de utilidad en el mismo archivo del componente (no amerita
un servicio compartido con un solo uso).

### 2.3 Plantilla — estructura visual

Siguiendo `CLAUDE.md` (jerarquía clara, secciones agrupadas, consistencia
con el resto de la app):

1. **Encabezado** — título "Libro de Ventas" + descripción breve.
2. **Filtro de período** (`mat-card`) — dos `<input type="date">`
   (Desde/Hasta) en una fila, botón "Consultar" como acción principal de
   esta sección.
3. **Resumen por tipo de documento** (`mat-card`, solo si `libro &&
   libro.filas.length`) — tabla con columnas Tipo / Cantidad / Neto / IVA
   / Total, una fila por `subtotal` + fila final destacada para
   `totalGeneral`.
4. **Detalle de ventas** (`mat-card`) — tabla con columnas N° / Fecha /
   Tipo documento / RUT / Cliente / Neto / IVA / Total, usando las mismas
   clases `.items-table` ya definidas para tablas en el proyecto
   (`movimientos.component.scss` las declara; se mueven o se reutilizan
   vía estilos compartidos si aplica, o se reimplementan localmente
   siguiendo el mismo patrón visual). Estado vacío: "No hay ventas en el
   período seleccionado."
5. **Acción exportar** — botón "Exportar a Excel" visible junto al
   resumen, deshabilitado si no hay filas o mientras `exportando`.

Estados de carga/error siguen el patrón ya usado en Movimientos
(`page-error`, `page-mensaje`, deshabilitar botones durante la
operación).

---

## 3. Testing

**Backend:**
- `LibroVentasServiceTest`:
  - Ventas de distintos tipos (Factura afecta/exenta, Boleta
    afecta/exenta, Voucher) en el rango se agrupan y subtotalizan
    correctamente; el orden de `subtotales` es siempre
    Factura→Factura Exenta→Boleta→Boleta Exenta→Voucher, omitiendo tipos
    sin ventas.
  - `totalGeneral` suma correctamente todas las filas sin importar tipo.
  - Rango sin ventas devuelve `filas`/`subtotales` vacíos y
    `totalGeneral` en cero (no lanza excepción).
  - Venta con `clienteId` cuyo cliente no existe (o fue desactivado)
    resuelve `clienteNombre = "—"` / `clienteRut = null` sin lanzar
    excepción.
  - `desde` posterior a `hasta` lanza `IllegalArgumentException`.
  - Ventas fuera del rango (antes de `desde` o después de `hasta`) no
    aparecen.
  - Ventas con `activo = false` no aparecen.
- `LibroVentasExcelServiceTest`:
  - El Excel generado se puede releer con POI (`XSSFWorkbook` desde el
    `byte[]`) y sus celdas de detalle coinciden con las filas del
    `LibroVentasResponse` de entrada.
  - Un libro vacío (sin filas) genera un Excel válido y releíble (sin
    excepción), con el bloque de detalle vacío.
- `LibroVentasControllerTest` (o test de integración liviano): rango
  inválido devuelve 400; falta de permiso `VENTAS_VER` devuelve 403.

**Frontend:**
- Test del componente: `consultar()` bloquea cuando `desde > hasta`
  (mensaje de error, no llama al servicio); `exportarExcel()` deshabilita
  el botón mientras está en curso.

No se testea cálculo de montos en el frontend porque todo llega ya
calculado del backend (Global Constraint).
