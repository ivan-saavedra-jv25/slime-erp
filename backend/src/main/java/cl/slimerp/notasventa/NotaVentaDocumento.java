package cl.slimerp.notasventa;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Vínculo entre una nota de venta y un documento posterior de la cadena
// comercial (Guía de Despacho, Factura, Pago). tipoDocumento es texto libre en
// vez de un enum para no tener que tocar esta entidad cada vez que aparezca un
// módulo nuevo aguas abajo.
@Entity
@Table(name = "nota_venta_documento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotaVentaDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "nota_venta_id", nullable = false)
    private Long notaVentaId;

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