package cl.slimerp.ventas;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculadoraMontosVentaTest {

    @Test
    void facturaCalculaIvaSobreElNetoDeDetalle() {
        var montos = CalculadoraMontosVenta.calcular(TipoDocumentoVenta.FACTURA, new BigDecimal("1000"));

        assertEquals(new BigDecimal("1000.00"), montos.neto());
        assertEquals(new BigDecimal("190.00"), montos.iva());
        assertEquals(new BigDecimal("1190.00"), montos.total());
    }

    @Test
    void boletaDesglosaElNetoYElIvaDesdeElBrutoDeDetalle() {
        var montos = CalculadoraMontosVenta.calcular(TipoDocumentoVenta.BOLETA, new BigDecimal("1190"));

        assertEquals(new BigDecimal("1190.00"), montos.total());
        // neto + iva debe reconstruir el total exacto (el IVA absorbe el redondeo)
        assertEquals(montos.total(), montos.neto().add(montos.iva()));
        assertEquals(new BigDecimal("1000.00"), montos.neto());
        assertEquals(new BigDecimal("190.00"), montos.iva());
    }

    @Test
    void boletaConMontoNoDivisibleExactoElIvaAbsorbeElResiduo() {
        var montos = CalculadoraMontosVenta.calcular(TipoDocumentoVenta.BOLETA, new BigDecimal("1000"));

        assertEquals(montos.total(), montos.neto().add(montos.iva()));
        assertEquals(new BigDecimal("1000.00"), montos.total());
    }

    @Test
    void voucherNoAplicaIva() {
        var montos = CalculadoraMontosVenta.calcular(TipoDocumentoVenta.VOUCHER, new BigDecimal("500"));

        assertEquals(new BigDecimal("500.00"), montos.neto());
        assertEquals(new BigDecimal("0.00"), montos.iva());
        assertEquals(new BigDecimal("500.00"), montos.total());
    }

    @Test
    void unaSumaNegativaSeTrataComoCero() {
        var montos = CalculadoraMontosVenta.calcular(TipoDocumentoVenta.FACTURA, new BigDecimal("-50"));

        assertEquals(new BigDecimal("0.00"), montos.neto());
        assertEquals(new BigDecimal("0.00"), montos.iva());
        assertEquals(new BigDecimal("0.00"), montos.total());
    }
}
