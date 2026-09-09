package cl.slimerp.admin.cobranza;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cobranza_pago")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CobranzaPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cobranza_empresa_id", nullable = false)
    private Long cobranzaEmpresaId;

    // Empresa (tenant) que paga.
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

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
    private EstadoPagoCobranza estado = EstadoPagoCobranza.CONFIRMADA;

    @Column(name = "numero_operacion", length = 100)
    private String numeroOperacion;

    @Column(length = 500)
    private String observaciones;

    @Column(name = "usuario_admin_id")
    private Long usuarioAdminId;

    @Column(name = "fecha_anulacion")
    private LocalDateTime fechaAnulacion;

    @Column(name = "motivo_anulacion", length = 500)
    private String motivoAnulacion;
}