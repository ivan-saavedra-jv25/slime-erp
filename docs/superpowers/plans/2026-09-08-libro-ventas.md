# Libro de Ventas consolidado Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Un reporte "Libro de Ventas" que junta las ventas de todos los tipos de documento (Factura, Factura Exenta, Boleta, Boleta Exenta, Voucher) en un rango de fechas, con subtotales por tipo, total general, y exportación a Excel.

**Architecture:** Primer módulo de reportería del proyecto (`cl.slimerp.reporteria`), siguiendo el patrón delgado de `VentaController`: sin entidades nuevas, un servicio arma la respuesta a partir de `Venta`+`Cliente` existentes, un segundo servicio la vuelca a un `.xlsx` con Apache POI, y un controlador expone ambos. Frontend agrega un `ReporteService` y una pantalla nueva bajo un grupo de menú "Reportes".

**Tech Stack:** Spring Boot 3.3 / Java 21 / JPA (backend), Angular 18 standalone components + RxJS + Angular Material (frontend), Apache POI 5.2.5 (ya es dependencia del proyecto; esta es la primera vez que el backend *escribe* un `.xlsx`, hasta ahora solo leía).

**Spec:** docs/superpowers/specs/2026-09-08-libro-ventas-design.md

## Global Constraints

- El `id` de toda tabla es autoincremental y lo genera la base de datos — nunca se envía ni genera manualmente desde el frontend ni desde lógica de negocio (regla del proyecto).
- **No hay migración de base de datos en este plan** — no se agrega ningún campo a `Venta` ni `Cliente`.
- Multi-tenant: todo endpoint/query nuevo filtra por `TenantContext.getTenantId()`.
- Errores de negocio se lanzan como `IllegalArgumentException` (400, ya manejado por `GlobalExceptionHandler` — no requiere código nuevo de manejo de errores).
- El endpoint reutiliza el permiso `VENTAS_VER` (`@PreAuthorize("hasAuthority('VENTAS_VER')")`) — no se crea ningún permiso `REPORTES_*` nuevo.
- Los DTOs (records) van **anidados dentro del servicio que los produce** (`LibroVentasService.LibroVentasFila`, etc.), igual que `MovimientoImportService.ImportResultado`/`ItemResuelto`/`FilaError` — no se crean archivos `.java` separados solo para records.
- Formularios/pantallas nuevas usan HTML nativo (`.form-group`, `input`/`select`) con los tokens de diseño existentes; Material solo para botones, tarjetas, tablas e íconos (`frontend/src/styles/_components.scss`). No se introduce ningún `<mat-form-field>`.
- **Ningún comando de git que modifique el repositorio en ninguna tarea** (`git add`, `git commit`, `git push`, etc.) — ni los subagentes que ejecuten este plan deben invocarlos. Los cambios quedan en el árbol de trabajo para que el usuario los revise y comitee manualmente. Ningún paso de este plan incluye un paso de commit. Comandos de git de solo lectura (`git diff`, `git status`, `git log`) sí están permitidos para quien coordine la ejecución (armar paquetes de revisión entre tareas), pero los subagentes implementadores no deben ejecutar ningún comando de git, ni siquiera de lectura.
- Comandos de test verificados en este mismo proyecto (usar tal cual, ejecutar desde la carpeta indicada):
  - Backend (requiere Docker — no hay `mvn` local ni wrapper):
    ```bash
    cd backend
    MSYS_NO_PATHCONV=1 docker run --rm -v "//c/Users/ivana/Documents/slime-erp/backend://app" -v slime-erp-maven-repo:/root/.m2 -w //app maven:3.9-eclipse-temurin-21 mvn -q -B -Dtest=<NombreClaseTest> test
    ```
  - Frontend (usa el Chrome instalado en Windows, no el `chromium-browser` de Linux que asume `karma.conf.js` para CI):
    ```bash
    cd frontend
    CHROME_BIN="C:\Program Files\Google\Chrome\Application\chrome.exe" npx ng test --watch=false --include='**/<archivo>.spec.ts'
    ```

---

### Task 1: Backend — `LibroVentasService`

**Files:**
- Modify: `backend/src/main/java/cl/slimerp/ventas/VentaRepository.java`
- Modify: `backend/src/main/java/cl/slimerp/catalogo/ClienteRepository.java`
- Create: `backend/src/main/java/cl/slimerp/reporteria/LibroVentasService.java`
- Create: `backend/src/test/java/cl/slimerp/reporteria/LibroVentasServiceTest.java`

**Interfaces:**
- Consumes: `Venta` (`cl.slimerp.ventas.Venta`), `TipoDocumentoVenta`, `Cliente` (`cl.slimerp.catalogo.Cliente`) — todos ya existentes, sin cambios.
- Produces:
  - `VentaRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(Long tenantId, LocalDateTime desde, LocalDateTime hasta): List<Venta>`.
  - `ClienteRepository.findByTenantIdAndIdIn(Long tenantId, List<Long> ids): List<Cliente>`.
  - `LibroVentasService.generar(Long tenantId, LocalDate desde, LocalDate hasta): LibroVentasService.LibroVentasResponse` — usado por Task 3 (controller).
  - `LibroVentasService.LibroVentasFila(Long ventaId, LocalDateTime fecha, String tipoDocumento, String clienteRut, String clienteNombre, BigDecimal montoNeto, BigDecimal montoIva, BigDecimal montoTotal)` — usado por Task 2 (Excel).
  - `LibroVentasService.LibroVentasSubtotal(String tipoDocumento, int cantidad, BigDecimal montoNeto, BigDecimal montoIva, BigDecimal montoTotal)` — usado por Task 2.
  - `LibroVentasService.LibroVentasResponse(LocalDate desde, LocalDate hasta, List<LibroVentasFila> filas, List<LibroVentasSubtotal> subtotales, LibroVentasSubtotal totalGeneral)` — usado por Task 2 y Task 3.

- [ ] **Step 1: Agregar el método de rango de fechas a `VentaRepository`**

En `backend/src/main/java/cl/slimerp/ventas/VentaRepository.java`, agregar después de `findByTenantIdAndActivoTrueAndFechaBetween` (sin tocar el método existente, que sigue usándolo el Dashboard):

```java
    // Para el Libro de Ventas: mismo filtro que el del Dashboard pero ordenado,
    // porque el libro se lee de arriba hacia abajo por fecha.
    List<Venta> findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(
            Long tenantId, LocalDateTime desde, LocalDateTime hasta);
```

- [ ] **Step 2: Agregar el método de búsqueda por lista de IDs a `ClienteRepository`**

