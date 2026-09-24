# NC-012 — Frontend: formulario

## Objetivo

Formulario de creación y edición de borrador, con jerarquía visual clara y un orden que se
complete naturalmente de arriba hacia abajo.

## Archivos

`frontend/src/app/features/notas-credito/nota-credito-form.component.ts | .html | .scss`

## Patrón obligatorio del proyecto

**No usar Reactive Forms.** `FormBuilder`, `FormArray` y `ReactiveFormsModule` tienen cero
ocurrencias en todo el repositorio. El patrón real:

- Template-driven con `FormsModule` y `[(ngModel)]` sobre **propiedades planas de la clase**
  (con `name="..."` suelto, sin `<form>` envolvente).
- Las líneas son un **array plano** `items: NotaCreditoItem[]`, manipulado con `push` /
  `splice`. No hay `FormArray`.
- Validación **imperativa**: un método privado que devuelve `string | null` (mensaje o
  null) y un getter `puedeGuardar`.
- Inputs, selects, textareas, checkboxes y date pickers son **HTML nativo** dentro de
  `.form-group`. No usar `mat-form-field` ni `MatDatepicker`.

## Secciones (jerarquía según CLAUDE.md)

### 1. Encabezado
`.page-header` con `<h1>{{ esEdicion ? 'Editar' : 'Nueva' }} Nota de Crédito</h1>`,
una línea de descripción breve, y botón **Volver** (`mat-stroked-button`).

### 2. Documento asociado — `mat-card.form-panel`
Va primero porque **todo lo demás depende de él**: cliente, bodega, base impositiva y
líneas disponibles salen de aquí.

- Buscador de **cliente** (`input` + dropdown `.search-results`, debounce 300 ms) → al
  elegirlo se muestra `.cliente-seleccionado` con nombre y RUT.
- Buscador de **documento** (solo habilitado con cliente elegido): llama a
  `documentosAsociables(clienteId, q)` y lista tipo, folio, fecha y total.
  Marcar visualmente los que ya tienen NC previas (`tieneNotasCredito`).
- Ficha readonly del documento elegido: Tipo · Folio · Fecha · Total.
- **Razón de la asociación** — `textarea`, obligatorio (`.required-mark`), `.span-2`.

Al elegir el documento se cargan sus líneas con `lineasDocumento(ventaId, notaCreditoId?)`.
Cambiar de documento con líneas ya cargadas pide confirmación y limpia el detalle.

### 3. Tipo de corrección — `mat-card.form-panel`
Tres opciones como radio buttons en una fila (columna en móvil), obligatorio. Cada una con
una línea de ayuda `.form-section-hint`:

| Opción | Ayuda |
|---|---|
| Corrige documento | *Anula o reemplaza el documento completo. Se cargan todas sus líneas.* |
| Corrige monto | *Corrige total o parcialmente los valores. Elija las líneas a corregir.* |
| Corrige texto | *Corrige información textual. No afecta montos ni inventario.* |

Cambiar el tipo reconfigura las secciones 4 y 5. Si hay datos cargados que se perderían,
confirmar antes.

### 4. Detalle — `mat-card.form-panel`
Visible solo con CORRIGE_DOCUMENTO o CORRIGE_MONTO.

Tabla `.items-table` con las líneas del documento original:

| Columna | Comportamiento |
|---|---|
| Producto | código + descripción, readonly |
| Cant. vendida | readonly |
| Disponible | readonly — `cantidadDisponible` |
| Cant. a corregir | `<input type="number">`, máximo la disponible cuando recupera inventario |
| Precio unitario | editable en CORRIGE_MONTO, readonly en CORRIGE_DOCUMENTO |
| Descuento | editable |
| Subtotal | calculado |
| **Recupera inventario** | `<input type="checkbox">`; deshabilitado si `cantidadDisponible === 0`, con `title` explicando por qué |
| Acción | quitar la línea (solo CORRIGE_MONTO) |

En **CORRIGE_DOCUMENTO** la tabla llega precargada y completa, con todos los checkboxes
marcados; el usuario puede desmarcar la recuperación pero no quitar líneas.

