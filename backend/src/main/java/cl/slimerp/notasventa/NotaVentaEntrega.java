package cl.slimerp.notasventa;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Cabecera de un avance de entrega: una entrega parcial (o total) de la nota de
// venta. La suma de sus líneas incrementa cantidad_entregada del detalle. Será
// reemplazado por el documento formal de Guía de Despacho en el futuro.
@Entity
@Table(name = "nota_venta_entrega")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotaVentaEntrega {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "nota_venta_id", nullable = false)
    private Long notaVentaId;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime fecha = LocalDateTime.now();

    @Column(length = 500)
    private String observacion;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @OneToMany(mappedBy = "entrega", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<NotaVentaEntregaLinea> lineas = new ArrayList<>();
}