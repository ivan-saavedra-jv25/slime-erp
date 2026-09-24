# ND-012 — Frontend — Formulario

## Objetivo

Formulario crear/editar de Nota de Débito en 7 secciones, template-driven, con staging de
líneas y validación imperativa. Espejo de `NotaCreditoFormComponent`.

## Archivos

- `frontend/src/app/features/notas-debito/nota-debito-form.component.ts`
- `frontend/src/app/features/notas-debito/nota-debito-form.component.html`
- `frontend/src/app/features/notas-debito/nota-debito-form.component.scss`
- Constantes: `frontend/src/app/features/notas-debito/estado-nota-debito.ts`
  (ESTADOS, ETIQUETAS_ESTADO, TAGS_ESTADO, TIPOS_REVERSION, ETIQUETAS_TIPO_REVERSION,
  AYUDA_TIPO_REVERSION, ETIQUETAS_IMPACTO, TAGS_IMPACTO, ETIQUETAS_MOVIMIENTO,
  TAGS_MOVIMIENTO).

## Secciones (7) — según CLAUDE.md

1. **Documento a revertir**: autocomplete custom de cliente (debounce) + autocomplete de
   Nota de Crédito asociable (número `NC-000012`, cliente, monto y disponible). Al
   seleccionar, ficha "documento-ficha" con tipo de documento original, folio NC, fecha,
   monto total, monto disponible y razón (`ncRazon`).
2. **Tipo de reversión**: radio-cards con `REVIERTE_DOCUMENTO / REVIERTE_MONTO /
   REVIERTE_TEXTO` + texto de ayuda (AYUDA_TIPO_REVERSION).
3. **Detalle de líneas** (`REVIERTE_DOCUMENTO/MONTO`): tabla editable de líneas de la NC
   cargadas por `lineasNotaCredito(ncId)` con columnas código, descripción, cantidad a
   revertir (input), precio, descuento, subtotal y checkbox "revierte inventario".
   `REVIERTE_DOCUMENTO` precarga todo con `cantidadDisponible`; `REVIERTE_MONTO` deja
   editar; ninguna línea puede superar `cantidadDisponible`.
4. **Texto de corrección** (`REVIERTE_TEXTO`): textarea `textoCorreccion`.
5. **Información complementaria**: motivo, observaciones, descuento global.
6. **Totales**: subtotal, descuento, neto, IVA, total (TASA_IVA=0.19, espejo NC;
   redondeo bruto/neto según tipo de documento de la venta original mostrado en la ficha).
7. **Acciones**: "Guardar borrador" (primario), "Cancelar" (secundario), y en edición
   "Eliminar" (rojo). Posición consistente con el resto de formularios.

## Reglas frontend

- Al cambiar la NC, recargar `lineasNotaCredito(ncId, excluyendoNotaDebitoId?)` y
  reiniciar staging de líneas.
- Al cambiar `tipoReversion`:
  - → REVIERTE_TEXTO: limpiar líneas, ocultar tabla, mostrar textarea; totales a cero.
  - → REVIERTE_DOCUMENTO: precargar todas las líneas disponibles con
    `revierteInventario = true` (según `recuperaInventario` de la NC).
  - → REVIERTE_MONTO: mantener líneas editables.
- Validación imperativa antes de enviar: NC seleccionada, razón no vacía, cantidad > 0 y
  ≤ disponible, descuento ≥ 0, texto si REVIERTE_TEXTO.
- Solo soporta edición de estados `BORRADOR` (carga previa con `obtener(id)`).
- El request NO envía `id` (lo genera la base de datos).

## Validación

- `ng build` compila; prueba manual del flujo crear/editar con NC asociable.