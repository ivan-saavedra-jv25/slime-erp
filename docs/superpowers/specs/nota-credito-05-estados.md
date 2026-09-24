# NC-006 — Estados y eventos

## Objetivo

Ciclo de vida de la Nota de Crédito con tres estados, y un historial append-only de cada
cambio.

## Enum

```java
public enum EstadoNotaCredito {
    BORRADOR,
    EMITIDA,
    ANULADA
}
```

| Estado | Significado |
|---|---|
| **BORRADOR** | Permite modificar la información. No tiene efectos sobre inventario. |
| **EMITIDA** | Bloquea las modificaciones y aplica los efectos sobre la operación e inventario. |
| **ANULADA** | La NC queda marcada como anulada y sus efectos sobre inventario quedan revertidos. Terminal. |

## Transiciones

Sin máquina de estados declarativa. Un guardia varargs, igual que `NotaVentaService`:

```java
private void exigirEstado(NotaCredito nc, String accion, EstadoNotaCredito... permitidos) {
    for (EstadoNotaCredito permitido : permitidos) {
        if (nc.getEstado() == permitido) return;
    }
    throw new IllegalArgumentException(
        "No se puede " + accion + " una nota de crédito en estado " + nc.getEstado());
}
```

| Operación | Estados permitidos | Estado resultante | Efecto adicional |
|---|---|---|---|
| `crear` | — | BORRADOR | asigna folio |
| `actualizar` | BORRADOR | BORRADOR | `fechaActualizacion = now()` |
| `eliminar` | BORRADOR | (delete físico) | el folio queda como hueco |
| `emitir` | BORRADOR | EMITIDA | genera los movimientos de inventario; `fechaEmision = now()` |
| `anular` | EMITIDA | ANULADA | revierte los movimientos; `fechaAnulacion = now()` |

Consecuencias que deben quedar cubiertas por guardias:

- Editar o eliminar una NC EMITIDA o ANULADA falla.
- Emitir dos veces falla.
- Anular un BORRADOR falla (se elimina, no se anula).
- Anular una NC ya ANULADA falla.

## Historial — `nota_credito_evento`

Append-only. Nunca se actualiza ni se borra una fila de eventos.

```java
public enum AccionNotaCredito {
    CREADA, EDITADA, EMITIDA, ANULADA
}
```

```java
private void registrarEvento(NotaCredito nc, AccionNotaCredito accion,
                             EstadoNotaCredito anterior, EstadoNotaCredito nuevo,
                             String detalle, Long usuarioId) {
    eventoRepository.save(NotaCreditoEvento.builder()
            .tenantId(nc.getTenantId())
            .notaCreditoId(nc.getId())
            .usuarioId(usuarioId)
            .accion(accion)
            .estadoAnterior(anterior)
            .estadoNuevo(nuevo)
            .detalle(detalle)
            .build());
}
```

Qué se registra en `detalle`:

| Acción | `detalle` |
|---|---|
| CREADA | tipo de corrección y documento asociado, p. ej. `"Corrige monto sobre FACTURA N.º 1042"` |
| EDITADA | null, o el cambio de tipo de corrección si lo hubo |
| EMITIDA | resumen de la recuperación, p. ej. `"3 líneas, 2 con recuperación de inventario"` |
| ANULADA | el motivo indicado por el usuario |

`usuarioId` se resuelve con `UsuarioActualService.idUsuarioActual(tenantId)`. Es nullable
en la tabla porque un proceso automático podría no actuar en nombre de un usuario.

El historial se expone en el detalle como `List<EventoNotaCredito>` ordenado
ascendentemente por fecha, con el nombre del usuario ya resuelto.

## Bloqueo de modificaciones

`actualizar(id, request)` empieza con `exigirEstado(nc, "editar", BORRADOR)`. No hay
edición parcial de una NC emitida: si el documento está mal, se anula y se emite otra.

## Multi-tenant

Todo método del service empieza resolviendo `TenantContext.getTenantId()` y **todas** las
consultas filtran por `tenantId` (`findByIdAndTenantId`, `findByTenantIdAnd…`). Una NC de
otro tenant debe comportarse como inexistente.

## Validación de la tarea

Tests en `NotaCreditoServiceTest`:

- `crear` deja la NC en BORRADOR y escribe un evento `CREADA`.
- `actualizar` sobre una NC EMITIDA lanza `IllegalArgumentException` con un mensaje que
  nombra el estado.
- `emitir` sobre una NC ya EMITIDA falla.
- `anular` sobre un BORRADOR falla.
- `eliminar` sobre una NC EMITIDA falla.
- Cada transición exitosa agrega exactamente un evento con `estadoAnterior` y
  `estadoNuevo` correctos.
- Una NC de otro tenant no se encuentra.
