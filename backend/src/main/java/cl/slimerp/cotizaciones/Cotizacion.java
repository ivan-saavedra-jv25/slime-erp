package cl.slimerp.cotizaciones;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cotizacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cotizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private Integer folio;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(name = "vendedor_id", nullable = false)
    private Long vendedorId;

    @Column(name = "forma_pago_id")
    private Long formaPagoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoCotizacion estado = EstadoCotizacion.BORRADOR;

    @Column(nullable = false)
    @Builder.Default
    private boolean exenta = false;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDate fechaEmision;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    // Descuento global de cabecera, aplicado sobre la suma de las líneas.
    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal descuento = BigDecimal.ZERO;

    @Column(name = "monto_subtotal", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal montoSubtotal = BigDecimal.ZERO;

    @Column(name = "monto_descuento", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal montoDescuento = BigDecimal.ZERO;

    @Column(name = "monto_neto", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal montoNeto = BigDecimal.ZERO;

    @Column(name = "monto_iva", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal montoIva = BigDecimal.ZERO;

    @Column(name = "monto_total", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal montoTotal = BigDecimal.ZERO;

    @Column(name = "condiciones_comerciales", length = 500)
    private String condicionesComerciales;

    @Column(length = 500)
    private String observaciones;

    // Motivo de rechazo o cancelación.
    @Column(length = 500)
    private String motivo;

    @Column(name = "fecha_creacion", nullable = false)
    @Builder.Default
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @OneToMany(mappedBy = "cotizacion", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CotizacionDetalle> detalle = new ArrayList<>();
}
