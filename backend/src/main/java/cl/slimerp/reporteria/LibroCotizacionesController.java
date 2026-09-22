package cl.slimerp.reporteria;

import cl.slimerp.config.TenantContext;
import cl.slimerp.cotizaciones.EstadoCotizacion;
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
public class LibroCotizacionesController {

    private final LibroCotizacionesService libroCotizacionesService;
    private final LibroCotizacionesExcelService libroCotizacionesExcelService;

    public LibroCotizacionesController(LibroCotizacionesService libroCotizacionesService,
                                        LibroCotizacionesExcelService libroCotizacionesExcelService) {
        this.libroCotizacionesService = libroCotizacionesService;
        this.libroCotizacionesExcelService = libroCotizacionesExcelService;
    }

    @GetMapping("/libro-cotizaciones")
    @PreAuthorize("hasAuthority('COTIZACIONES_VER')")
    public LibroCotizacionesService.LibroCotizacionesResponse libroCotizaciones(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) EstadoCotizacion estado) {
        return libroCotizacionesService.generar(TenantContext.getTenantId(), desde, hasta, estado);
    }

    @GetMapping("/libro-cotizaciones/excel")
    @PreAuthorize("hasAuthority('COTIZACIONES_VER')")
    public ResponseEntity<byte[]> libroCotizacionesExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) EstadoCotizacion estado) {
        LibroCotizacionesService.LibroCotizacionesResponse libro =
                libroCotizacionesService.generar(TenantContext.getTenantId(), desde, hasta, estado);
        byte[] excel = libroCotizacionesExcelService.generar(libro);
        String nombreArchivo = "libro-cotizaciones-" + desde + "-a-" + hasta + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + nombreArchivo)
                .body(excel);
    }
}
