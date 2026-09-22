package cl.slimerp.cotizaciones;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Vínculo entre una cotización y un documento posterior de la cadena comercial
// (Nota de Venta, Guía, Factura, Pago). tipoDocumento es texto libre en vez de
// un enum para no tener que tocar esta entidad cada vez que aparezca un módulo
// nuevo aguas abajo.
@Entity
@Table(name = "cotizacion_documento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CotizacionDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "cotizacion_id", nullable = false)
    private Long cotizacionId;

    @Column(name = "tipo_documento", nullable = false, length = 30)
    private String tipoDocumento;

    @Column(name = "documento_id", nullable = false)
    private Long documentoId;

    @Column(length = 50)
    private String numero;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime fecha = LocalDateTime.now();
}
