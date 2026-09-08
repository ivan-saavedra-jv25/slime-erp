package cl.slimerp.admin.plan;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "plan", schema = "admin")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    @Column(name = "precio_mensual", nullable = false, precision = 14, scale = 2)
    private BigDecimal precioMensual;

    @Column(name = "precio_anual", precision = 14, scale = 2)
    private BigDecimal precioAnual;

    @Column(name = "max_usuarios", nullable = false)
    private Integer maxUsuarios;

    @Column(name = "max_documentos", nullable = false)
    private Integer maxDocumentos;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String modulos;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String caracteristicas;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String estado = "ACTIVE";
}