En `backend/src/main/java/cl/slimerp/catalogo/ClienteRepository.java`, agregar:

```java
    // Resuelve nombre/RUT de varios clientes en una sola consulta (evita N+1
    // al armar el Libro de Ventas).
    List<Cliente> findByTenantIdAndIdIn(Long tenantId, List<Long> ids);
```

- [ ] **Step 3: Escribir el test que falla — `LibroVentasServiceTest`**

Crear `backend/src/test/java/cl/slimerp/reporteria/LibroVentasServiceTest.java`:

```java
package cl.slimerp.reporteria;

import cl.slimerp.catalogo.Cliente;
import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.ventas.TipoDocumentoVenta;
import cl.slimerp.ventas.Venta;
import cl.slimerp.ventas.VentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LibroVentasServiceTest {

    private VentaRepository ventaRepository;
    private ClienteRepository clienteRepository;
    private LibroVentasService service;

    private final Long tenantId = 1L;
    private final LocalDate desde = LocalDate.of(2026, 9, 1);
    private final LocalDate hasta = LocalDate.of(2026, 9, 30);

    private final Cliente clienteUno = Cliente.builder().id(1L).tenantId(1L).nombre("Cliente Uno").rut("11.111.111-1").activo(true).build();
    private final Cliente clienteDos = Cliente.builder().id(2L).tenantId(1L).nombre("Cliente Dos").rut("22.222.222-2").activo(true).build();

    @BeforeEach
    void setUp() {
        ventaRepository = mock(VentaRepository.class);
        clienteRepository = mock(ClienteRepository.class);
        service = new LibroVentasService(ventaRepository, clienteRepository);
    }

    private Venta venta(Long id, Long clienteId, TipoDocumentoVenta tipo, boolean exento,
                         BigDecimal neto, BigDecimal iva, BigDecimal total) {
        return Venta.builder()
                .id(id).tenantId(tenantId).clienteId(clienteId).formaPagoId(1L).bodegaId(1L)
                .tipoDocumento(tipo).exento(exento)
                .fecha(LocalDateTime.of(2026, 9, 10, 12, 0))
                .montoNeto(neto).montoIva(iva).montoTotal(total)
                .build();
    }

    @Test
    void agrupaYSubtotalizaPorTipoDeDocumentoEnElOrdenFacturaBoletaVoucher() {
        List<Venta> ventas = List.of(
                venta(1L, 1L, TipoDocumentoVenta.FACTURA, false, new BigDecimal("1000"), new BigDecimal("190"), new BigDecimal("1190")),
                venta(2L, 1L, TipoDocumentoVenta.FACTURA, true, new BigDecimal("500"), BigDecimal.ZERO, new BigDecimal("500")),
                venta(3L, 2L, TipoDocumentoVenta.BOLETA, false, new BigDecimal("841"), new BigDecimal("159"), new BigDecimal("1000")),
                venta(4L, 2L, TipoDocumentoVenta.BOLETA, true, new BigDecimal("300"), BigDecimal.ZERO, new BigDecimal("300")),
                venta(5L, 1L, TipoDocumentoVenta.VOUCHER, false, new BigDecimal("200"), BigDecimal.ZERO, new BigDecimal("200"))
        );
        when(ventaRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(ventas);
        when(clienteRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(clienteUno, clienteDos));

        var libro = service.generar(tenantId, desde, hasta);

        assertEquals(5, libro.subtotales().stream().mapToInt(LibroVentasService.LibroVentasSubtotal::cantidad).sum());
        assertEquals(List.of("Factura", "Factura Exenta", "Boleta", "Boleta Exenta", "Voucher"),
                libro.subtotales().stream().map(LibroVentasService.LibroVentasSubtotal::tipoDocumento).toList());

        var subtotalFactura = libro.subtotales().get(0);
        assertEquals(1, subtotalFactura.cantidad());
        assertEquals(new BigDecimal("1000"), subtotalFactura.montoNeto());
        assertEquals(new BigDecimal("190"), subtotalFactura.montoIva());
        assertEquals(new BigDecimal("1190"), subtotalFactura.montoTotal());
    }

    @Test
    void losTiposSinVentasEnElPeriodoNoAparecenEnLosSubtotales() {
        List<Venta> ventas = List.of(
                venta(1L, 1L, TipoDocumentoVenta.FACTURA, false, new BigDecimal("1000"), new BigDecimal("190"), new BigDecimal("1190")),
                venta(2L, 1L, TipoDocumentoVenta.VOUCHER, false, new BigDecimal("200"), BigDecimal.ZERO, new BigDecimal("200"))
        );
        when(ventaRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(ventas);
        when(clienteRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(clienteUno));

        var libro = service.generar(tenantId, desde, hasta);

        assertEquals(2, libro.subtotales().size());
        assertEquals(List.of("Factura", "Voucher"),
                libro.subtotales().stream().map(LibroVentasService.LibroVentasSubtotal::tipoDocumento).toList());
    }

    @Test
    void elTotalGeneralSumaTodasLasFilasSinImportarElTipo() {
        List<Venta> ventas = List.of(
                venta(1L, 1L, TipoDocumentoVenta.FACTURA, false, new BigDecimal("1000"), new BigDecimal("190"), new BigDecimal("1190")),
                venta(2L, 2L, TipoDocumentoVenta.BOLETA, false, new BigDecimal("500"), new BigDecimal("95"), new BigDecimal("595"))
        );
        when(ventaRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(ventas);
        when(clienteRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(clienteUno, clienteDos));

        var libro = service.generar(tenantId, desde, hasta);

        assertEquals("Total", libro.totalGeneral().tipoDocumento());
        assertEquals(2, libro.totalGeneral().cantidad());
        assertEquals(new BigDecimal("1500"), libro.totalGeneral().montoNeto());
        assertEquals(new BigDecimal("285"), libro.totalGeneral().montoIva());
        assertEquals(new BigDecimal("1785"), libro.totalGeneral().montoTotal());
    }

    @Test
    void unRangoSinVentasDevuelveListasVaciasYTotalGeneralEnCero() {
        when(ventaRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(List.of());

        var libro = service.generar(tenantId, desde, hasta);

        assertTrue(libro.filas().isEmpty());
        assertTrue(libro.subtotales().isEmpty());
        assertEquals(0, libro.totalGeneral().cantidad());
        assertEquals(BigDecimal.ZERO, libro.totalGeneral().montoNeto());
        verify(clienteRepository, never()).findByTenantIdAndIdIn(any(), any());
    }

    @Test
    void resuelveNombreYRutDelClienteYUsaGuionSiNoExiste() {
        List<Venta> ventas = List.of(
                venta(1L, 1L, TipoDocumentoVenta.FACTURA, false, new BigDecimal("1000"), new BigDecimal("190"), new BigDecimal("1190")),
                venta(2L, 99L, TipoDocumentoVenta.BOLETA, false, new BigDecimal("500"), new BigDecimal("95"), new BigDecimal("595"))
        );
        when(ventaRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(eq(tenantId), any(), any()))
                .thenReturn(ventas);
        // clienteId 99 no existe (desactivado o eliminado): el repo simplemente no lo devuelve.
        when(clienteRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(clienteUno));

        var libro = service.generar(tenantId, desde, hasta);

        var filaClienteUno = libro.filas().stream().filter(f -> f.ventaId().equals(1L)).findFirst().orElseThrow();
        assertEquals("11.111.111-1", filaClienteUno.clienteRut());
        assertEquals("Cliente Uno", filaClienteUno.clienteNombre());

        var filaClienteInexistente = libro.filas().stream().filter(f -> f.ventaId().equals(2L)).findFirst().orElseThrow();
        assertNull(filaClienteInexistente.clienteRut());
        assertEquals("—", filaClienteInexistente.clienteNombre());
    }

    @Test
    void elRangoDeFechasSeConvierteAInicioYFinDelDiaAlConsultarElRepositorio() {
        when(ventaRepository.findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(any(), any(), any()))
                .thenReturn(List.of());

        service.generar(tenantId, desde, hasta);

        verify(ventaRepository).findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(
                tenantId, desde.atStartOfDay(), hasta.atTime(LocalTime.MAX));
    }

    @Test
    void lanzaExcepcionSiDesdeEsPosteriorAHasta() {
        assertThrows(IllegalArgumentException.class, () -> service.generar(tenantId, hasta, desde));
        verifyNoInteractions(ventaRepository);
    }
}
```

