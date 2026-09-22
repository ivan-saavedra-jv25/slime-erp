package cl.slimerp.notasventa;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "nota_venta_folio_contador")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotaVentaFolioContador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "ultimo_folio", nullable = false)
    private Integer ultimoFolio;
}