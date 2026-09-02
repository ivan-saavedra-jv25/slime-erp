package cl.slimerp.inventario;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "stock_producto_bodega")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockProductoBodega {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "producto_id", nullable = false)
    private Long productoId;

    @Column(name = "bodega_id", nullable = false)
    private Long bodegaId;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal cantidad = BigDecimal.ZERO;
}