- [ ] **Step 4: Ejecutar el test y verificar que falla**

Run:
```bash
cd backend
MSYS_NO_PATHCONV=1 docker run --rm -v "//c/Users/ivana/Documents/slime-erp/backend://app" -v slime-erp-maven-repo:/root/.m2 -w //app maven:3.9-eclipse-temurin-21 mvn -q -B -Dtest=LibroVentasServiceTest test
```
Expected: FALLA — `LibroVentasService` no existe todavía (error de compilación).

- [ ] **Step 5: Implementar `LibroVentasService`**

Crear `backend/src/main/java/cl/slimerp/reporteria/LibroVentasService.java`:

```java
package cl.slimerp.reporteria;

import cl.slimerp.catalogo.Cliente;
import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.ventas.TipoDocumentoVenta;
import cl.slimerp.ventas.Venta;
import cl.slimerp.ventas.VentaRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Arma el Libro de Ventas: junta las ventas activas de un rango de fechas,
// agrupadas y subtotalizadas por tipo de documento (Factura/Factura
// Exenta/Boleta/Boleta Exenta/Voucher). No recalcula montos: los toma tal
// cual los dejó CalculadoraMontosVenta al confirmar cada venta.
@Service
public class LibroVentasService {

    private static final List<String> ORDEN_TIPOS =
            List.of("Factura", "Factura Exenta", "Boleta", "Boleta Exenta", "Voucher");

    private final VentaRepository ventaRepository;
    private final ClienteRepository clienteRepository;

    public LibroVentasService(VentaRepository ventaRepository, ClienteRepository clienteRepository) {
        this.ventaRepository = ventaRepository;
        this.clienteRepository = clienteRepository;
    }

    public record LibroVentasFila(
            Long ventaId,
            LocalDateTime fecha,
            String tipoDocumento,
            String clienteRut,
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

        return new LibroVentasResponse(desde, hasta, filas, agruparSubtotales(filas), totalizar(filas));
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

    // Orden fijo Factura -> Factura Exenta -> Boleta -> Boleta Exenta -> Voucher (no
    // alfabético ni de aparición) para que el libro se lea siempre igual aunque un
    // tipo no tenga ventas en el período.
    static String etiquetaTipoDocumento(TipoDocumentoVenta tipo, boolean exento) {
        return switch (tipo) {
            case FACTURA -> exento ? "Factura Exenta" : "Factura";
            case BOLETA -> exento ? "Boleta Exenta" : "Boleta";
            case VOUCHER -> "Voucher";
        };
    }

    private List<LibroVentasSubtotal> agruparSubtotales(List<LibroVentasFila> filas) {
        Map<String, List<LibroVentasFila>> porTipo = filas.stream()
                .collect(Collectors.groupingBy(LibroVentasFila::tipoDocumento));

        return ORDEN_TIPOS.stream()
                .filter(porTipo::containsKey)
                .map(tipo -> subtotalDe(tipo, porTipo.get(tipo)))
                .toList();
    }

    private LibroVentasSubtotal totalizar(List<LibroVentasFila> filas) {
        return subtotalDe("Total", filas);
    }

    private LibroVentasSubtotal subtotalDe(String etiqueta, List<LibroVentasFila> filas) {
        return new LibroVentasSubtotal(
                etiqueta,
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

- [ ] **Step 6: Ejecutar el test y verificar que pasa**

Run:
```bash
cd backend
MSYS_NO_PATHCONV=1 docker run --rm -v "//c/Users/ivana/Documents/slime-erp/backend://app" -v slime-erp-maven-repo:/root/.m2 -w //app maven:3.9-eclipse-temurin-21 mvn -q -B -Dtest=LibroVentasServiceTest test
```
Expected: PASS, 7 tests.

---

### Task 2: Backend — `LibroVentasExcelService`

**Files:**
- Create: `backend/src/main/java/cl/slimerp/reporteria/LibroVentasExcelService.java`
- Create: `backend/src/test/java/cl/slimerp/reporteria/LibroVentasExcelServiceTest.java`

**Interfaces:**
- Consumes: `LibroVentasService.LibroVentasResponse`/`LibroVentasFila`/`LibroVentasSubtotal` (Task 1).
- Produces: `LibroVentasExcelService.generar(LibroVentasService.LibroVentasResponse libro): byte[]` — usado por Task 3 (controller).

- [ ] **Step 1: Escribir el test que falla — `LibroVentasExcelServiceTest`**

Crear `backend/src/test/java/cl/slimerp/reporteria/LibroVentasExcelServiceTest.java`:

```java
package cl.slimerp.reporteria;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LibroVentasExcelServiceTest {

    private final LibroVentasExcelService service = new LibroVentasExcelService();

    private LibroVentasService.LibroVentasResponse libroDeEjemplo() {
        List<LibroVentasService.LibroVentasFila> filas = List.of(
                new LibroVentasService.LibroVentasFila(1L, LocalDateTime.of(2026, 9, 1, 10, 30), "Factura",
                        "11.111.111-1", "Cliente Uno",
                        new BigDecimal("1000.00"), new BigDecimal("190.00"), new BigDecimal("1190.00")),
                new LibroVentasService.LibroVentasFila(2L, LocalDateTime.of(2026, 9, 2, 12, 0), "Boleta",
                        null, "Cliente Sin Rut",
                        new BigDecimal("500.00"), new BigDecimal("95.00"), new BigDecimal("595.00"))
        );
        List<LibroVentasService.LibroVentasSubtotal> subtotales = List.of(
                new LibroVentasService.LibroVentasSubtotal("Factura", 1, new BigDecimal("1000.00"), new BigDecimal("190.00"), new BigDecimal("1190.00")),
                new LibroVentasService.LibroVentasSubtotal("Boleta", 1, new BigDecimal("500.00"), new BigDecimal("95.00"), new BigDecimal("595.00"))
        );
        LibroVentasService.LibroVentasSubtotal totalGeneral = new LibroVentasService.LibroVentasSubtotal(
                "Total", 2, new BigDecimal("1500.00"), new BigDecimal("285.00"), new BigDecimal("1785.00"));
        return new LibroVentasService.LibroVentasResponse(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), filas, subtotales, totalGeneral);
    }

    @Test
    void generaUnExcelReleiblesConLasFilasYSubtotalesCorrectos() throws IOException {
        byte[] excel = service.generar(libroDeEjemplo());

        assertTrue(excel.length > 0);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            Sheet hoja = workbook.getSheet("Libro de Ventas");
            assertNotNull(hoja);

            // Fila 0: título con el período.
            Row filaTitulo = hoja.getRow(0);
            assertTrue(filaTitulo.getCell(0).getStringCellValue().contains("01-09-2026"));

            // Fila 1: blank. Fila 2: encabezado subtotales. Filas 3-4: subtotales. Fila 5: total.
            Row subtotalFactura = hoja.getRow(3);
            assertEquals("Factura", subtotalFactura.getCell(0).getStringCellValue());
            assertEquals(1190.00, subtotalFactura.getCell(4).getNumericCellValue(), 0.001);

            Row subtotalBoleta = hoja.getRow(4);
            assertEquals("Boleta", subtotalBoleta.getCell(0).getStringCellValue());

            Row filaTotal = hoja.getRow(5);
            assertEquals("Total", filaTotal.getCell(0).getStringCellValue());
            assertEquals(1785.00, filaTotal.getCell(4).getNumericCellValue(), 0.001);

            // Fila 6: blank. Fila 7: encabezado detalle. Filas 8+: detalle.
            Row encabezadoDetalle = hoja.getRow(7);
            assertEquals("Cliente", encabezadoDetalle.getCell(4).getStringCellValue());

            Row detalle1 = hoja.getRow(8);
            assertEquals(1.0, detalle1.getCell(0).getNumericCellValue(), 0.001);
            assertEquals("Factura", detalle1.getCell(2).getStringCellValue());
            assertEquals("11.111.111-1", detalle1.getCell(3).getStringCellValue());
            assertEquals("Cliente Uno", detalle1.getCell(4).getStringCellValue());
            assertEquals(1190.00, detalle1.getCell(7).getNumericCellValue(), 0.001);

            Row detalle2 = hoja.getRow(9);
            assertEquals("", detalle2.getCell(3).getStringCellValue());
            assertEquals("Cliente Sin Rut", detalle2.getCell(4).getStringCellValue());
        }
    }

    @Test
    void unLibroSinVentasGeneraUnExcelValidoSinFilasDeDetalle() throws IOException {
        LibroVentasService.LibroVentasResponse libroVacio = new LibroVentasService.LibroVentasResponse(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), List.of(), List.of(),
                new LibroVentasService.LibroVentasSubtotal("Total", 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        byte[] excel = service.generar(libroVacio);

        assertTrue(excel.length > 0);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            Sheet hoja = workbook.getSheet("Libro de Ventas");
            assertNotNull(hoja);
            // Fila 0 título, fila 1 blank, fila 2 encabezado subtotales, fila 3 total
            // general (sin subtotales por tipo), fila 4 blank, fila 5 encabezado
            // detalle, sin filas de detalle después.
            Row filaTotal = hoja.getRow(3);
            assertEquals("Total", filaTotal.getCell(0).getStringCellValue());
            assertNull(hoja.getRow(6));
        }
    }
}
```

- [ ] **Step 2: Ejecutar el test y verificar que falla**

Run:
```bash
cd backend
MSYS_NO_PATHCONV=1 docker run --rm -v "//c/Users/ivana/Documents/slime-erp/backend://app" -v slime-erp-maven-repo:/root/.m2 -w //app maven:3.9-eclipse-temurin-21 mvn -q -B -Dtest=LibroVentasExcelServiceTest test
```
Expected: FALLA — `LibroVentasExcelService` no existe todavía (error de compilación).

- [ ] **Step 3: Implementar `LibroVentasExcelService`**

Crear `backend/src/main/java/cl/slimerp/reporteria/LibroVentasExcelService.java`:

```java
package cl.slimerp.reporteria;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

