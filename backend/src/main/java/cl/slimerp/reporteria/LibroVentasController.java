package cl.slimerp.reporteria;

import cl.slimerp.config.TenantContext;
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
public class LibroVentasController {

    private final LibroVentasService libroVentasService;
    private final LibroVentasExcelService libroVentasExcelService;

    public LibroVentasController(LibroVentasService libroVentasService, LibroVentasExcelService libroVentasExcelService) {
        this.libroVentasService = libroVentasService;
        this.libroVentasExcelService = libroVentasExcelService;
    }

    @GetMapping("/libro-ventas")
    @PreAuthorize("hasAuthority('VENTAS_VER')")
    public LibroVentasService.LibroVentasResponse libroVentas(
            @RequestParam LocalDate desde,
            @RequestParam LocalDate hasta) {
        return libroVentasService.generar(TenantContext.getTenantId(), desde, hasta);
    }

    @GetMapping("/libro-ventas/excel")
    @PreAuthorize("hasAuthority('VENTAS_VER')")
    public ResponseEntity<byte[]> libroVentasExcel(
            @RequestParam LocalDate desde,
            @RequestParam LocalDate hasta) {
        LibroVentasService.LibroVentasResponse libro = libroVentasService.generar(TenantContext.getTenantId(), desde, hasta);
        byte[] excel = libroVentasExcelService.generar(libro);
        String nombreArchivo = "libro-ventas-" + desde + "-a-" + hasta + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + nombreArchivo)
                .body(excel);
    }
}
