package cl.slimerp.ventas;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "folio_venta_contador")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FolioVentaContador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 20)
    private String clave;

    @Column(name = "ultimo_folio", nullable = false)
    private Integer ultimoFolio;
}