// Vuelca un LibroVentasResponse a un .xlsx: título con el período, subtotales por
// tipo de documento + total general, y el detalle completo de ventas.
@Service
public class LibroVentasExcelService {

    private static final DateTimeFormatter FORMATO_FECHA_CORTA = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final String[] ENCABEZADO_SUBTOTALES = {"Tipo", "Cantidad", "Neto", "IVA", "Total"};
    private static final String[] ENCABEZADO_DETALLE =
            {"N°", "Fecha", "Tipo documento", "RUT", "Cliente", "Neto", "IVA", "Total"};

    public byte[] generar(LibroVentasService.LibroVentasResponse libro) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet hoja = workbook.createSheet("Libro de Ventas");
            CellStyle negrita = estiloNegrita(workbook);

            int fila = 0;
            fila = escribirTitulo(hoja, fila, libro, negrita);
            fila++; // fila en blanco
            fila = escribirEncabezado(hoja, fila, ENCABEZADO_SUBTOTALES, negrita);
            fila = escribirSubtotales(hoja, fila, libro, negrita);
            fila++; // fila en blanco
            fila = escribirEncabezado(hoja, fila, ENCABEZADO_DETALLE, negrita);
            escribirDetalle(hoja, fila, libro);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el Excel del Libro de Ventas", e);
        }
    }

    private int escribirTitulo(Sheet hoja, int fila, LibroVentasService.LibroVentasResponse libro, CellStyle negrita) {
        Row row = hoja.createRow(fila);
        Cell cell = row.createCell(0);
        cell.setCellValue("Libro de Ventas — " + libro.desde().format(FORMATO_FECHA_CORTA)
                + " a " + libro.hasta().format(FORMATO_FECHA_CORTA));
        cell.setCellStyle(negrita);
        return fila + 1;
    }

    private int escribirEncabezado(Sheet hoja, int fila, String[] columnas, CellStyle negrita) {
        Row row = hoja.createRow(fila);
        for (int i = 0; i < columnas.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(columnas[i]);
            cell.setCellStyle(negrita);
        }
        return fila + 1;
    }

    private int escribirSubtotales(Sheet hoja, int fila, LibroVentasService.LibroVentasResponse libro, CellStyle negrita) {
        for (LibroVentasService.LibroVentasSubtotal s : libro.subtotales()) {
            escribirFilaSubtotal(hoja, fila++, s, null);
        }
        escribirFilaSubtotal(hoja, fila++, libro.totalGeneral(), negrita);
        return fila;
    }

    private void escribirFilaSubtotal(Sheet hoja, int fila, LibroVentasService.LibroVentasSubtotal s, CellStyle estilo) {
        Row row = hoja.createRow(fila);
        celda(row, 0, s.tipoDocumento(), estilo);
        celda(row, 1, s.cantidad(), estilo);
        celda(row, 2, s.montoNeto(), estilo);
        celda(row, 3, s.montoIva(), estilo);
        celda(row, 4, s.montoTotal(), estilo);
    }

    private void escribirDetalle(Sheet hoja, int filaInicial, LibroVentasService.LibroVentasResponse libro) {
        int fila = filaInicial;
        for (LibroVentasService.LibroVentasFila f : libro.filas()) {
            Row row = hoja.createRow(fila++);
            row.createCell(0).setCellValue(f.ventaId());
            row.createCell(1).setCellValue(f.fecha().format(FORMATO_FECHA_HORA));
            row.createCell(2).setCellValue(f.tipoDocumento());
            row.createCell(3).setCellValue(f.clienteRut() != null ? f.clienteRut() : "");
            row.createCell(4).setCellValue(f.clienteNombre());
            row.createCell(5).setCellValue(f.montoNeto().doubleValue());
            row.createCell(6).setCellValue(f.montoIva().doubleValue());
            row.createCell(7).setCellValue(f.montoTotal().doubleValue());
        }
    }

    private void celda(Row row, int col, String valor, CellStyle estilo) {
        Cell cell = row.createCell(col);
        cell.setCellValue(valor);
        if (estilo != null) cell.setCellStyle(estilo);
    }

    private void celda(Row row, int col, int valor, CellStyle estilo) {
        Cell cell = row.createCell(col);
        cell.setCellValue(valor);
        if (estilo != null) cell.setCellStyle(estilo);
    }

    private void celda(Row row, int col, BigDecimal valor, CellStyle estilo) {
        Cell cell = row.createCell(col);
        cell.setCellValue(valor.doubleValue());
        if (estilo != null) cell.setCellStyle(estilo);
    }

    private CellStyle estiloNegrita(Workbook workbook) {
        Font fuente = workbook.createFont();
        fuente.setBold(true);
        CellStyle estilo = workbook.createCellStyle();
        estilo.setFont(fuente);
        return estilo;
    }
}
```

- [ ] **Step 4: Ejecutar el test y verificar que pasa**

Run:
```bash
cd backend
MSYS_NO_PATHCONV=1 docker run --rm -v "//c/Users/ivana/Documents/slime-erp/backend://app" -v slime-erp-maven-repo:/root/.m2 -w //app maven:3.9-eclipse-temurin-21 mvn -q -B -Dtest=LibroVentasExcelServiceTest test
```
Expected: PASS, 2 tests.

---

### Task 3: Backend — `LibroVentasController`

**Files:**
- Create: `backend/src/main/java/cl/slimerp/reporteria/LibroVentasController.java`
- Create: `backend/src/test/java/cl/slimerp/reporteria/LibroVentasControllerTest.java`

**Interfaces:**
- Consumes: `LibroVentasService.generar(...)` (Task 1), `LibroVentasExcelService.generar(...)` (Task 2).
- Produces: `GET /api/reportes/libro-ventas?desde=&hasta=` → `LibroVentasService.LibroVentasResponse` (JSON); `GET /api/reportes/libro-ventas/excel?desde=&hasta=` → `.xlsx` — consumidos por Task 4 (frontend `ReporteService`).

- [ ] **Step 1: Escribir el test que falla — `LibroVentasControllerTest`**

Crear `backend/src/test/java/cl/slimerp/reporteria/LibroVentasControllerTest.java`:

```java
package cl.slimerp.reporteria;

