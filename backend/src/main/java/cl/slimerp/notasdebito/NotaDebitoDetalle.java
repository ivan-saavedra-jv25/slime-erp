package cl.slimerp.notasdebito;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "nota_debito_detalle")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotaDebitoDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nota_debito_id", nullable = false)
    @JsonIgnore
    private NotaDebito notaDebito;

    // Línea de la nota de crédito que esta línea revierte. Es nulo cuando la
    // reversión se asocia a información no vinculada a una línea de la NC; esas
    // líneas no pueden revertir inventario porque no hay cantidad contra la que
    // validar.
    @Column(name = "nota_credito_detalle_id")
    private Long notaCreditoDetalleId;

    @Column(name = "producto_id", nullable = false)
    private Long productoId;

    // Snapshot del producto al guardar: la nota de débito es un documento
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

    // Si esta línea impacta inventario al emitir la nota de débito (revierte la
    // recuperación que hizo la NC). Se hereda de la línea de la NC original.
    @Column(name = "revierte_inventario", nullable = false)
    @Builder.Default
    private boolean revierteInventario = true;
}