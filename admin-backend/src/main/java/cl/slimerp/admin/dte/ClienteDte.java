package cl.slimerp.admin.dte;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Vista de solo lectura de la tabla `cliente` del ERP (schema public). */
@Entity
@Immutable
@Table(name = "cliente")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ClienteDte {

    @Id
    private Long id;

    @Column
    private String rut;

    @Column(name = "razon_social")
    private String razonSocial;
}