# NC-007 — Productos, inventario y trazabilidad

## Objetivo

Cuando la Nota de Crédito implica devolución o recuperación de productos, al emitirla debe
generarse el movimiento de entrada correspondiente, quedar registrada la relación
NC ↔ movimiento, y ser imposible recuperar dos veces la misma cantidad.

## Cómo funciona el inventario en este ERP

- **`Producto` no tiene campo de stock.** El stock vive solo en `StockProductoBodega`
  (`tenant_id`, `producto_id`, `bodega_id`, `cantidad`).
- Único punto de escritura:
  `StockService.sumar(tenantId, productoId, bodegaId, cantidad, tipo, headerId, referenciaId)`.
  La **dirección es el signo de `cantidad`**; la línea de `movimiento_inventario` se graba
  siempre con `cantidad.abs()` y el sentido queda implícito en `tipo`.
- `VentaService.crear()` descuenta con
  `stockService.sumar(..., item.cantidad().negate(), SALIDA_VENTA, null, venta.getId())`.
  La Nota de Crédito es la operación inversa.

## Cambios en el módulo de inventario

### `TipoMovimiento` (`inventario/TipoMovimiento.java`)

```java
ENTRADA_NOTA_CREDITO,        // recuperación al emitir la NC
SALIDA_ANULA_NOTA_CREDITO    // reversa al anular la NC
```

`SALIDA_ANULA_NOTA_CREDITO` tiene 25 caracteres y la columna es `VARCHAR(20)`: la
migración `V37` la ensancha a `VARCHAR(30)` (ver `01-modelo-datos.md`).

### `StockService.sumar(...)` devuelve el movimiento

Cambia de `void` a `MovimientoInventario` para poder guardar el vínculo:

```java
@Transactional
public MovimientoInventario sumar(Long tenantId, Long productoId, Long bodegaId,
                                  BigDecimal cantidad, TipoMovimiento tipo,
                                  Long headerId, Long referenciaId) {
    ...
    return movimientoRepository.save(MovimientoInventario.builder()...build());
}
```

Cambio no rompiente: `VentaService`, `CompraService`, `MovimientoInventarioService` y
`StockController` ignoran el retorno y no requieren modificación.

## Bodega

La mercadería vuelve a **la bodega del documento original** (`venta.bodega_id`), guardada
como `nota_credito.bodega_id` al crear la NC. No se pide al usuario.

Si la venta no tuviera bodega, caer en `stockService.bodegaPrincipal(tenantId)`, igual que
hacen `VentaService` y `CompraService`.

## Cantidad disponible a recuperar

Por cada línea del documento original:

```
disponible(ventaDetalle) = ventaDetalle.cantidad − yaRecuperado(ventaDetalle)
```

donde `yaRecuperado` es la suma de `nota_credito_detalle.cantidad` de las NC de esa misma
venta que cumplen **todas** estas condiciones:

- `nota_credito.estado = 'EMITIDA'` (un BORRADOR no reserva nada; una ANULADA ya liberó)
- `nota_credito_detalle.recupera_inventario = true`
- mismo `venta_detalle_id`

Consulta en `NotaCreditoDetalleRepository`:

```java
@Query("""
       SELECT d.ventaDetalleId, COALESCE(SUM(d.cantidad), 0)
       FROM NotaCreditoDetalle d
       JOIN NotaCredito nc ON nc.id = d.notaCreditoId
       WHERE nc.tenantId = :tenantId
         AND nc.ventaId = :ventaId
         AND nc.estado = cl.slimerp.notascredito.EstadoNotaCredito.EMITIDA
         AND d.recuperaInventario = true
         AND (:excluyendoId IS NULL OR nc.id <> :excluyendoId)
         AND d.ventaDetalleId IS NOT NULL
       GROUP BY d.ventaDetalleId
       """)
List<Object[]> cantidadesRecuperadasPorVenta(@Param("tenantId") Long tenantId,
                                             @Param("ventaId") Long ventaId,
                                             @Param("excluyendoId") Long excluyendoId);
```

> Si el driver de Postgres se queja del parámetro que solo se compara contra `IS NULL`
> (problema ya documentado en `NotaVentaService`), separar en dos métodos: uno con
> `excluyendo` y otro sin él.

El resultado se mapea a `Map<Long, BigDecimal>` (ventaDetalleId → cantidad recuperada).

## Emitir — `@Transactional`

1. `exigirEstado(nc, "emitir", BORRADOR)`.
2. Validar según el tipo de corrección (ver `04-tipo-correccion.md`).
3. Si el tipo es CORRIGE_TEXTO, no hay paso de inventario: saltar a 6.
4. Cargar `cantidadesRecuperadasPorVenta(tenantId, nc.ventaId, nc.id)` **una sola vez** y,
   por cada línea con `recuperaInventario = true`:

   ```
   disponible = ventaDetalle.cantidad − yaRecuperado.getOrDefault(ventaDetalleId, 0)
   si linea.cantidad > disponible → IllegalArgumentException
   ```

   Mensaje, en el estilo de `VentaService.validarStock`:

   > `No se puede recuperar 5 de "Bidón 20L": el documento asociado solo tiene 2 disponibles para recuperar.`

