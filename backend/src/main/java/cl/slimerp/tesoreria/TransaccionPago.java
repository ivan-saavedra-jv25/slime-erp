package cl.slimerp.tesoreria;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaccion_pago")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransaccionPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "cuenta_por_cobrar_id", nullable = false)
    private Long cuentaPorCobrarId;

    // Denormalizados desde la cuenta para poder filtrar el historial sin join.
    @Column(name = "venta_id", nullable = false)
    private Long ventaId;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime fecha = LocalDateTime.now();

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(name = "medio_pago", nullable = false, length = 20)
    private MedioPago medioPago;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoTransaccion estado = EstadoTransaccion.CONFIRMADA;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(length = 500)
    private String observaciones;

    // Transferencia
    @Column(name = "transferencia_banco_origen", length = 100)
    private String transferenciaBancoOrigen;

    @Column(name = "transferencia_banco_destino", length = 100)
    private String transferenciaBancoDestino;

    @Column(name = "transferencia_numero_operacion", length = 100)
    private String transferenciaNumeroOperacion;

    @Column(name = "transferencia_fecha")
    private LocalDate transferenciaFecha;

    // Tarjeta
    @Column(name = "tarjeta_entidad", length = 100)
    private String tarjetaEntidad;

    @Column(name = "tarjeta_tipo", length = 50)
    private String tarjetaTipo;

    @Column(name = "tarjeta_numero_operacion", length = 100)
    private String tarjetaNumeroOperacion;

    @Column(name = "tarjeta_fecha")
    private LocalDate tarjetaFecha;

    // Cheque
    @Column(name = "cheque_banco", length = 100)
    private String chequeBanco;

    @Column(name = "cheque_numero", length = 50)
    private String chequeNumero;

    @Column(name = "cheque_fecha_emision")
    private LocalDate chequeFechaEmision;

    @Column(name = "cheque_fecha_pago")
    private LocalDate chequeFechaPago;

    @Column(name = "usuario_anulo_id")
    private Long usuarioAnuloId;

    @Column(name = "fecha_anulacion")
    private LocalDateTime fechaAnulacion;

    @Column(name = "motivo_anulacion", length = 500)
    private String motivoAnulacion;
}
