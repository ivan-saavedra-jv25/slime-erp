package cl.slimerp.catalogo;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "producto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(length = 50)
    private String sku;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    @Column(name = "precio_venta", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal precioVenta = BigDecimal.ZERO;

    @Column(name = "precio_compra", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal precioCompra = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal stock = BigDecimal.ZERO;

    @Column(name = "controla_stock", nullable = false)
    @Builder.Default
    private boolean controlaStock = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @Column(name = "fecha_creacion", nullable = false)
    @Builder.Default
    private LocalDateTime fechaCreacion = LocalDateTime.now();
}
