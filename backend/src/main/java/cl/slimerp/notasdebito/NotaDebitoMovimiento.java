package cl.slimerp.notasdebito;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Vínculo entre la nota de débito y los movimientos de inventario que generó.
// Existe porque movimiento_inventario.referencia_id es un id "desnudo" sin
// discriminador de tabla: sin esta tabla no quedaría registro de qué línea
// produjo qué salida, y no se podría comprobar la anti doble reversión.
@Entity
@Table(name = "nota_debito_movimiento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotaDebitoMovimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "nota_debito_id", nullable = false)
    private Long notaDebitoId;

    @Column(name = "nota_debito_detalle_id")
    private Long notaDebitoDetalleId;

    @Column(name = "movimiento_inventario_id", nullable = false)
    private Long movimientoInventarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimientoNotaDebito tipo;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime fecha = LocalDateTime.now();
}