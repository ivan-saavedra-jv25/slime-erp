package cl.slimerp.admin.cobranza;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "cobranza_empresa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CobranzaEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 150)
    private String concepto;

    @Column(nullable = false, length = 20)
    private String periodo;

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
    private EstadoCobranza estado = EstadoCobranza.DEUDA;

    @Column(name = "fecha_emision", nullable = false)
    @Builder.Default
    private LocalDateTime fechaEmision = LocalDateTime.now();

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @Column(name = "fecha_ultimo_pago")
    private LocalDateTime fechaUltimoPago;

    @Column(length = 500)
    private String observaciones;

    @Column(name = "usuario_admin_id")
    private Long usuarioAdminId;

    @Column(name = "fecha_anulacion")
    private LocalDateTime fechaAnulacion;

    @Column(name = "motivo_anulacion", length = 500)
    private String motivoAnulacion;
}