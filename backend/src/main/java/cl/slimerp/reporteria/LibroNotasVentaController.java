package cl.slimerp.reporteria;

import cl.slimerp.config.TenantContext;
import cl.slimerp.notasventa.EstadoNotaVenta;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reportes")
public class LibroNotasVentaController {

    private final LibroNotasVentaService libroNotasVentaService;
    private final LibroNotasVentaExcelService libroNotasVentaExcelService;

    public LibroNotasVentaController(LibroNotasVentaService libroNotasVentaService,
                                     LibroNotasVentaExcelService libroNotasVentaExcelService) {
        this.libroNotasVentaService = libroNotasVentaService;
        this.libroNotasVentaExcelService = libroNotasVentaExcelService;
    }

    @GetMapping("/libro-notas-venta")
    @PreAuthorize("hasAuthority('NOTAS_VENTA_VER')")
    public LibroNotasVentaService.LibroNotasVentaResponse libroNotasVenta(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) EstadoNotaVenta estado) {
        return libroNotasVentaService.generar(TenantContext.getTenantId(), desde, hasta, estado);
    }

    @GetMapping("/libro-notas-venta/excel")
    @PreAuthorize("hasAuthority('NOTAS_VENTA_VER')")
    public ResponseEntity<byte[]> libroNotasVentaExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) EstadoNotaVenta estado) {
        LibroNotasVentaService.LibroNotasVentaResponse libro =
                libroNotasVentaService.generar(TenantContext.getTenantId(), desde, hasta, estado);
        byte[] excel = libroNotasVentaExcelService.generar(libro);
        String nombreArchivo = "libro-notas-venta-" + desde + "-a-" + hasta + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + nombreArchivo)
                .body(excel);
    }
}