En **CORRIGE_MONTO** el usuario elige qué líneas incluir. Reutilizar el patrón de
*staging* de `nota-venta-form.component.ts` para agregar líneas que no estaban en el
documento original (esas no pueden recuperar inventario: checkbox deshabilitado).

Nombres de producto cacheados en `private productosConocidos = new Map<number, Producto>()`.

### 5. Corrección de texto — `mat-card.form-panel`
Visible solo con CORRIGE_TEXTO. Un `textarea` a ancho completo (`.span-2`), obligatorio,
con contador de caracteres (máx. 2000).

### 6. Información complementaria — `mat-card.form-panel`
`.form-grid` de 2 columnas: **Fecha** (`<input type="date">`, obligatoria) y **Motivo**;
**Observaciones** como `textarea` en `.span-2`.

### 7. Totales y acciones — `mat-card.form-panel`
`.totals-grid`: descuento global a la izquierda, `.totals-group` de `.totals-row` a la
derecha (Subtotal · Descuentos · Neto · IVA · **Total** con `.totals-row--total`).
Oculto en CORRIGE_TEXTO (todos los montos son 0).

`.form-actions`: **Guardar borrador** (`mat-flat-button color="primary"`) y **Cancelar**
(`mat-stroked-button`). Un solo botón primario. La emisión se hace desde el detalle, no
desde el formulario: emitir es una acción sobre un documento ya guardado.

## Totales — getters puros

El IVA depende del tipo de documento asociado, no de una tasa fija aplicada siempre:

```ts
get subtotalActual()  { return this.items.reduce((a, it) => a + it.cantidad * it.precioUnitario, 0); }
get descuentoLineas() { return this.items.reduce((a, it) => a + (it.descuento || 0), 0); }
get descuentoTotal()  { return this.descuentoLineas + (this.descuento || 0); }
get baseActual()      { return Math.max(this.subtotalActual - this.descuentoTotal, 0); }
// FACTURA afecta: la base es neta y el IVA se suma.
// BOLETA afecta: la base es bruta y se desglosa.
// Exenta o VOUCHER: sin IVA.
```

Son una previsualización; la cifra que manda es la que calcula el backend con
`CalculadoraMontosVenta`. Tras guardar, refrescar con la respuesta del servidor.

## Validación

Imperativa, con los mensajes cerca del campo (`.field-error`) y el error general en
`<p class="page-error">`:

```ts
get puedeGuardar(): boolean {
  return !this.guardando
      && !!this.documentoSeleccionado
      && !!this.razonAsociacion?.trim()
      && !!this.tipoCorreccion
      && (this.tipoCorreccion === 'CORRIGE_TEXTO'
            ? !!this.textoCorreccion?.trim()
            : this.items.length > 0);
}
```

Por línea: cantidad > 0; precio ≥ 0; descuento ≥ 0 y ≤ subtotal de la línea; si recupera
inventario, cantidad ≤ disponible (*"Solo hay 2 disponibles para recuperar"*).

## Guardado

`mostrarCargando('Guardando nota de crédito')` → `esEdicion ? actualizar(id, req) : crear(req)`
→ `cerrarCargando().then(() => this.router.navigate(['/notas-credito', nc.id]))`.
El `id` **nunca** se envía en el request de creación.

Al editar (`/notas-credito/:id/editar`), cargar la NC y llamar `lineasDocumento(ventaId,
id)` pasando el propio id para que sus líneas no cuenten como ya recuperadas. Si la NC no
está en BORRADOR, redirigir al detalle con un mensaje.

## Estados visuales y responsive

Cubrir normal, hover, focus, disabled, error y loading con los tokens existentes
(`--focus-ring`, `--border-interactive`, `--error-base`). `.two-col` y `.form-grid`
colapsan a una columna bajo 900 px; la tabla de detalle scrollea horizontalmente dentro
de su contenedor. Solo variables CSS, nunca valores crudos.

## Validación de la tarea

Manual, siguiendo el plan: crear una NC de cada tipo, comprobar que las secciones aparecen
y desaparecen según el tipo, que no se puede marcar recuperación sobre una línea sin
disponible, y que los totales previsualizados coinciden con los que devuelve el backend
tras guardar. Verificar en desktop, tablet y móvil.
