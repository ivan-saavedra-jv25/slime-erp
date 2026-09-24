package cl.slimerp.notasdebito;

import cl.slimerp.ventas.TipoDocumentoVenta;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "nota_debito")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotaDebito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private Integer folio;

    // Se copia de la nota de crédito asociada: la nota de débito siempre es del
    // mismo cliente que el documento que revierte.
    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoNotaDebito estado = EstadoNotaDebito.BORRADOR;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_reversion", nullable = false, length = 20)
    private TipoReversion tipoReversion;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(length = 500)
    private String motivo;

    @Column(length = 500)
    private String observaciones;

    // Solo se usa cuando tipoReversion es REVIERTE_TEXTO.
    @Column(name = "texto_correccion", length = 2000)
    private String textoCorreccion;

    // Bodega de la mercadería que la NC recuperó y que esta ND devuelve: la
    // misma de la venta original, heredada de la NC.
    @Column(name = "bodega_id")
    private Long bodegaId;

    @Column(nullable = false)
    @Builder.Default
    private boolean exenta = false;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String moneda = "CLP";

    // --- Documento asociado -------------------------------------------------
    // nota_credito_id es la FK dura hacia el documento vivo; los campos nc_*
    // son un snapshot del momento de creación, porque la cadena
    // Venta -> NC -> ND es histórica y debe seguir mostrando qué se revirtió
    // aunque la NC o la venta cambien después.

    @Column(name = "nota_credito_id", nullable = false)
    private Long notaCreditoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "nc_doc_asociado_tipo", nullable = false, length = 20)
    private TipoDocumentoVenta ncDocAsociadoTipo;

    @Column(name = "nc_folio")
    private Integer ncFolio;

    @Column(name = "nc_fecha")
    private LocalDate ncFecha;

    @Column(name = "nc_monto_total", precision = 14, scale = 2)
    private BigDecimal ncMontoTotal;

    @Column(name = "nc_razon", length = 500)
    private String ncRazon;

    // --- Montos -------------------------------------------------------------
    // Siempre positivos: el signo semántico lo da el tipo de documento, una nota
    // de débito por definición revierte una nota de crédito.

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

    @Column(name = "fecha_emision")
    private LocalDateTime fechaEmision;

    @Column(name = "fecha_anulacion")
    private LocalDateTime fechaAnulacion;

    @Column(name = "fecha_creacion", nullable = false)
    @Builder.Default
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @OneToMany(mappedBy = "notaDebito", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<NotaDebitoDetalle> detalle = new ArrayList<>();
}