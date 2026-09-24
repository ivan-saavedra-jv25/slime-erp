package cl.slimerp.notasdebito;

import cl.slimerp.common.PaginaResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/notas-debito")
public class NotaDebitoController {

    private final NotaDebitoService notaDebitoService;
    private final NotaDebitoPdfService notaDebitoPdfService;

    public NotaDebitoController(NotaDebitoService notaDebitoService,
                                NotaDebitoPdfService notaDebitoPdfService) {
        this.notaDebitoService = notaDebitoService;
        this.notaDebitoPdfService = notaDebitoPdfService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('NOTAS_DEBITO_VER')")
    public PaginaResponse<NotaDebitoService.NotaDebitoResumen> listar(
            @RequestParam(required = false) EstadoNotaDebito estado,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) TipoReversion tipoReversion,
            @RequestParam(required = false) Long notaCreditoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String dir,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano) {
        return notaDebitoService.buscar(estado, clienteId, tipoReversion, notaCreditoId,
                desde, hasta, q, sort, dir, pagina, tamano);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('NOTAS_DEBITO_VER')")
    public NotaDebitoService.DashboardNotasDebito dashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return notaDebitoService.dashboard(desde, hasta);
    }

    // Notas de crédito EMITIDAS que el usuario puede elegir para revertir.
    @GetMapping("/notas-credito-asociables")
    @PreAuthorize("hasAuthority('NOTAS_DEBITO_VER')")
    public List<NotaDebitoService.DocumentoNotaCreditoAsociable> notasCreditoAsociables(
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) String q) {
        return notaDebitoService.notasCreditoAsociables(clienteId, q);
    }

    // Líneas de la nota de crédito con la cantidad todavía disponible para
    // revertir. Al editar un borrador se pasa su propio id en
    // excluyendoNotaDebitoId para que sus líneas no cuenten como ya revertidas.
    @GetMapping("/notas-credito/{notaCreditoId}/lineas")
    @PreAuthorize("hasAuthority('NOTAS_DEBITO_VER')")
    public List<NotaDebitoService.LineaNotaCreditoOriginal> lineasNotaCredito(
            @PathVariable Long notaCreditoId,
            @RequestParam(required = false) Long excluyendoNotaDebitoId) {
        return notaDebitoService.lineasNotaCredito(notaCreditoId, excluyendoNotaDebitoId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('NOTAS_DEBITO_VER')")
    public NotaDebitoService.NotaDebitoCompleta obtener(@PathVariable Long id) {
        return notaDebitoService.obtener(id);
    }

    // Toda la línea de documentos asociados (Cotización -> Nota de Venta ->
    // Venta -> Nota de Crédito), para la trazabilidad del detalle.
    @GetMapping("/{id}/cadena")
    @PreAuthorize("hasAuthority('NOTAS_DEBITO_VER')")
    public List<NotaDebitoService.EslabonCadena> cadena(@PathVariable Long id) {
        return notaDebitoService.cadenaDe(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('NOTAS_DEBITO_EDITAR')")
    public NotaDebitoService.NotaDebitoCompleta crear(@Valid @RequestBody NotaDebitoRequest request) {
        return notaDebitoService.crear(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('NOTAS_DEBITO_EDITAR')")
    public NotaDebitoService.NotaDebitoCompleta actualizar(@PathVariable Long id,
                                                           @Valid @RequestBody NotaDebitoRequest request) {
        return notaDebitoService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('NOTAS_DEBITO_EDITAR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        notaDebitoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/emitir")
    @PreAuthorize("hasAuthority('NOTAS_DEBITO_EDITAR')")
    public NotaDebitoService.NotaDebitoCompleta emitir(@PathVariable Long id) {
        return notaDebitoService.emitir(id);
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize("hasAuthority('NOTAS_DEBITO_EDITAR')")
    public NotaDebitoService.NotaDebitoCompleta anular(@PathVariable Long id,
                                                       @RequestBody(required = false) MotivoRequest request) {
        return notaDebitoService.anular(id, request != null ? request.motivo() : null);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('NOTAS_DEBITO_VER')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        NotaDebito nota = notaDebitoService.obtenerEntidad(id);
        byte[] pdf = notaDebitoPdfService.generar(nota);
        String nombreArchivo = NumeroNotaDebito.formatear(nota.getFolio()) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + nombreArchivo)
                .body(pdf);
    }
}