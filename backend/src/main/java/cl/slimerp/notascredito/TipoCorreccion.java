package cl.slimerp.notascredito;

// Tipo de anulación o corrección. Es obligatorio y determina el comportamiento
// funcional de la nota de crédito:
//   - CORRIGE_DOCUMENTO: reemplaza o corrige el documento asociado completo. El
//     detalle se precarga con todas sus líneas y todas recuperan inventario por
//     defecto.
//   - CORRIGE_MONTO: modifica total o parcialmente los valores de la operación.
//     El usuario elige qué líneas corregir, con qué cantidad, precio y descuento,
//     y cuáles recuperan inventario.
//   - CORRIGE_TEXTO: corrige información textual del documento. No lleva líneas,
//     todos los montos quedan en cero y nunca toca inventario.
public enum TipoCorreccion {
    CORRIGE_DOCUMENTO,
    CORRIGE_MONTO,
    CORRIGE_TEXTO
}
