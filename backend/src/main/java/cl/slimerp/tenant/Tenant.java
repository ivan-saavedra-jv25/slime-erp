package cl.slimerp.tenant;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, unique = true, length = 20)
    private String rut;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String plan = "basico";

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @Column(name = "fecha_alta", nullable = false)
    @Builder.Default
    private LocalDateTime fechaAlta = LocalDateTime.now();
}
