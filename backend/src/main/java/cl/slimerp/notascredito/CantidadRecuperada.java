package cl.slimerp.notascredito;

import java.math.BigDecimal;

// Cuánto se ha recuperado ya de una línea del documento original. Es un tipo de
// nivel superior (y no un record anidado en el repositorio) porque se construye
// desde una expresión constructora JPQL, que necesita un nombre de clase simple.
public record CantidadRecuperada(Long ventaDetalleId, BigDecimal cantidad) {
}
