package cl.slimerp.notascredito;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// El id no viaja nunca en el request: lo genera la base de datos.
//
// items no lleva @NotEmpty a propósito — una nota de crédito de tipo
// CORRIGE_TEXTO va sin líneas. Que sean obligatorias según el tipo de corrección
// se valida en NotaCreditoService, junto con el resto de las reglas de negocio.
public record NotaCreditoRequest(
        @NotNull Long ventaId,
        @NotNull TipoCorreccion tipoCorreccion,
        @NotNull LocalDate fecha,
        @NotBlank String docAsociadoRazon,
        String motivo,
        String observaciones,
        String textoCorreccion,
        BigDecimal descuento,
        @Valid List<Item> items) {

    public record Item(
            @NotNull Long productoId,
            // Línea del documento original que se corrige. Nulo cuando la
            // corrección agrega algo que no estaba en él; esas líneas no pueden
            // recuperar inventario.
            Long ventaDetalleId,
            @NotNull BigDecimal cantidad,
            @NotNull BigDecimal precioUnitario,
            BigDecimal descuento,
            boolean recuperaInventario) {
    }

    public List<Item> itemsOVacio() {
        return items != null ? items : List.of();
    }
}
