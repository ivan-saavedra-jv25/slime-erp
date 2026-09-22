package cl.slimerp.notasventa;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "nota_venta_detalle")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotaVentaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nota_venta_id", nullable = false)
    @JsonIgnore
    private NotaVenta notaVenta;

    @Column(name = "producto_id", nullable = false)
    private Long productoId;

    // Snapshot del producto al guardar: la nota de venta es un documento
    // histórico y el catálogo puede cambiar de nombre o SKU después.
    @Column(length = 50)
    private String codigo;

    @Column(nullable = false, length = 255)
    private String descripcion;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal cantidad;

    @Column(name = "cantidad_entregada", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal cantidadEntregada = BigDecimal.ZERO;

    @Column(name = "precio_unitario", nullable = false, precision = 14, scale = 2)
    private BigDecimal precioUnitario;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal descuento = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;
}