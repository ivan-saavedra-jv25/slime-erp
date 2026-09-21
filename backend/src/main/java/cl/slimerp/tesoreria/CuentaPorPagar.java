package cl.slimerp.tesoreria;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cuenta_por_pagar")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CuentaPorPagar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "compra_id")
    private Long compraId;

    @Column(name = "gasto_id")
    private Long gastoId;

    @Column(name = "proveedor_id")
    private Long proveedorId;

    @Column(name = "categoria_gasto_id")
    private Long categoriaGastoId;

    @Column(nullable = false, length = 255)
    private String descripcion;

    @Column(name = "fecha_generacion", nullable = false)
    @Builder.Default
    private LocalDateTime fechaGeneracion = LocalDateTime.now();

    @Column(name = "monto_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoTotal;

    @Column(name = "monto_pagado", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal montoPagado = BigDecimal.ZERO;

    @Column(name = "saldo_pendiente", nullable = false, precision = 14, scale = 2)
    private BigDecimal saldoPendiente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoCuentaPorPagar estado = EstadoCuentaPorPagar.DEUDA;

    @Column(name = "fecha_ultimo_pago")
    private LocalDateTime fechaUltimoPago;

    @Column(length = 500)
    private String observaciones;

    @Column(name = "usuario_anulo_id")
    private Long usuarioAnuloId;

    @Column(name = "fecha_anulacion")
    private LocalDateTime fechaAnulacion;

    @Column(name = "motivo_anulacion", length = 500)
    private String motivoAnulacion;
}
