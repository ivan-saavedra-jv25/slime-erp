package cl.slimerp.ventas;

import cl.slimerp.config.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ventas")
public class VentaController {

    private final VentaRepository ventaRepository;
    private final VentaService ventaService;
    private final VentaPdfService ventaPdfService;

    public VentaController(VentaRepository ventaRepository, VentaService ventaService, VentaPdfService ventaPdfService) {
        this.ventaRepository = ventaRepository;
        this.ventaService = ventaService;
        this.ventaPdfService = ventaPdfService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VENTAS_VER')")
    public List<Venta> listar() {
        return ventaRepository.findByTenantIdAndActivoTrueOrderByFechaDesc(TenantContext.getTenantId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VENTAS_VER')")
    public ResponseEntity<Venta> obtener(@PathVariable Long id) {
        return ventaRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('VENTAS_EDITAR')")
    public ResponseEntity<Venta> crear(@Valid @RequestBody VentaRequest request) {
        return ResponseEntity.ok(ventaService.crear(request));
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('VENTAS_VER')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        Venta venta = ventaRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada: " + id));
        byte[] pdf = ventaPdfService.generar(venta);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=venta-" + id + ".pdf")
                .body(pdf);
    }
}
