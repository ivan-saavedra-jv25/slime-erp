package cl.slimerp.inventario;

import cl.slimerp.common.PaginaResponse;
import cl.slimerp.config.TenantContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventario")
public class InventarioConsultaController {

    private final InventarioConsultaService service;
    private final InventarioExportService exportService;

    public InventarioConsultaController(InventarioConsultaService service, InventarioExportService exportService) {
        this.service = service;
        this.exportService = exportService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('BODEGAS_VER')")
    public PaginaResponse<InventarioConsultaItem> listar(
            @RequestParam(required = false) Long bodegaId,
            @RequestParam(required = false) Long familiaId,
            @RequestParam(required = false) Long subfamiliaId,
            @RequestParam(defaultValue = "false") boolean verDeshabilitados,
            @RequestParam(required = false) TipoBusquedaInventario tipoBusqueda,
            @RequestParam(required = false) String busqueda,
            @RequestParam(defaultValue = "nombre") String sort,
            @RequestParam(defaultValue = "asc") String dir,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano) {
        return service.consultar(TenantContext.getTenantId(), bodegaId, familiaId, subfamiliaId,
                verDeshabilitados, tipoBusqueda, busqueda, sort, dir, pagina, tamano);
    }

    @GetMapping("/exportar.csv")
    @PreAuthorize("hasAuthority('BODEGAS_VER')")
    public ResponseEntity<byte[]> exportarCsv(
            @RequestParam(required = false) Long bodegaId,
            @RequestParam(required = false) Long familiaId,
            @RequestParam(required = false) Long subfamiliaId,
            @RequestParam(defaultValue = "false") boolean verDeshabilitados,
            @RequestParam(required = false) TipoBusquedaInventario tipoBusqueda,
            @RequestParam(required = false) String busqueda) {
        List<InventarioConsultaItem> items = service.consultarTodo(TenantContext.getTenantId(), bodegaId,
                familiaId, subfamiliaId, verDeshabilitados, tipoBusqueda, busqueda);
        byte[] csv = exportService.generarCsv(items);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=inventario.csv")
                .body(csv);
    }

    @GetMapping("/exportar.xlsx")
    @PreAuthorize("hasAuthority('BODEGAS_VER')")
    public ResponseEntity<byte[]> exportarXlsx(
            @RequestParam(required = false) Long bodegaId,
            @RequestParam(required = false) Long familiaId,
            @RequestParam(required = false) Long subfamiliaId,
            @RequestParam(defaultValue = "false") boolean verDeshabilitados,
            @RequestParam(required = false) TipoBusquedaInventario tipoBusqueda,
            @RequestParam(required = false) String busqueda) {
        List<InventarioConsultaItem> items = service.consultarTodo(TenantContext.getTenantId(), bodegaId,
                familiaId, subfamiliaId, verDeshabilitados, tipoBusqueda, busqueda);
        byte[] xlsx = exportService.generarXlsx(items);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=inventario.xlsx")
                .body(xlsx);
    }
}
