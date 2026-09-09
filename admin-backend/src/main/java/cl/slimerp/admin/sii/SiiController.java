package cl.slimerp.admin.sii;

import cl.slimerp.admin.common.Paginated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Estado de configuración SII por empresa (spec §15, solo consulta). */
@RestController
@RequestMapping("/api/admin/sii")
public class SiiController {

    private final SiiService siiService;

    public SiiController(SiiService siiService) {
        this.siiService = siiService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SII_VER')")
    public Paginated<SiiEstadoResponse> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String estado) {
        return siiService.listar(page, limit, estado);
    }

    @GetMapping("/empresas/{id}")
    @PreAuthorize("hasAuthority('SII_VER')")
    public SiiEstadoResponse empresa(@PathVariable Long id) {
        return siiService.empresa(id);
    }
}