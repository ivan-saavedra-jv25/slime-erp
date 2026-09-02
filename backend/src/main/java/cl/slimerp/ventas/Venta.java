package cl.slimerp.ventas;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "venta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(name = "forma_pago_id", nullable = false)
    private Long formaPagoId;

    @Column(name = "bodega_id", nullable = false)
    private Long bodegaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 20)
    private TipoDocumentoVenta tipoDocumento;

    // Boleta/Factura: exenta (true, sin IVA) vs. afecta (false, con IVA).
    // Voucher: distingue venta interna (false) de venta exenta (true); nunca
    // lleva IVA en ningún caso.
    @Column(nullable = false)
    @Builder.Default
    private boolean exento = false;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime fecha = LocalDateTime.now();

    @Column(name = "monto_neto", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal montoNeto = BigDecimal.ZERO;

    @Column(name = "monto_iva", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal montoIva = BigDecimal.ZERO;

    @Column(name = "monto_total", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal montoTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal descuento = BigDecimal.ZERO;

    @Column(length = 500)
    private String observacion;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<VentaDetalle> detalle = new ArrayList<>();
}
