package cl.slimerp.notascredito;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Vínculo entre la nota de crédito y los movimientos de inventario que generó.
// Existe porque movimiento_inventario.referencia_id es un id "desnudo" sin
// discriminador de tabla: sin esta tabla habría que inferir el origen desde el
// tipo de movimiento, y no quedaría registro de qué línea produjo qué entrada.
@Entity
@Table(name = "nota_credito_movimiento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotaCreditoMovimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "nota_credito_id", nullable = false)
    private Long notaCreditoId;

    @Column(name = "nota_credito_detalle_id")
    private Long notaCreditoDetalleId;

    @Column(name = "movimiento_inventario_id", nullable = false)
    private Long movimientoInventarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimientoNotaCredito tipo;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime fecha = LocalDateTime.now();
}
