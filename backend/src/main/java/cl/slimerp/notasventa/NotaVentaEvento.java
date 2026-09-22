package cl.slimerp.notasventa;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "nota_venta_evento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotaVentaEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "nota_venta_id", nullable = false)
    private Long notaVentaId;

    // Nulo cuando el evento no lo genera un usuario (por ejemplo, un proceso
    // automático futuro).
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccionNotaVenta accion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", length = 30)
    private EstadoNotaVenta estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", length = 30)
    private EstadoNotaVenta estadoNuevo;

    @Column(length = 500)
    private String detalle;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime fecha = LocalDateTime.now();
}