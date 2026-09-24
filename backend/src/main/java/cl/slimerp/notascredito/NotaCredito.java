package cl.slimerp.notascredito;

import cl.slimerp.ventas.TipoDocumentoVenta;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "nota_credito")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotaCredito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private Integer folio;

    // Se copia de la venta asociada: la nota de crédito siempre es del mismo
    // cliente que el documento que corrige.
    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoNotaCredito estado = EstadoNotaCredito.BORRADOR;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_correccion", nullable = false, length = 20)
    private TipoCorreccion tipoCorreccion;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(length = 500)
    private String motivo;

    @Column(length = 500)
    private String observaciones;

    // Solo se usa cuando tipoCorreccion es CORRIGE_TEXTO.
    @Column(name = "texto_correccion", length = 2000)
    private String textoCorreccion;

    // Bodega a la que vuelve la mercadería: la misma desde la que salió al
    // emitirse la venta.
    @Column(name = "bodega_id")
    private Long bodegaId;

    @Column(nullable = false)
    @Builder.Default
    private boolean exenta = false;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String moneda = "CLP";

    // --- Documento asociado -------------------------------------------------
    // venta_id es la FK dura hacia el documento vivo; los campos doc_asociado_*
    // son un snapshot del momento de creación, porque la nota de crédito es un
    // documento histórico y debe seguir mostrando qué se corrigió aunque la
    // venta cambie después.

    @Column(name = "venta_id", nullable = false)
    private Long ventaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_asociado_tipo", nullable = false, length = 20)
    private TipoDocumentoVenta docAsociadoTipo;

    @Column(name = "doc_asociado_folio")
    private Integer docAsociadoFolio;

    @Column(name = "doc_asociado_fecha")
    private LocalDate docAsociadoFecha;

    @Column(name = "doc_asociado_razon", length = 500)
    private String docAsociadoRazon;

    // --- Montos -------------------------------------------------------------
    // Siempre positivos: el signo semántico lo da el tipo de documento, una nota
    // de crédito por definición resta.

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

    @OneToMany(mappedBy = "notaCredito", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<NotaCreditoDetalle> detalle = new ArrayList<>();
}
