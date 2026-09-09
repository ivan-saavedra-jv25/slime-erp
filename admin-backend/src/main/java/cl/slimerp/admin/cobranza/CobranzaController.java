package cl.slimerp.admin.cobranza;

import cl.slimerp.admin.config.UsuarioActual;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/cobranza")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class CobranzaController {

    private final CobranzaService cobranzaService;
    private final UsuarioActual usuarioActual;

    public CobranzaController(CobranzaService cobranzaService, UsuarioActual usuarioActual) {
        this.cobranzaService = cobranzaService;
        this.usuarioActual = usuarioActual;
    }

    @GetMapping
    public List<CobranzaResponse> listar(
            @RequestParam(required = false) Long empresaId,
            @RequestParam(required = false) EstadoCobranza estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaHasta) {
        return cobranzaService.listar(empresaId, estado, fechaDesde, fechaHasta)
                .stream()
                .map(CobranzaResponse::desde)
                .toList();
    }

    @GetMapping("/resumen")
    public ResumenCobranza resumen() {
        return cobranzaService.resumen();
    }

    @GetMapping("/{id}")
    public CobranzaResponse obtener(@PathVariable Long id) {
        return CobranzaResponse.desde(cobranzaService.obtener(id));
    }

    @PostMapping
    public ResponseEntity<CobranzaResponse> emitir(@Valid @RequestBody EmitirCobranzaRequest request) {
        return ResponseEntity.ok(CobranzaResponse.desde(cobranzaService.emitir(request, usuarioActual.id())));
    }

    @GetMapping("/{id}/pagos")
    public List<CobranzaPago> listarPagos(@PathVariable Long id) {
        return cobranzaService.listarPagos(id);
    }

    @PostMapping("/{id}/pagos")
    public ResponseEntity<CobranzaPago> registrarPago(@PathVariable Long id,
                                                      @Valid @RequestBody PagoCobranzaRequest request) {
        return ResponseEntity.ok(cobranzaService.registrarPago(id, request, usuarioActual.id()));
    }

    @PostMapping("/pagos/{pagoId}/anular")
    public ResponseEntity<CobranzaPago> anularPago(@PathVariable Long pagoId,
                                                   @Valid @RequestBody AnularRequest request) {
        return ResponseEntity.ok(cobranzaService.anularPago(pagoId, request.motivo(), usuarioActual.id()));
    }

    @PostMapping("/{id}/anular")
    public ResponseEntity<CobranzaResponse> anular(@PathVariable Long id,
                                                   @Valid @RequestBody AnularRequest request) {
        return ResponseEntity.ok(CobranzaResponse.desde(cobranzaService.anular(id, request.motivo(), usuarioActual.id())));
    }
}