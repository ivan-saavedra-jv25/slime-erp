package cl.slimerp.notascredito;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "nota_credito_detalle")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotaCreditoDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nota_credito_id", nullable = false)
    @JsonIgnore
    private NotaCredito notaCredito;

    @Column(name = "producto_id", nullable = false)
    private Long productoId;

    // Línea del documento original que esta línea corrige. Es nulo cuando la
    // corrección agrega algo que no estaba en el documento; esas líneas no pueden
    // recuperar inventario porque no hay cantidad vendida contra la cual validar.
    @Column(name = "venta_detalle_id")
    private Long ventaDetalleId;

    // Snapshot del producto al guardar: la nota de crédito es un documento
    // histórico y el catálogo puede cambiar de nombre o SKU después.
    @Column(length = 50)
    private String codigo;

    @Column(nullable = false, length = 255)
    private String descripcion;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal cantidad;

    @Column(name = "precio_unitario", nullable = false, precision = 14, scale = 2)
    private BigDecimal precioUnitario;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal descuento = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    // Si esta línea devuelve mercadería a bodega al emitir la nota de crédito.
    @Column(name = "recupera_inventario", nullable = false)
    @Builder.Default
    private boolean recuperaInventario = false;
}
