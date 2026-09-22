package cl.slimerp.cotizaciones;

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
@RequestMapping("/api/cotizaciones")
public class CotizacionController {

    private final CotizacionService cotizacionService;
    private final CotizacionPdfService cotizacionPdfService;

    public CotizacionController(CotizacionService cotizacionService, CotizacionPdfService cotizacionPdfService) {
        this.cotizacionService = cotizacionService;
        this.cotizacionPdfService = cotizacionPdfService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('COTIZACIONES_VER')")
    public PaginaResponse<CotizacionService.CotizacionResumen> listar(
            @RequestParam(required = false) EstadoCotizacion estado,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) Long vendedorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String dir,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano) {
        return cotizacionService.buscar(estado, clienteId, vendedorId, desde, hasta, q, sort, dir, pagina, tamano);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('COTIZACIONES_VER')")
    public CotizacionService.DashboardCotizaciones dashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return cotizacionService.dashboard(desde, hasta);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('COTIZACIONES_VER')")
    public CotizacionService.CotizacionCompleta obtener(@PathVariable Long id) {
        return cotizacionService.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('COTIZACIONES_EDITAR')")
    public CotizacionService.CotizacionCompleta crear(@Valid @RequestBody CotizacionRequest request) {
        return cotizacionService.crear(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('COTIZACIONES_EDITAR')")
    public CotizacionService.CotizacionCompleta actualizar(@PathVariable Long id,
                                                            @Valid @RequestBody CotizacionRequest request) {
        return cotizacionService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('COTIZACIONES_EDITAR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        cotizacionService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/enviar")
    @PreAuthorize("hasAuthority('COTIZACIONES_EDITAR')")
    public CotizacionService.CotizacionCompleta enviar(@PathVariable Long id) {
        return cotizacionService.enviar(id);
    }

    @PostMapping("/{id}/aceptar")
    @PreAuthorize("hasAuthority('COTIZACIONES_EDITAR')")
    public CotizacionService.CotizacionCompleta aceptar(@PathVariable Long id) {
        return cotizacionService.aceptar(id);
    }

    @PostMapping("/{id}/rechazar")
    @PreAuthorize("hasAuthority('COTIZACIONES_EDITAR')")
    public CotizacionService.CotizacionCompleta rechazar(@PathVariable Long id,
                                                          @RequestBody(required = false) MotivoRequest request) {
        return cotizacionService.rechazar(id, request != null ? request.motivo() : null);
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasAuthority('COTIZACIONES_EDITAR')")
    public CotizacionService.CotizacionCompleta cancelar(@PathVariable Long id,
                                                          @RequestBody(required = false) MotivoRequest request) {
        return cotizacionService.cancelar(id, request != null ? request.motivo() : null);
    }

    @PostMapping("/{id}/duplicar")
    @PreAuthorize("hasAuthority('COTIZACIONES_EDITAR')")
    public CotizacionService.CotizacionCompleta duplicar(@PathVariable Long id) {
        return cotizacionService.duplicar(id);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('COTIZACIONES_VER')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        Cotizacion cotizacion = cotizacionService.obtenerEntidad(id);
        byte[] pdf = cotizacionPdfService.generar(cotizacion);
        String nombreArchivo = NumeroCotizacion.formatear(cotizacion.getFolio()) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + nombreArchivo)
                .body(pdf);
    }
}
