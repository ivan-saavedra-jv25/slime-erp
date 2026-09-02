package cl.slimerp.ventas;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Única fuente de verdad para el cálculo de neto/IVA/total de una venta.
// No tiene relación con el módulo de facturación electrónica: es una
// clasificación interna para saber cómo interpretar los montos.
public final class CalculadoraMontosVenta {

    public static final BigDecimal TASA_IVA = new BigDecimal("0.19");
    private static final BigDecimal FACTOR_IVA = BigDecimal.ONE.add(TASA_IVA);
    private static final int ESCALA = 2;

    public record Montos(BigDecimal neto, BigDecimal iva, BigDecimal total) {
    }

    private CalculadoraMontosVenta() {
    }

    // Calcula neto/IVA/total según el tipo de documento a partir de la suma de
    // los subtotales de detalle (ya con el descuento aplicado):
    //   - FACTURA: la suma de detalle es NETA -> se calcula y suma el IVA.
    //   - BOLETA: la suma de detalle es BRUTA (IVA incluido) -> se desglosa.
    //   - VOUCHER: sin IVA, el total es la suma tal cual.
    public static Montos calcular(TipoDocumentoVenta tipoDocumento, BigDecimal sumaDetalle) {
        BigDecimal monto = sumaDetalle.max(BigDecimal.ZERO).setScale(ESCALA, RoundingMode.HALF_UP);
        return switch (tipoDocumento) {
            case FACTURA -> desdeNeto(monto);
            case BOLETA -> desdeBruto(monto);
            case VOUCHER -> new Montos(monto, BigDecimal.ZERO.setScale(ESCALA), monto);
        };
    }

    private static Montos desdeNeto(BigDecimal neto) {
        BigDecimal iva = neto.multiply(TASA_IVA).setScale(ESCALA, RoundingMode.HALF_UP);
        BigDecimal total = neto.add(iva);
        return new Montos(neto, iva, total);
    }

    private static Montos desdeBruto(BigDecimal total) {
        BigDecimal neto = total.divide(FACTOR_IVA, ESCALA, RoundingMode.HALF_UP);
        // El IVA absorbe el residuo de redondeo: neto + iva == total siempre.
        BigDecimal iva = total.subtract(neto);
        return new Montos(neto, iva, total);
    }
}
