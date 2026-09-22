package cl.slimerp.notasventa;

import cl.slimerp.common.PaginaResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/notas-venta")
public class NotaVentaController {

    private final NotaVentaService notaVentaService;
    private final NotaVentaPdfService notaVentaPdfService;

    public NotaVentaController(NotaVentaService notaVentaService, NotaVentaPdfService notaVentaPdfService) {
        this.notaVentaService = notaVentaService;
        this.notaVentaPdfService = notaVentaPdfService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('NOTAS_VENTA_VER')")
    public PaginaResponse<NotaVentaService.NotaVentaResumen> listar(
            @RequestParam(required = false) EstadoNotaVenta estado,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) Long vendedorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) OrigenNotaVenta origen,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String dir,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano) {
        return notaVentaService.buscar(estado, clienteId, vendedorId, desde, hasta, origen, q, sort, dir,
                pagina, tamano);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('NOTAS_VENTA_VER')")
    public NotaVentaService.DashboardNotasVenta dashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return notaVentaService.dashboard(desde, hasta);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('NOTAS_VENTA_VER')")
    public NotaVentaService.NotaVentaCompleta obtener(@PathVariable Long id) {
        return notaVentaService.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('NOTAS_VENTA_EDITAR')")
    public NotaVentaService.NotaVentaCompleta crear(@Valid @RequestBody NotaVentaRequest request) {
        return notaVentaService.crear(request);
    }

    // Crear una nota de venta a partir de una cotización aceptada.
    @PostMapping("/desde-cotizacion/{cotizacionId}")
    @PreAuthorize("hasAuthority('NOTAS_VENTA_EDITAR')")
    public NotaVentaService.NotaVentaCompleta crearDesdeCotizacion(@PathVariable Long cotizacionId) {
        return notaVentaService.crearDesdeCotizacion(cotizacionId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('NOTAS_VENTA_EDITAR')")
    public NotaVentaService.NotaVentaCompleta actualizar(@PathVariable Long id,
                                                          @Valid @RequestBody NotaVentaRequest request) {
        return notaVentaService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('NOTAS_VENTA_EDITAR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        notaVentaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/confirmar")
    @PreAuthorize("hasAuthority('NOTAS_VENTA_EDITAR')")
    public NotaVentaService.NotaVentaCompleta confirmar(@PathVariable Long id) {
        return notaVentaService.confirmar(id);
    }

    @PostMapping("/{id}/preparar")
    @PreAuthorize("hasAuthority('NOTAS_VENTA_EDITAR')")
    public NotaVentaService.NotaVentaCompleta preparar(@PathVariable Long id) {
        return notaVentaService.preparar(id);
    }

    @PostMapping("/{id}/entregas")
    @PreAuthorize("hasAuthority('NOTAS_VENTA_EDITAR')")
    public NotaVentaService.NotaVentaCompleta registrarEntrega(@PathVariable Long id,
                                                                @Valid @RequestBody EntregaRequest request) {
        return notaVentaService.registrarEntrega(id, request);
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasAuthority('NOTAS_VENTA_EDITAR')")
    public NotaVentaService.NotaVentaCompleta cancelar(@PathVariable Long id,
                                                        @RequestBody(required = false) MotivoRequest request) {
        return notaVentaService.cancelar(id, request != null ? request.motivo() : null);
    }

    @PostMapping("/{id}/duplicar")
    @PreAuthorize("hasAuthority('NOTAS_VENTA_EDITAR')")
    public NotaVentaService.NotaVentaCompleta duplicar(@PathVariable Long id) {
        return notaVentaService.duplicar(id);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('NOTAS_VENTA_VER')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        NotaVenta nota = notaVentaService.obtenerEntidad(id);
        byte[] pdf = notaVentaPdfService.generar(nota);
        String nombreArchivo = NumeroNotaVenta.formatear(nota.getFolio()) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + nombreArchivo)
                .body(pdf);
    }
}