# NC-008 — Acciones, listado y dashboard (API)

## Objetivo

Exponer todas las acciones del módulo y el listado con filtros, paginación y ordenamiento.

## Acciones requeridas

| Acción | Endpoint |
|---|---|
| Crear Nota de Crédito | `POST /api/notas-credito` |
| Guardar borrador | el mismo `POST` (nace en BORRADOR) |
| Editar borrador | `PUT /api/notas-credito/{id}` |
| Emitir Nota de Crédito | `POST /api/notas-credito/{id}/emitir` |
| Anular Nota de Crédito | `POST /api/notas-credito/{id}/anular` |
| Ver detalle | `GET /api/notas-credito/{id}` |
| Consultar documento asociado | incluido en el detalle + `GET /documentos-asociables` |
| Consultar movimientos de inventario relacionados | incluido en el detalle |
| Eliminar borrador | `DELETE /api/notas-credito/{id}` |

## `NotaCreditoController` — `/api/notas-credito`

Controller delgado: sin lógica, devuelve directamente los records del service.
`@PreAuthorize` en **cada** método.

| Verbo | Ruta | Request | Response | Permiso |
|---|---|---|---|---|
| GET | `/` | query params | `PaginaResponse<NotaCreditoResumen>` | VER |
| GET | `/dashboard` | `desde`, `hasta` | `DashboardNotasCredito` | VER |
| GET | `/{id}` | — | `NotaCreditoCompleta` | VER |
| GET | `/documentos-asociables` | `clienteId`, `q` | `List<DocumentoAsociable>` | VER |
| GET | `/ventas/{ventaId}/lineas` | `excluyendoNotaCreditoId` | `List<LineaDocumentoOriginal>` | VER |
| POST | `/` | `@Valid NotaCreditoRequest` | `NotaCreditoCompleta` | EDITAR |
| PUT | `/{id}` | `@Valid NotaCreditoRequest` | `NotaCreditoCompleta` | EDITAR |
| DELETE | `/{id}` | — | `204 No Content` | EDITAR |
| POST | `/{id}/emitir` | — | `NotaCreditoCompleta` | EDITAR |
| POST | `/{id}/anular` | `MotivoRequest` (opcional) | `NotaCreditoCompleta` | EDITAR |
| GET | `/{id}/pdf` | — | `byte[]` `application/pdf` inline | VER |

## DTOs

### Request — records de nivel superior en el paquete

```java
public record NotaCreditoRequest(
        @NotNull Long ventaId,
        @NotNull TipoCorreccion tipoCorreccion,
        @NotNull LocalDate fecha,
        @NotBlank String docAsociadoRazon,
        String motivo,
        String observaciones,
        String textoCorreccion,
        BigDecimal descuento,
        @Valid List<Item> items) {

    public record Item(@NotNull Long productoId,
                       Long ventaDetalleId,
                       @NotNull BigDecimal cantidad,
                       @NotNull BigDecimal precioUnitario,
                       BigDecimal descuento,
                       boolean recuperaInventario) { }
}
```

`items` **no** lleva `@NotEmpty`: CORRIGE_TEXTO va sin líneas. La obligatoriedad según el
tipo se valida en el service (ver `04-tipo-correccion.md`).

`public record MotivoRequest(String motivo) { }`

El `id` nunca viaja en el request de creación: lo genera la base de datos.

### Response — records anidados en `NotaCreditoService`

```java
public record LineaNotaCredito(Long id, Long productoId, Long ventaDetalleId,
        String codigo, String descripcion, BigDecimal cantidad,
        BigDecimal precioUnitario, BigDecimal descuento, BigDecimal subtotal,
        boolean recuperaInventario) { }

public record EventoNotaCredito(LocalDateTime fecha, String usuario, AccionNotaCredito accion,
        EstadoNotaCredito estadoAnterior, EstadoNotaCredito estadoNuevo, String detalle) { }

public record DocumentoAsociado(Long ventaId, TipoDocumentoVenta tipo, Integer folio,
        String numero, LocalDate fecha, String razon, BigDecimal montoTotal) { }

public record NotaCreditoResumen(Long id, Integer folio, String numero,
        EstadoNotaCredito estado, TipoCorreccion tipoCorreccion, LocalDate fecha,
        String clienteNombre, String clienteRut,
        TipoDocumentoVenta docAsociadoTipo, Integer docAsociadoFolio,
        String motivo, BigDecimal montoTotal, boolean recuperaInventario) { }

public record NotaCreditoCompleta(Long id, Integer folio, String numero,
        EstadoNotaCredito estado, TipoCorreccion tipoCorreccion, LocalDate fecha,
        Long clienteId, String clienteNombre, String clienteRut,
        String vendedorNombre, DocumentoAsociado documentoAsociado,
        String motivo, String observaciones, String textoCorreccion,
        Long bodegaId, String bodegaNombre, boolean exenta, String moneda,
        BigDecimal descuento, BigDecimal montoSubtotal, BigDecimal montoDescuento,
        BigDecimal montoNeto, BigDecimal montoIva, BigDecimal montoTotal,
        LocalDateTime fechaEmision, LocalDateTime fechaAnulacion,
        List<LineaNotaCredito> lineas,
        List<MovimientoRelacionado> movimientosInventario,
        List<EventoNotaCredito> historial) { }
```

