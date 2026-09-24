package cl.slimerp.notasdebito;

import java.math.BigDecimal;

// Proyección de la consulta constructora JPQL que suma lo ya revertido de cada
// línea de la nota de crédito. Clase top-level porque el constructor JPQL
// necesita el nombre completo de la clase simple.
public record CantidadRevertida(Long notaCreditoDetalleId, BigDecimal cantidad) {
}