package cl.slimerp.notasdebito;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "nota_debito_folio_contador")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotaDebitoFolioContador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "ultimo_folio", nullable = false)
    private Integer ultimoFolio;
}