5. Por cada línea válida (todas ya validadas antes de escribir nada):

   ```java
   MovimientoInventario mov = stockService.sumar(
           tenantId, linea.getProductoId(), nc.getBodegaId(), linea.getCantidad(),
           TipoMovimiento.ENTRADA_NOTA_CREDITO, headerId, nc.getId());

   movimientoRepository.save(NotaCreditoMovimiento.builder()
           .tenantId(tenantId)
           .notaCreditoId(nc.getId())
           .notaCreditoDetalleId(linea.getId())
           .movimientoInventarioId(mov.getId())
           .tipo(TipoMovimientoNotaCredito.RECUPERACION)
           .build());
   ```

6. `estado = EMITIDA`, `fechaEmision = now()`, evento `EMITIDA`.

Validar **todas** las líneas antes de escribir la primera: así un fallo en la línea 3 no
deja las líneas 1 y 2 aplicadas (aunque la transacción haga rollback, el mensaje de error
es más claro y el comportamiento más predecible).

Validación y escritura ocurren en la misma transacción, de modo que la doble recuperación
queda cerrada dentro del módulo.

## Anular — `@Transactional`

1. `exigirEstado(nc, "anular", EMITIDA)`.
2. Por cada `NotaCreditoMovimiento` de la NC con `tipo = RECUPERACION`:
   - leer el `MovimientoInventario` original para saber producto, bodega y cantidad;
   - `mov = stockService.sumar(tenantId, productoId, bodegaId, cantidad.negate(),
     TipoMovimiento.SALIDA_ANULA_NOTA_CREDITO, headerId, nc.getId())`;
   - guardar un `NotaCreditoMovimiento` nuevo con `tipo = REVERSA_ANULACION` apuntando al
     movimiento recién creado.
3. `estado = ANULADA`, `fechaAnulacion = now()`, evento `ANULADA` con el motivo.

Al quedar ANULADA, sus cantidades dejan de contar como "ya recuperado" (la consulta filtra
por `estado = EMITIDA`), así que vuelven a estar disponibles para otra NC.

Las filas de `RECUPERACION` **no se borran**: el historial de inventario es append-only,
igual que el de eventos.

## Consultar movimientos relacionados

Expuesto en el detalle de la NC:

```java
public record MovimientoRelacionado(
        Long movimientoInventarioId, Long headerId, TipoMovimientoNotaCredito tipo, LocalDateTime fecha,
        Long productoId, String producto, BigDecimal cantidad,
        Long bodegaId, String bodega) { }
```

Se arma uniendo `nota_credito_movimiento` con `movimiento_inventario`, resolviendo nombres
de producto y bodega en consultas aparte (evitar N+1).

## Validación de la tarea

Tests en `NotaCreditoServiceTest` (Mockito puro):

- Emitir genera exactamente un `stockService.sumar(...)` por línea con
  `recuperaInventario = true`, con signo **positivo** y `ENTRADA_NOTA_CREDITO`.
- Las líneas con `recuperaInventario = false` no generan movimiento.
- CORRIGE_TEXTO no invoca `stockService` en absoluto.
- Emitir una segunda NC que excede lo disponible lanza `IllegalArgumentException` con el
  nombre del producto en el mensaje.
- Anular invoca `sumar` con cantidad **negativa** y `SALIDA_ANULA_NOTA_CREDITO` una vez por
  cada movimiento de recuperación.
- Tras anular, `cantidadesRecuperadasPorVenta` ya no cuenta esa NC.

Manual (ver el plan): crear venta, emitir NC parcial, verificar el stock y el movimiento en
el módulo de Inventario, intentar exceder lo disponible, anular y comprobar que el stock
vuelve al valor inicial.

## Cabecera de movimiento (V38)

Los movimientos de la nota de crédito cuelgan de un `movimiento_inventario_header`,
creado por el servicio al emitir y al anular:

| Momento | `tipo` | Bodega | Observación |
|---|---|---|---|
| Emitir | `ENTRADA` | `bodega_destino_id` = bodega de la NC | `Recuperación de inventario por NC-000012` |
| Anular | `SALIDA` | `bodega_origen_id` = bodega de la NC | `Anulación de NC-000012` |

Sin cabecera, el movimiento queda fuera del historial del módulo de Inventario y sin
acceso a su pantalla de detalle ni a sus exportaciones a PDF y Excel, que se sirven por
cabecera. Con ella, la recuperación se consulta con las pantallas que ya existen en vez de
duplicarlas.

`MovimientoRelacionado` expone `headerId` para que la trazabilidad pueda abrir esa
pantalla. `V38__nota_credito_movimiento_header.sql` crea las cabeceras que faltaban en las
notas emitidas antes de este cambio.
