package cl.slimerp.cotizaciones;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cotizacion_folio_contador")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CotizacionFolioContador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "ultimo_folio", nullable = false)
    private Integer ultimoFolio;
}
