package cl.slimerp.catalogo;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "cliente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 20)
    private String rut;

    @Column(length = 150)
    private String email;

    @Column(length = 30)
    private String telefono;

    @Column(length = 255)
    private String direccion;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @Column(name = "fecha_creacion", nullable = false)
    @Builder.Default
    private LocalDateTime fechaCreacion = LocalDateTime.now();
}
