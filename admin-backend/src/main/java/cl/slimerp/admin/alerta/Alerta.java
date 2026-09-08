package cl.slimerp.admin.alerta;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "alerta", schema = "admin")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(length = 1000)
    private String descripcion;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "OPEN";

    @Column(name = "creada_en", nullable = false)
    @Builder.Default
    private LocalDateTime creadaEn = LocalDateTime.now();

    @Column(name = "leida_en")
    private LocalDateTime leidaEn;

    @Column(name = "resuelta_en")
    private LocalDateTime resueltaEn;
}