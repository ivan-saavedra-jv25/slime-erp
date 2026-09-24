# NC-013 — Frontend: detalle y trazabilidad

## Objetivo

Pantalla de detalle con las acciones de estado, el historial y la trazabilidad completa
**Documento original → Nota de Crédito → Movimiento de Inventario**.

## Archivos

`frontend/src/app/features/notas-credito/nota-credito-detalle.component.ts | .html | .scss | .spec.ts`

## Secciones

### 1. Encabezado
`.page-header` con `NC-000012`, el `tag` de estado, y la barra de acciones:

| Acción | Visible cuando |
|---|---|
| **Editar** | `estado === 'BORRADOR'` y permiso EDITAR |
| **Emitir** | `estado === 'BORRADOR'` y permiso EDITAR — botón primario |
| **Anular** | `estado === 'EMITIDA'` y permiso EDITAR |
| **Eliminar** | `estado === 'BORRADOR'` y permiso EDITAR |
| **PDF** | siempre con permiso VER |
| **Volver** | siempre |

Un solo botón primario a la vez; el resto `mat-stroked-button` o `mat-icon-button`.

**Emitir** pide confirmación explicando el efecto, porque no es reversible sin anular:

> Al emitir la nota de crédito se bloquean las modificaciones y se generan las entradas de
> inventario de las líneas marcadas. ¿Continuar?

**Anular** usa `ConfirmActionDialog` de `core/components/confirm-action-dialog`, que
devuelve el motivo por `dialogRef.close()`; ese motivo va a `anular(id, motivo)`.

**PDF**: abrir `VentaPdfDialogComponent` (`features/ventas/venta-pdf-dialog.component.ts`)
con `{ titulo, url }`, reutilizado tal como lo hace notas de venta.

Errores de acción: `err?.error?.error ?? '<mensaje por defecto>'` en `<p class="page-error">`.
Overlay bloqueante con `mostrarCargando` / `cerrarCargando` de `core/utils/swal-loading.ts`.

### 2. Cadena de trazabilidad
Banda superior con el patrón `.cadena__nodo` / `.cadena__flecha` ya usado en el detalle de
Nota de Venta:

```
[ FACTURA N.º 1042 ]  →  [ NC-000012 · Emitida ]  →  [ 3 movimientos de inventario ]
```

- El primer nodo abre el detalle del documento original con su PDF.
- El nodo central es la NC actual, destacado.
- El tercero abre el movimiento de inventario; se omite si no hay ninguno.

Ver «Qué abre cada nodo de la cadena» al final de este spec.

### 3. Documento asociado — `mat-card.form-panel`
`.form-grid` readonly: Tipo · Folio · Fecha · Total del documento · **Razón de la
asociación** a ancho completo.

### 4. Datos de la Nota de Crédito — `mat-card.form-panel`
Número · Fecha · Cliente (nombre y RUT) · Tipo de corrección · Estado · Bodega de
recuperación · Motivo · Observaciones. Fechas de emisión y anulación cuando existan.

### 5. Corrección de texto
Solo si `tipoCorreccion === 'CORRIGE_TEXTO'`: el texto en un bloque legible a ancho
completo.

### 6. Detalle — `mat-card.form-panel`
Tabla `.items-table` readonly: Código · Descripción · Cantidad · Precio unitario ·
Descuento · Subtotal · **Recupera inventario** (icono `check` / `remove`).
Debajo, el bloque de totales (Subtotal · Descuentos · Neto · IVA · Total).
Se omite completa en CORRIGE_TEXTO.

### 7. Movimientos de inventario relacionados — `mat-card.form-panel`
Tabla con Fecha · Producto · Cantidad · Bodega · Tipo:

| `tipo` | Etiqueta | Tag |
|---|---|---|
| `RECUPERACION` | Recuperación | `tag--success` |
| `REVERSA_ANULACION` | Reversa por anulación | `tag--error` |

`.empty-state` con *"Esta nota de crédito no generó movimientos de inventario."* cuando
la lista viene vacía. Es la respuesta a «consultar movimientos de inventario relacionados».

### 8. Historial — `mat-card.form-panel`
Tabla `.historial-table` con Fecha · Usuario · Acción · Estado anterior → Estado nuevo ·
Detalle, en orden ascendente.

## Estado del componente

```ts
nota: NotaCredito | null = null;
cargando = false;
error: string | null = null;

get esBorrador() { return this.nota?.estado === 'BORRADOR'; }
get esEmitida()  { return this.nota?.estado === 'EMITIDA'; }
```

Las acciones de transición devuelven la NC completa: basta con `this.nota = nota` para que
la pantalla se reconfigure sola (botones, tags, movimientos e historial).

## Responsive

Las tres tablas dentro de contenedores `overflow-x: auto`. La cadena de trazabilidad pasa
a vertical bajo 700 px (las flechas rotan). `.form-grid` colapsa a una columna.
Solo variables CSS; presupuesto SCSS: warning 3 kB, error 8 kB.

## Validación de la tarea

`nota-credito-detalle.component.spec.ts` sin `TestBed`, con spies:

- Con `estado = 'BORRADOR'` se muestran Editar, Emitir y Eliminar, y no Anular.
- Con `estado = 'EMITIDA'` se muestra Anular y no Editar ni Emitir.
- Con `estado = 'ANULADA'` no se muestra ninguna acción de transición.
- `emitir()` llama al servicio y reasigna `this.nota` con la respuesta.
- `anular()` pasa el motivo devuelto por el diálogo; si el diálogo se cierra sin motivo, no
  se llama al servicio.
- Un error del servicio deja `this.error` con el mensaje del backend.

Manual: sobre una NC emitida con recuperación, comprobar que la cadena muestra los tres
nodos y que la tabla de movimientos lista una entrada por línea recuperada; tras anular,
que aparecen también las reversas.

## Qué abre cada nodo de la cadena

| Nodo | Acción |
|---|---|
| Documento asociado | `DocumentoAsociadoDialogComponent` — detalle del documento (tipo, folio, fecha, cliente, líneas con nombre y SKU, totales) y botón **Ver PDF**, que abre `VentaPdfDialogComponent` con `/api/ventas/{id}/pdf`. |
| Nota de Crédito | Es el nodo actual; no navega. |
| Inventario | `MovimientoDetalleDialogComponent` del módulo de Movimientos, cargado con `movimientoService.detalle(headerId)`. Ya trae el detalle por producto y sus exportaciones a PDF y Excel. |

Cada fila de la tabla de movimientos relacionados abre el suyo con el mismo diálogo; las
filas sin `headerId` no son clicables.

El diálogo del documento asociado pide las líneas a `/api/notas-credito/ventas/{id}/lineas`
y no a `/api/ventas/{id}`, porque aquel ya resuelve el nombre y el SKU del producto
(`venta_detalle` solo guarda el id) y además informa la cantidad disponible para recuperar.