import cl.slimerp.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LibroVentasControllerTest {

    private LibroVentasService libroVentasService;
    private LibroVentasExcelService libroVentasExcelService;
    private LibroVentasController controller;

    private final Long tenantId = 1L;
    private final LocalDate desde = LocalDate.of(2026, 9, 1);
    private final LocalDate hasta = LocalDate.of(2026, 9, 30);

    @BeforeEach
    void setUp() {
        libroVentasService = mock(LibroVentasService.class);
        libroVentasExcelService = mock(LibroVentasExcelService.class);
        controller = new LibroVentasController(libroVentasService, libroVentasExcelService);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private LibroVentasService.LibroVentasResponse libroVacio() {
        return new LibroVentasService.LibroVentasResponse(desde, hasta, List.of(), List.of(),
                new LibroVentasService.LibroVentasSubtotal("Total", 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void libroVentasDelegaEnElServicioConElTenantDelContexto() {
        when(libroVentasService.generar(tenantId, desde, hasta)).thenReturn(libroVacio());

        LibroVentasService.LibroVentasResponse respuesta = controller.libroVentas(desde, hasta);

        assertEquals(desde, respuesta.desde());
        verify(libroVentasService).generar(tenantId, desde, hasta);
    }

    @Test
    void libroVentasExcelDevuelveElContentTypeYNombreDeArchivoCorrectos() {
        when(libroVentasService.generar(tenantId, desde, hasta)).thenReturn(libroVacio());
        byte[] excelFalso = new byte[]{1, 2, 3};
        when(libroVentasExcelService.generar(any())).thenReturn(excelFalso);

        ResponseEntity<byte[]> respuesta = controller.libroVentasExcel(desde, hasta);

        assertArrayEquals(excelFalso, respuesta.getBody());
        assertEquals(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                respuesta.getHeaders().getContentType());
        assertTrue(respuesta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
                .contains("libro-ventas-2026-09-01-a-2026-09-30.xlsx"));
    }
}
```

- [ ] **Step 2: Ejecutar el test y verificar que falla**

Run:
```bash
cd backend
MSYS_NO_PATHCONV=1 docker run --rm -v "//c/Users/ivana/Documents/slime-erp/backend://app" -v slime-erp-maven-repo:/root/.m2 -w //app maven:3.9-eclipse-temurin-21 mvn -q -B -Dtest=LibroVentasControllerTest test
```
Expected: FALLA — `LibroVentasController` no existe todavía (error de compilación).

- [ ] **Step 3: Implementar `LibroVentasController`**

Crear `backend/src/main/java/cl/slimerp/reporteria/LibroVentasController.java`:

```java
package cl.slimerp.reporteria;

import cl.slimerp.config.TenantContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reportes")
public class LibroVentasController {

    private final LibroVentasService libroVentasService;
    private final LibroVentasExcelService libroVentasExcelService;

    public LibroVentasController(LibroVentasService libroVentasService, LibroVentasExcelService libroVentasExcelService) {
        this.libroVentasService = libroVentasService;
        this.libroVentasExcelService = libroVentasExcelService;
    }

    @GetMapping("/libro-ventas")
    @PreAuthorize("hasAuthority('VENTAS_VER')")
    public LibroVentasService.LibroVentasResponse libroVentas(
            @RequestParam LocalDate desde,
            @RequestParam LocalDate hasta) {
        return libroVentasService.generar(TenantContext.getTenantId(), desde, hasta);
    }

    @GetMapping("/libro-ventas/excel")
    @PreAuthorize("hasAuthority('VENTAS_VER')")
    public ResponseEntity<byte[]> libroVentasExcel(
            @RequestParam LocalDate desde,
            @RequestParam LocalDate hasta) {
        LibroVentasService.LibroVentasResponse libro = libroVentasService.generar(TenantContext.getTenantId(), desde, hasta);
        byte[] excel = libroVentasExcelService.generar(libro);
        String nombreArchivo = "libro-ventas-" + desde + "-a-" + hasta + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + nombreArchivo)
                .body(excel);
    }
}
```

- [ ] **Step 4: Ejecutar el test y verificar que pasa**

Run:
```bash
cd backend
MSYS_NO_PATHCONV=1 docker run --rm -v "//c/Users/ivana/Documents/slime-erp/backend://app" -v slime-erp-maven-repo:/root/.m2 -w //app maven:3.9-eclipse-temurin-21 mvn -q -B -Dtest=LibroVentasControllerTest test
```
Expected: PASS, 2 tests.

- [ ] **Step 5: Ejecutar toda la suite de backend**

Run:
```bash
cd backend
MSYS_NO_PATHCONV=1 docker run --rm -v "//c/Users/ivana/Documents/slime-erp/backend://app" -v slime-erp-maven-repo:/root/.m2 -w //app maven:3.9-eclipse-temurin-21 mvn -q -B test
```
Expected: BUILD SUCCESS, todos los tests (los existentes más los de este plan) en verde.

---

### Task 4: Frontend — Modelos y `ReporteService`

**Files:**
- Modify: `frontend/src/app/core/models/models.ts`
- Create: `frontend/src/app/core/services/reporte.service.ts`
- Create: `frontend/src/app/core/services/reporte.service.spec.ts`

**Interfaces:**
- Consumes: la forma JSON de `GET /api/reportes/libro-ventas` y `GET /api/reportes/libro-ventas/excel` (Task 3).
- Produces: `LibroVentasFila`, `LibroVentasSubtotal`, `LibroVentasResponse` (interfaces TS), `ReporteService.libroVentas(desde, hasta): Observable<LibroVentasResponse>`, `ReporteService.libroVentasExcel(desde, hasta): Observable<Blob>` — usados por Task 5.

- [ ] **Step 1: Agregar las interfaces a `models.ts`**

En `frontend/src/app/core/models/models.ts`, agregar después de la interfaz `Venta` (línea 208):

```ts

export interface LibroVentasFila {
  ventaId: number;
  fecha: string;
  tipoDocumento: string;
  clienteRut: string | null;
  clienteNombre: string;
  montoNeto: number;
  montoIva: number;
  montoTotal: number;
}

export interface LibroVentasSubtotal {
  tipoDocumento: string;
  cantidad: number;
  montoNeto: number;
  montoIva: number;
  montoTotal: number;
}

export interface LibroVentasResponse {
  desde: string;
  hasta: string;
  filas: LibroVentasFila[];
  subtotales: LibroVentasSubtotal[];
  totalGeneral: LibroVentasSubtotal;
}
```

- [ ] **Step 2: Escribir el test que falla — `reporte.service.spec.ts`**

Crear `frontend/src/app/core/services/reporte.service.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ReporteService } from './reporte.service';
import { environment } from '../../../environments/environment';

describe('ReporteService', () => {
  let service: ReporteService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ReporteService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('libroVentas hace GET a /reportes/libro-ventas con los parámetros de fecha', () => {
    service.libroVentas('2026-09-01', '2026-09-30').subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/reportes/libro-ventas`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('desde')).toBe('2026-09-01');
    expect(req.request.params.get('hasta')).toBe('2026-09-30');
    req.flush({ desde: '2026-09-01', hasta: '2026-09-30', filas: [], subtotales: [], totalGeneral: null });
  });

  it('libroVentasExcel hace GET con responseType blob y los parámetros de fecha', () => {
    service.libroVentasExcel('2026-09-01', '2026-09-30').subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/reportes/libro-ventas/excel`);
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    expect(req.request.params.get('desde')).toBe('2026-09-01');
    req.flush(new Blob());
  });
});
```

- [ ] **Step 3: Ejecutar el test y verificar que falla**

Run:
```bash
cd frontend
CHROME_BIN="C:\Program Files\Google\Chrome\Application\chrome.exe" npx ng test --watch=false --include='**/reporte.service.spec.ts'
```
Expected: FALLA — no se puede resolver el módulo `./reporte.service` (todavía no existe).

- [ ] **Step 4: Implementar `ReporteService`**

Crear `frontend/src/app/core/services/reporte.service.ts`:

```ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LibroVentasResponse } from '../models/models';

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

