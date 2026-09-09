package cl.slimerp.admin.dte;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Vista de solo lectura de la tabla `venta` del ERP (schema public). */
@Entity
@Immutable
@Table(name = "venta")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class VentaDte {

    @Id
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private ClienteDte cliente;

    @Column(name = "tipo_documento", nullable = false)
    private String tipoDocumento;

    @Column
    private boolean exento;

    @Column
    private Integer folio;

    @Column(name = "codigo_sii")
    private Integer codigoSii;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(name = "monto_total")
    private BigDecimal montoTotal;

    @Column(nullable = false)
    private boolean activo;
}