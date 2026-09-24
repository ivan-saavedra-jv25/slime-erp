package cl.slimerp.notasdebito;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// El id no viaja nunca en el request: lo genera la base de datos.
//
// items no lleva @NotEmpty a propósito — una nota de débito de tipo
// REVIERTE_TEXTO va sin líneas. Que sean obligatorias según el tipo de reversión
// se valida en NotaDebitoService, junto con el resto de las reglas de negocio.
public record NotaDebitoRequest(
        @NotNull Long notaCreditoId,
        @NotNull TipoReversion tipoReversion,
        @NotNull LocalDate fecha,
        @NotBlank String ncRazon,
        String motivo,
        String observaciones,
        String textoCorreccion,
        BigDecimal descuento,
        @Valid List<Item> items) {

    public record Item(
            @NotNull Long productoId,
            // Línea de la nota de crédito que se revierte. Nulo cuando la
            // reversión se asocia a algo que no está en la NC; esas líneas no
            // pueden revertir inventario porque no hay cantidad contra la que
            // validar.
            Long notaCreditoDetalleId,
            @NotNull BigDecimal cantidad,
            @NotNull BigDecimal precioUnitario,
            BigDecimal descuento,
            boolean revierteInventario) {
    }

    public List<Item> itemsOVacio() {
        return items != null ? items : List.of();
    }
}