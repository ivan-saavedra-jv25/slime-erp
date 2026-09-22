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
public class LibroComprasController {

    private final LibroComprasService libroComprasService;
    private final LibroComprasExcelService libroComprasExcelService;

    public LibroComprasController(LibroComprasService libroComprasService, LibroComprasExcelService libroComprasExcelService) {
        this.libroComprasService = libroComprasService;
        this.libroComprasExcelService = libroComprasExcelService;
    }

    @GetMapping("/libro-compras")
    @PreAuthorize("hasAuthority('COMPRAS_VER')")
    public LibroComprasService.LibroComprasResponse libroCompras(
            @RequestParam LocalDate desde,
            @RequestParam LocalDate hasta) {
        return libroComprasService.generar(TenantContext.getTenantId(), desde, hasta);
    }

    @GetMapping("/libro-compras/excel")
    @PreAuthorize("hasAuthority('COMPRAS_VER')")
    public ResponseEntity<byte[]> libroComprasExcel(
            @RequestParam LocalDate desde,
            @RequestParam LocalDate hasta) {
        LibroComprasService.LibroComprasResponse libro = libroComprasService.generar(TenantContext.getTenantId(), desde, hasta);
        byte[] excel = libroComprasExcelService.generar(libro);
        String nombreArchivo = "libro-compras-" + desde + "-a-" + hasta + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + nombreArchivo)
                .body(excel);
    }
}