- [ ] **Step 5: Ejecutar el test y verificar que pasa**

Run:
```bash
cd frontend
CHROME_BIN="C:\Program Files\Google\Chrome\Application\chrome.exe" npx ng test --watch=false --include='**/reporte.service.spec.ts'
```
Expected: PASS, 2 tests.

---

### Task 5: Frontend — `LibroVentasComponent`, ruta y menú

**Files:**
- Create: `frontend/src/app/features/reportes/libro-ventas.component.ts`
- Create: `frontend/src/app/features/reportes/libro-ventas.component.spec.ts`
- Create: `frontend/src/app/features/reportes/libro-ventas.component.html`
- Create: `frontend/src/app/features/reportes/libro-ventas.component.scss`
- Modify: `frontend/src/app/app.routes.ts`
- Modify: `frontend/src/app/layout/layout.component.ts`

**Interfaces:**
- Consumes: `ReporteService.libroVentas(...)`/`libroVentasExcel(...)` (Task 4).
- Produces: pantalla en `/reportes/libro-ventas`, visible en el menú lateral bajo un grupo "Reportes" para usuarios con `VENTAS_VER`.

- [ ] **Step 1: Escribir el test que falla — `libro-ventas.component.spec.ts`**

Crear `frontend/src/app/features/reportes/libro-ventas.component.spec.ts`:

