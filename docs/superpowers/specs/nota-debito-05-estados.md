# ND-006 — Estados y eventos

## Objetivo

Ciclo de vida `BORRADOR / EMITIDA / ANULADA` con guardias de transición y un historial
append-only en `nota_debito_evento`.

## Estados (`EstadoNotaDebito`)

| Estado | Significado |
|---|---|
| `BORRADOR` | Editable. El folio ya está asignado (no se reutiliza al borrar). |
| `EMITIDA` | Aplica los efectos sobre la NC, montos e inventario. Bloquea modificaciones. |
| `ANULADA` | Terminal. Revierte los efectos propios de la ND (reversa de la reversión). |

## Transiciones permitidas

| Desde | A | Guardia |
|---|---|---|
| BORRADOR | EMITIDA | `emitir` — valida documento asociado, cantidades y montos; aplica inventario |
| EMITIDA | ANULADA | `anular(motivo)` — revierte la reversión de inventario; motivo es requerido |
| BORRADOR | BORRADOR | `actualizar` / `eliminar` (delete solo en BORRADOR) |
| — | BORRADOR | `crear` |
| ANULADA / EMITIDA | cualquier otra | **rechazada** con `IllegalArgumentException` claro |

Mensajes al estilo de la NC: `"No se puede emitir una nota de débito en estado X"`.

## Historial `nota_debito_evento` (append-only)

Se escribe exclusivamente vía el helper privado `registrarEvento(...)` del service. Acciones
(`AccionNotaDebito`):

| Acción | estadoAnterior → estadoNuevo | detalle |
|---|---|---|
| `CREADA` | null → BORRADOR | `"Reviera {tipo} sobre NC-000012"` |
| `EDITADA` | BORRADOR → BORRADOR | cambio de tipo de reversión si aplica |
| `EMITIDA` | BORRADOR → EMITIDA | `"N línea(s), M con reversión de inventario"` |
| `ANULADA` | EMITIDA → ANULADA | el motivo |

No se borra ni modifica. El `usuario_id` puede ser `null` (procesos automáticos).

## Business checks

- Emitir exige `BORRADOR`.
- Anular exige `EMITIDA` y es la **única** operación posible tras emitir.
- Si la ND fue emitida, `actualizar` y `eliminar` devuelven error (regla "una ND emitida
  no puede modificarse").

## Validación

- Unit test de transiciones válidas e inválidas, y de que el historial queda en orden
  cronológico (`OrderByFechaAscIdAsc`).