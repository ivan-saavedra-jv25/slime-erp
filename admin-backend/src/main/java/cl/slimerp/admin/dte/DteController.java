package cl.slimerp.admin.dte;

import cl.slimerp.admin.common.Paginated;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Monitoreo global de DTE del ERP (spec §16, solo consultas). */
@RestController
@RequestMapping("/api/admin/dte")
public class DteController {

    private final DteService dteService;

    public DteController(DteService dteService) {
        this.dteService = dteService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('DTE_VER')")
    public Paginated<DteResponse> listar(
            @RequestParam(required = false) Long empresaId,
            @RequestParam(required = false) String tipoDte,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) Integer folio,
            @RequestParam(required = false) String rutReceptor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return dteService.listar(page, limit, empresaId, tipoDte, estado,
                fechaDesde, fechaHasta, folio, rutReceptor);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('DTE_VER')")
    public DteDashboardResponse dashboard(
            @RequestParam(required = false) Long empresaId,
            @RequestParam(required = false) String tipoDte,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {
        return dteService.dashboard(empresaId, tipoDte, fechaDesde, fechaHasta);
    }
}