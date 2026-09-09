package cl.slimerp.admin.configuracion;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "configuracion", schema = "admin")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Configuracion {

    @Id
    @Column(name = "clave", length = 80)
    private String clave;

    @Column(nullable = false)
    private String valor;

    @Column(length = 300)
    private String descripcion;

    @Column(name = "actualizado_en", nullable = false)
    @Builder.Default
    private LocalDateTime actualizadoEn = LocalDateTime.now();

    @Column(name = "actualizado_por")
    private Long actualizadoPor;
}