```ts
import { of } from 'rxjs';
import { LibroVentasComponent } from './libro-ventas.component';
import { ReporteService } from '../../core/services/reporte.service';
import { LibroVentasResponse } from '../../core/models/models';

describe('LibroVentasComponent', () => {
  function crear() {
    const libroVacio: LibroVentasResponse = {
      desde: '2026-09-01',
      hasta: '2026-09-30',
      filas: [],
      subtotales: [],
      totalGeneral: { tipoDocumento: 'Total', cantidad: 0, montoNeto: 0, montoIva: 0, montoTotal: 0 },
    };
    const reporteServiceStub = {
      libroVentas: jasmine.createSpy('libroVentas').and.returnValue(of(libroVacio)),
      libroVentasExcel: jasmine.createSpy('libroVentasExcel'),
    } as unknown as ReporteService;
    return { component: new LibroVentasComponent(reporteServiceStub), reporteServiceStub };
  }

  it('no consulta si "desde" es posterior a "hasta"', () => {
    const { component, reporteServiceStub } = crear();
    component.desde = '2026-09-30';
    component.hasta = '2026-09-01';

    component.consultar();

    expect(reporteServiceStub.libroVentas).not.toHaveBeenCalled();
    expect(component.error).toContain('no puede ser posterior');
  });

  it('consulta el libro cuando el rango es válido', () => {
    const { component, reporteServiceStub } = crear();
    component.desde = '2026-09-01';
    component.hasta = '2026-09-30';

    component.consultar();

    expect(reporteServiceStub.libroVentas).toHaveBeenCalledWith('2026-09-01', '2026-09-30');
    expect(component.libro).not.toBeNull();
    expect(component.error).toBe('');
  });
});
```

- [ ] **Step 2: Ejecutar el test y verificar que falla**

Run:
```bash
cd frontend
CHROME_BIN="C:\Program Files\Google\Chrome\Application\chrome.exe" npx ng test --watch=false --include='**/libro-ventas.component.spec.ts'
```
Expected: FALLA — no se puede resolver el módulo `./libro-ventas.component` (todavía no existe).

- [ ] **Step 3: Implementar `LibroVentasComponent` (lógica)**

Crear `frontend/src/app/features/reportes/libro-ventas.component.ts`:

```ts
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { LibroVentasResponse } from '../../core/models/models';
import { ReporteService } from '../../core/services/reporte.service';
import { MonedaPipe } from '../../core/pipes/moneda.pipe';

function formatoFecha(fecha: Date): string {
  const anio = fecha.getFullYear();
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const dia = String(fecha.getDate()).padStart(2, '0');
  return `${anio}-${mes}-${dia}`;
}

function primerDiaDelMes(): string {
  const hoy = new Date();
  return formatoFecha(new Date(hoy.getFullYear(), hoy.getMonth(), 1));
}

function descargarBlob(blob: Blob, nombreArchivo: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = nombreArchivo;
  anchor.click();
  URL.revokeObjectURL(url);
}

@Component({
  selector: 'app-libro-ventas',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatIconModule, MatCardModule, MonedaPipe],
  templateUrl: './libro-ventas.component.html',
  styleUrl: './libro-ventas.component.scss',
})
export class LibroVentasComponent implements OnInit {
  desde = primerDiaDelMes();
  hasta = formatoFecha(new Date());
  libro: LibroVentasResponse | null = null;
  cargando = false;
  exportando = false;
  error = '';

  constructor(private reporteService: ReporteService) {}

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
      next: (libro) => {
        this.libro = libro;
        this.cargando = false;
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'No se pudo generar el libro de ventas.';
        this.cargando = false;
      },
    });
  }

  exportarExcel(): void {
    this.exportando = true;
    this.reporteService.libroVentasExcel(this.desde, this.hasta).subscribe({
      next: (blob) => {
        descargarBlob(blob, `libro-ventas-${this.desde}-a-${this.hasta}.xlsx`);
        this.exportando = false;
      },
      error: () => {
        this.error = 'No se pudo exportar el Excel.';
        this.exportando = false;
      },
    });
  }
}
```

- [ ] **Step 4: Ejecutar el test y verificar que pasa**

Run:
```bash
cd frontend
CHROME_BIN="C:\Program Files\Google\Chrome\Application\chrome.exe" npx ng test --watch=false --include='**/libro-ventas.component.spec.ts'
```
Expected: PASS, 2 tests.

- [ ] **Step 5: Crear la plantilla**

Crear `frontend/src/app/features/reportes/libro-ventas.component.html`:

