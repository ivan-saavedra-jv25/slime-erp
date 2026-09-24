# NC-009 — PDF

## Objetivo

Generar el documento imprimible de la Nota de Crédito, con la misma anatomía que
Cotizaciones y Notas de Venta.

## Implementación

`backend/src/main/java/cl/slimerp/notascredito/NotaCreditoPdfService.java`, espejo de
`NotaVentaPdfService`.

- Librería: **OpenPDF** (`com.github.librepdf:openpdf`, API `com.lowagie.text.*`), ya
  declarada en `backend/pom.xml`. No hay plantillas: el documento se construye
  imperativamente en Java.
- Salida a `ByteArrayOutputStream`, devuelta como `byte[]`.
- Formato monetario: `NumberFormat.getIntegerInstance(new Locale("es", "CL"))` prefijado
  con `$`. Fechas: `dd-MM-yyyy`.
- Errores de OpenPDF: `catch (DocumentException) -> throw new IllegalStateException(
  "No se pudo generar el PDF de la nota de crédito")`. Es el único uso legítimo de
  `IllegalStateException` aquí (inconsistencia interna, no error de negocio).

## Contenido

1. **Título**: `NOTA DE CRÉDITO NC-000012` (usando `NumeroNotaCredito.formatear`).
2. **Emisor**: datos del tenant desde `TenantRepository`.
3. **Cliente**: nombre, RUT, razón social, giro, dirección, comuna/ciudad.
4. **Documento asociado** — bloque propio, es lo que distingue a este documento:

   ```
   Documento asociado:  FACTURA N.º 1042 del 12-09-2026
   Razón:               Devolución parcial de mercadería
   ```

5. **Tipo de corrección**: etiqueta legible (`Corrige documento` / `Corrige monto` /
   `Corrige texto`).
6. **Detalle** — `PdfPTable` con Código, Descripción, Cantidad, Precio unitario,
   Descuento (columna condicional: solo si alguna línea trae descuento), Subtotal y una
   marca de **Recupera inventario**. Se omite completo en CORRIGE_TEXTO.
7. **Texto de la corrección** — solo en CORRIGE_TEXTO, como párrafo a ancho completo.
8. **Totales**, alineados a la derecha: Subtotal, Descuentos, Neto, IVA (omitido si
   `exenta`), Total.
9. **Pie**: motivo, observaciones, estado y, si está anulada, la fecha de anulación.

Si la NC está ANULADA, agregar una marca visible (texto `ANULADA` destacado bajo el
título) para que el PDF no se confunda con un documento vigente.

## Controller

```java
@GetMapping("/{id}/pdf")
@PreAuthorize("hasAuthority('NOTAS_CREDITO_VER')")
public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
    NotaCredito nc = notaCreditoService.obtenerEntidad(id);
    byte[] pdf = notaCreditoPdfService.generar(nc);
    String nombreArchivo = NumeroNotaCredito.formatear(nc.getFolio()) + ".pdf";
    return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + nombreArchivo)
            .body(pdf);
}
```

## Validación de la tarea

`NotaCreditoPdfServiceTest`: generar el `byte[]` de una NC de cada tipo de corrección y
verificar que empieza con `%PDF` y que no lanza excepción. Manual: abrir el PDF desde el
detalle y comprobar el bloque de documento asociado y los totales.
