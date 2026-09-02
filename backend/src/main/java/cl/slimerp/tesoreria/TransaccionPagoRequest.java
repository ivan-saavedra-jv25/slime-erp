package cl.slimerp.tesoreria;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransaccionPagoRequest(
        @NotNull @Positive BigDecimal monto,
        @NotNull MedioPago medioPago,
        String observaciones,
        String transferenciaBancoOrigen,
        String transferenciaBancoDestino,
        String transferenciaNumeroOperacion,
        LocalDate transferenciaFecha,
        String tarjetaEntidad,
        String tarjetaTipo,
        String tarjetaNumeroOperacion,
        LocalDate tarjetaFecha,
        String chequeBanco,
        String chequeNumero,
        LocalDate chequeFechaEmision,
        LocalDate chequeFechaPago
) {
}
