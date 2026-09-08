package cl.slimerp.admin.suscripcion;

import cl.slimerp.admin.plan.Plan;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "suscripcion", schema = "admin")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Suscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(nullable = false, length = 30)
    private String estado;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(name = "ciclo_facturacion", nullable = false, length = 20)
    private String cicloFacturacion;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal precio;

    @Column(name = "periodo_gracia_dias", nullable = false)
    @Builder.Default
    private Integer periodoGraciaDias = 0;
}