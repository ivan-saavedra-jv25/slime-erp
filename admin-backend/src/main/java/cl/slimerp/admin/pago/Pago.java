package cl.slimerp.admin.pago;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pago", schema = "admin")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "suscripcion_id")
    private Long suscripcionId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String moneda = "CLP";

    @Column(nullable = false, length = 30)
    private String metodo;

    @Column(nullable = false, length = 20)
    private String estado;

    @Column(name = "referencia_externa", length = 150)
    private String referenciaExterna;

    @Column(name = "pagado_en")
    private LocalDateTime pagadoEn;

    @Column(name = "creado_por")
    private Long creadoPor;

    @Column(name = "creado_en", nullable = false)
    @Builder.Default
    private LocalDateTime creadoEn = LocalDateTime.now();
}