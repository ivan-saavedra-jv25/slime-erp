# ND-009 — PDF

## Objetivo

`NotaDebitoPdfService` con OpenPDF (com.lowagie), espejo de `NotaCreditoPdfService`.

## Anatomía del PDF

- Cabecera con datos del tenant/empresa (igual que NC).
- Panel "Documento de débito": Nº `ND-000012`, fecha, cliente, estado.
- Panel "Nota de Crédito asociada": `NC-000012`, tipo de documento de la venta original,
  folio, fecha, monto total, razón.
- Panel "Tipo de reversión" y motivo/observaciones/`textoCorreccion`.
- Tabla de líneas: código, descripción, cantidad, precio unitario, descuento, subtotal,
  y columna "Rev. inventario" (Sí/No).
- Totales: subtotal, descuento, neto, IVA, total (usando `formatoMonto`).
- Marca **"ANULADA"** en negrita si el estado es `ANULADA` (misma técnica que NC).
- Footer con pie del documento.

Reutilizar helpers privados: `tablaDetalle`, `etiquetaTipo`, `totalAlineado`,
`formatoMonto`, `descripcionDocumento` — copiar de `NotaCreditoPdfService` cambiando el
prefijo del número (`ND-`).

## Firmas

```java
@Service
public class NotaDebitoPdfService {
    public byte[] generar(NotaDebito nota) { ... }
}
```

Guardia: `obtenerEntidad(id)` fuerza la carga del detalle (open-in-view deshabilitado),
igual que en NC.

## Validación

- `NotaDebitoPdfServiceTest` (espejo del de NC): generar con BORRADOR/EMITIDA (marca
  ANULADA solo en este último si aplica); el PDF contiene `ND-000001`, total formateado y
  los textos clave; no lanza excepciones con detalle vacío.