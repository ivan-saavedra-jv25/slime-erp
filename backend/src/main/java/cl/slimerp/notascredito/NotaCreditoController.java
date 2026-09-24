package cl.slimerp.notascredito;

import cl.slimerp.common.PaginaResponse;
import cl.slimerp.ventas.TipoDocumentoVenta;
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
@RequestMapping("/api/notas-credito")
public class NotaCreditoController {

    private final NotaCreditoService notaCreditoService;
    private final NotaCreditoPdfService notaCreditoPdfService;

    public NotaCreditoController(NotaCreditoService notaCreditoService,
                                 NotaCreditoPdfService notaCreditoPdfService) {
        this.notaCreditoService = notaCreditoService;
        this.notaCreditoPdfService = notaCreditoPdfService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('NOTAS_CREDITO_VER')")
    public PaginaResponse<NotaCreditoService.NotaCreditoResumen> listar(
            @RequestParam(required = false) EstadoNotaCredito estado,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) TipoCorreccion tipoCorreccion,
            @RequestParam(required = false) Long ventaId,
            @RequestParam(required = false) TipoDocumentoVenta docAsociadoTipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String dir,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano) {
        return notaCreditoService.buscar(estado, clienteId, tipoCorreccion, ventaId, docAsociadoTipo,
                desde, hasta, q, sort, dir, pagina, tamano);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('NOTAS_CREDITO_VER')")
    public NotaCreditoService.DashboardNotasCredito dashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return notaCreditoService.dashboard(desde, hasta);
    }

    // Ventas que el usuario puede elegir como documento a corregir.
    @GetMapping("/documentos-asociables")
    @PreAuthorize("hasAuthority('NOTAS_CREDITO_VER')")
    public List<NotaCreditoService.DocumentoAsociable> documentosAsociables(
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) String q) {
        return notaCreditoService.documentosAsociables(clienteId, q);
    }

    // Líneas del documento original con la cantidad todavía disponible para
    // recuperar. Al editar un borrador se pasa su propio id en
    // excluyendoNotaCreditoId para que sus líneas no cuenten como ya recuperadas.
    @GetMapping("/ventas/{ventaId}/lineas")
    @PreAuthorize("hasAuthority('NOTAS_CREDITO_VER')")
    public List<NotaCreditoService.LineaDocumentoOriginal> lineasDocumento(
            @PathVariable Long ventaId,
            @RequestParam(required = false) Long excluyendoNotaCreditoId) {
        return notaCreditoService.lineasDocumento(ventaId, excluyendoNotaCreditoId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('NOTAS_CREDITO_VER')")
    public NotaCreditoService.NotaCreditoCompleta obtener(@PathVariable Long id) {
        return notaCreditoService.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('NOTAS_CREDITO_EDITAR')")
    public NotaCreditoService.NotaCreditoCompleta crear(@Valid @RequestBody NotaCreditoRequest request) {
        return notaCreditoService.crear(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('NOTAS_CREDITO_EDITAR')")
    public NotaCreditoService.NotaCreditoCompleta actualizar(@PathVariable Long id,
                                                             @Valid @RequestBody NotaCreditoRequest request) {
        return notaCreditoService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('NOTAS_CREDITO_EDITAR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        notaCreditoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/emitir")
    @PreAuthorize("hasAuthority('NOTAS_CREDITO_EDITAR')")
    public NotaCreditoService.NotaCreditoCompleta emitir(@PathVariable Long id) {
        return notaCreditoService.emitir(id);
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize("hasAuthority('NOTAS_CREDITO_EDITAR')")
    public NotaCreditoService.NotaCreditoCompleta anular(@PathVariable Long id,
                                                         @RequestBody(required = false) MotivoRequest request) {
        return notaCreditoService.anular(id, request != null ? request.motivo() : null);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('NOTAS_CREDITO_VER')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        NotaCredito nota = notaCreditoService.obtenerEntidad(id);
        byte[] pdf = notaCreditoPdfService.generar(nota);
        String nombreArchivo = NumeroNotaCredito.formatear(nota.getFolio()) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + nombreArchivo)
                .body(pdf);
    }
}
