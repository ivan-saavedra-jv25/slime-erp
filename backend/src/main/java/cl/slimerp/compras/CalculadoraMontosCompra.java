package cl.slimerp.compras;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Única fuente de verdad para el desglose Neto/IVA de una compra. Los precios
// que se ingresan en el formulario de Compra son netos (sin IVA), por lo que
// "total" (suma de cantidad × precioUnitario de los ítems) ya es el monto
// neto: este cálculo solo agrega el IVA por encima, sin alterar "total" ni lo
// que consume de él Stock/CuentaPorPagar.
public final class CalculadoraMontosCompra {

    public static final BigDecimal TASA_IVA = new BigDecimal("0.19");
    private static final int ESCALA = 2;

    public record Montos(BigDecimal neto, BigDecimal iva) {
    }

    private CalculadoraMontosCompra() {
    }

    public static Montos calcularDesdeNeto(BigDecimal neto) {
        BigDecimal iva = neto.multiply(TASA_IVA).setScale(ESCALA, RoundingMode.HALF_UP);
        return new Montos(neto, iva);
    }
}