`recuperaInventario` en el resumen alimenta la columna "Recuperación de inventario" del
listado. Valor: `NO` si ninguna línea recupera, `SI` si todas lo hacen, `PARCIAL` si solo
algunas. Modelarlo como enum `RecuperacionInventario { NO, PARCIAL, TOTAL }` en vez de
`boolean` para que la UI pueda mostrar los tres casos.

## Listado — `GET /api/notas-credito`

Filtros pedidos por la especificación:

| Param | Tipo |
|---|---|
| `desde`, `hasta` | `LocalDate`, `@DateTimeFormat(iso = ISO.DATE)` |
| `clienteId` | `Long` |
| `estado` | `EstadoNotaCredito` |
| `tipoCorreccion` | `TipoCorreccion` |
| `ventaId` | `Long` — documento asociado |
| `docAsociadoTipo` | `TipoDocumentoVenta` — filtrar por tipo de documento corregido |
| `q` | búsqueda libre |
| `sort`, `dir` | ordenamiento |
| `pagina` (0), `tamano` (10) | paginación |

Implementación con **Spring Data `Specification`** compuesta dinámicamente. No usar
`(:param IS NULL OR ...)` en JPQL: el driver de Postgres no logra inferir el tipo de un
parámetro que solo se compara contra `IS NULL` (motivo ya documentado en
`NotaVentaService`).

```java
Specification<NotaCredito> spec = (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
if (estado != null) spec = spec.and((r, q_, cb) -> cb.equal(r.get("estado"), estado));
...
```

Búsqueda libre `q`: resolver ids de cliente en una consulta aparte
(`clienteRepository.idsPorBusqueda`) y, en paralelo, intentar interpretar `q` como folio
(`NC-000012` → `12`, también acepta `12`), uniendo ambas alternativas con `cb.or`
(`cb.disjunction()` si no hay ninguna).

Ordenamiento con whitelist y desempate estable:

```java
private static final Set<String> CAMPOS_ORDEN = Set.of("fecha", "folio", "montoTotal");
return Sort.by(dir, campo).and(Sort.by(dir, "folio"));
```

Paginación con `cl.slimerp.common.PaginaResponse<T>`. Validar `pagina >= 0`, `tamano >= 1`
y `desde <= hasta`.

Cargar el detalle con `@EntityGraph(attributePaths = "detalle")` — `open-in-view: false`.
Resolver nombres de cliente en lote con `clienteRepository.findByTenantIdAndIdIn` para
evitar N+1.

## Dashboard — `GET /api/notas-credito/dashboard?desde&hasta`

```java
public record ConteoPorEstado(EstadoNotaCredito estado, int cantidad, BigDecimal monto) { }

public record DashboardNotasCredito(
        LocalDate desde, LocalDate hasta,
        int cantidad, int borradores, int emitidas, int anuladas,
        BigDecimal montoTotalEmitido,
        int conRecuperacionInventario,
        List<ConteoPorEstado> porEstado,
        List<ConteoPorTipo> porTipoCorreccion) { }
```

`montoTotalEmitido` suma solo las EMITIDAS: los borradores no son documentos y las
anuladas no tienen efecto.

## Validación de la tarea

- `NotaCreditoControllerPermissionTest` — `@WebMvcTest(NotaCreditoController.class)` +
  `@TestConfiguration @EnableMethodSecurity`, `@MockBean` del service, del pdfService, de
  `JwtService` y `PermisoEfectivoService`; `@WithMockUser(authorities = ...)` verificando
  200 vs 403 en un endpoint de lectura y uno de escritura.
- Filtrar por cada uno de los filtros devuelve solo lo esperado.
- Buscar `NC-000012` y `12` encuentran la misma NC.
- `sort=campoInexistente` no rompe: cae al orden por defecto.
- `pagina=-1` o `tamano=0` responden 400.
