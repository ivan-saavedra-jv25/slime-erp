package cl.slimerp.inventario;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "movimiento_inventario_header")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimientoInventarioHeader {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimiento tipo;

    @Column(name = "bodega_origen_id")
    private Long bodegaOrigenId;

    @Column(name = "bodega_destino_id")
    private Long bodegaDestinoId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(length = 500)
    private String observacion;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime fecha = LocalDateTime.now();
}