```html
<div class="page-header">
  <h1>Libro de Ventas</h1>
</div>

@if (error) {
  <p class="page-error">{{ error }}</p>
}

<mat-card class="form-panel">
  <h2>Período</h2>
  <div class="form-grid">
    <div class="form-group">
      <label for="desde">Desde<span class="required-mark">*</span></label>
      <input id="desde" type="date" [(ngModel)]="desde" name="desde" />
    </div>
    <div class="form-group">
      <label for="hasta">Hasta<span class="required-mark">*</span></label>
      <input id="hasta" type="date" [(ngModel)]="hasta" name="hasta" />
    </div>
  </div>
  <div class="form-actions form-actions--start">
    <button type="button" mat-flat-button color="primary" [disabled]="cargando" (click)="consultar()">
      <mat-icon>search</mat-icon>
      {{ cargando ? 'Consultando...' : 'Consultar' }}
    </button>
  </div>
</mat-card>

@if (libro && libro.filas.length) {
  <mat-card class="form-panel">
    <h2>Resumen por tipo de documento</h2>
    <table class="libro-table">
      <thead>
        <tr>
          <th>Tipo</th>
          <th class="right">Cantidad</th>
          <th class="right">Neto</th>
          <th class="right">IVA</th>
          <th class="right">Total</th>
        </tr>
      </thead>
      <tbody>
        @for (s of libro.subtotales; track s.tipoDocumento) {
          <tr>
            <td>{{ s.tipoDocumento }}</td>
            <td class="right">{{ s.cantidad }}</td>
            <td class="right">{{ s.montoNeto | moneda }}</td>
            <td class="right">{{ s.montoIva | moneda }}</td>
            <td class="right">{{ s.montoTotal | moneda }}</td>
          </tr>
        }
        <tr class="libro-table__total">
          <td>Total</td>
          <td class="right">{{ libro.totalGeneral.cantidad }}</td>
          <td class="right">{{ libro.totalGeneral.montoNeto | moneda }}</td>
          <td class="right">{{ libro.totalGeneral.montoIva | moneda }}</td>
          <td class="right">{{ libro.totalGeneral.montoTotal | moneda }}</td>
        </tr>
      </tbody>
    </table>
  </mat-card>
}

<mat-card class="form-panel">
  <div class="page-header">
    <h2>Detalle de ventas</h2>
    <button
      type="button"
      mat-stroked-button
      [disabled]="!libro || !libro.filas.length || exportando"
      (click)="exportarExcel()"
    >
      <mat-icon>download</mat-icon>
      {{ exportando ? 'Exportando...' : 'Exportar a Excel' }}
    </button>
  </div>

  @if (cargando) {
    <p class="empty-state">Consultando ventas...</p>
  }
  @if (!cargando && libro && !libro.filas.length) {
    <p class="empty-state">No hay ventas en el período seleccionado.</p>
  }
  @if (!cargando && libro && libro.filas.length) {
    <table class="libro-table">
      <thead>
        <tr>
          <th>N°</th>
          <th>Fecha</th>
          <th>Tipo documento</th>
          <th>RUT</th>
          <th>Cliente</th>
          <th class="right">Neto</th>
          <th class="right">IVA</th>
          <th class="right">Total</th>
        </tr>
      </thead>
      <tbody>
        @for (f of libro.filas; track f.ventaId) {
          <tr>
            <td>{{ f.ventaId }}</td>
            <td>{{ f.fecha | date: 'short' }}</td>
            <td>{{ f.tipoDocumento }}</td>
            <td>{{ f.clienteRut || '—' }}</td>
            <td>{{ f.clienteNombre }}</td>
            <td class="right">{{ f.montoNeto | moneda }}</td>
            <td class="right">{{ f.montoIva | moneda }}</td>
            <td class="right">{{ f.montoTotal | moneda }}</td>
          </tr>
        }
      </tbody>
    </table>
  }
</mat-card>
```

- [ ] **Step 6: Crear los estilos**

Crear `frontend/src/app/features/reportes/libro-ventas.component.scss`:

```scss
.libro-table {
  width: 100%;
  border-collapse: collapse;
}

.libro-table th,
.libro-table td {
  text-align: left;
  padding: var(--space-2) var(--space-3);
  border-bottom: var(--border-width-default) solid var(--border-default);
  font: var(--font-body-sm);
}

.libro-table th {
  color: var(--text-muted);
  font-weight: 600;
}

.libro-table .right {
  text-align: right;
}

.libro-table__total td {
  font-weight: 700;
  background: var(--surface-subtle);
}
```

- [ ] **Step 7: Agregar la ruta**

En `frontend/src/app/app.routes.ts`, agregar dentro de `children`, después del bloque `admin/empresas` (antes del `],` de cierre):

```ts
      {
        path: 'reportes/libro-ventas',
        loadComponent: () =>
          import('./features/reportes/libro-ventas.component').then((m) => m.LibroVentasComponent),
      },
```

- [ ] **Step 8: Agregar el grupo de menú**

En `frontend/src/app/layout/layout.component.ts`, agregar un nuevo grupo entre `contactos` y `administracion`:

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

- [ ] **Step 9: Ejecutar toda la suite de frontend**

Run:
```bash
cd frontend
CHROME_BIN="C:\Program Files\Google\Chrome\Application\chrome.exe" npx ng test --watch=false
```
Expected: PASS, todos los tests (los existentes más los de este plan, incluyendo `layout.component.spec.ts` que sigue en verde con el grupo nuevo).

---

### Task 6: Verificación manual end-to-end

**Files:** ninguno (solo verificación en la app real).

**Interfaces:**
- Consumes: todo lo anterior (Tasks 1–5), corriendo junto vía `docker compose`.
- Produces: confirmación de que el flujo completo funciona con Postgres real y el navegador — el "test de integración" de facto de este proyecto (no hay `@SpringBootTest` en `backend/src/test`; los flujos de Ventas/Movimientos/Tesorería anteriores en este mismo proyecto se verificaron igual).

- [ ] **Step 1: Reconstruir y levantar backend y frontend**

Run:
```bash
docker compose build backend frontend
docker compose up -d backend frontend
```
Expected: ambos contenedores `Started`/`Running`. No hay migración nueva que aplicar en este plan.

- [ ] **Step 2: Verificar en el navegador**

1. Iniciar sesión en la app.
2. Si no hay ventas registradas en el mes actual, crear al menos una venta de tipo Factura y una de tipo Boleta desde `/ventas` (para tener datos que mostrar en el libro).
3. Ir a **Reportes > Libro de Ventas** (grupo nuevo en el menú lateral) — confirmar que el rango de fechas viene precargado con el mes actual y que la consulta se dispara automáticamente al entrar.
4. Confirmar que la tabla "Resumen por tipo de documento" muestra un subtotal por cada tipo con ventas y una fila "Total" al final.
5. Confirmar que la tabla "Detalle de ventas" lista cada venta con Fecha/Tipo/RUT/Cliente/Neto/IVA/Total correctos.
6. Cambiar el rango de fechas a un período sin ventas y confirmar el mensaje "No hay ventas en el período seleccionado." (sin error).
7. Volver al rango con datos y hacer clic en "Exportar a Excel" — confirmar que el navegador descarga un archivo `libro-ventas-<desde>-a-<hasta>.xlsx` sin error en consola.
8. Abrir el archivo descargado (o revisarlo con cualquier lector de Excel) y confirmar que el título, los subtotales y el detalle coinciden con lo mostrado en pantalla.

Expected: los 8 puntos se cumplen sin errores de consola ni de red (revisar con las herramientas de desarrollador del navegador si algo falla).
