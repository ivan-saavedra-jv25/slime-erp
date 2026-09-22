package cl.slimerp.notasventa;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "nota_venta_entrega_linea")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotaVentaEntregaLinea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entrega_id", nullable = false)
    @JsonIgnore
    private NotaVentaEntrega entrega;

    @Column(name = "producto_id", nullable = false)
    private Long productoId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal cantidad;
}