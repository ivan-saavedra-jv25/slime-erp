package cl.slimerp.compras;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculadoraMontosCompraTest {

    @Test
    void calculaElIvaComoUn19PorcientoDelNeto() {
        CalculadoraMontosCompra.Montos montos =
                CalculadoraMontosCompra.calcularDesdeNeto(new BigDecimal("1000"));

        assertEquals(new BigDecimal("1000"), montos.neto());
        assertEquals(new BigDecimal("190.00"), montos.iva());
    }

    @Test
    void redondeaElIvaADosDecimales() {
        CalculadoraMontosCompra.Montos montos =
                CalculadoraMontosCompra.calcularDesdeNeto(new BigDecimal("333.33"));

        assertEquals(new BigDecimal("63.33"), montos.iva());
    }

    @Test
    void unNetoEnCeroDevuelveIvaEnCero() {
        CalculadoraMontosCompra.Montos montos =
                CalculadoraMontosCompra.calcularDesdeNeto(BigDecimal.ZERO);

        assertEquals(BigDecimal.ZERO, montos.neto());
        assertEquals(new BigDecimal("0.00"), montos.iva());
    }
}
