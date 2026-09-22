package cl.slimerp.notasventa;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "nota_venta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotaVenta {

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
    private OrigenNotaVenta origen = OrigenNotaVenta.VENTA_DIRECTA;

    // Referencia a la cotización de origen cuando origen = COTIZACION. Puede ser
    // nula en ventas directas.
    @Column(name = "cotizacion_id")
    private Long cotizacionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private EstadoNotaVenta estado = EstadoNotaVenta.BORRADOR;

    @Column(nullable = false)
    @Builder.Default
    private boolean exenta = false;

    // Snapshot de la moneda del documento. El MVP opera solo en CLP; se guarda
    // para que el documento conserve su contexto si algún día hay multi-moneda.
    @Column(nullable = false, length = 10)
    @Builder.Default
    private String moneda = "CLP";

    @Column(name = "fecha_emision", nullable = false)
    private LocalDate fechaEmision;

    @Column(name = "fecha_entrega_estimada")
    private LocalDate fechaEntregaEstimada;

    @Column(name = "direccion_entrega", length = 255)
    private String direccionEntrega;

    @Column(name = "condiciones_venta", length = 500)
    private String condicionesVenta;

    @Column(length = 500)
    private String observaciones;

    // Motivo de cancelación.
    @Column(length = 500)
    private String motivo;

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

    @Column(name = "fecha_creacion", nullable = false)
    @Builder.Default
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @OneToMany(mappedBy = "notaVenta", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<NotaVentaDetalle> detalle = new ArrayList<>();
}