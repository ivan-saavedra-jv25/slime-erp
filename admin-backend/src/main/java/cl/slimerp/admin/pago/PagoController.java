package cl.slimerp.admin.pago;

import cl.slimerp.admin.common.Paginated;
import cl.slimerp.admin.config.UsuarioActual;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/pagos")
@PreAuthorize("hasAuthority('PERM_PAGOS_VER')")
public class PagoController {

    private final PagoService pagoService;
    private final UsuarioActual usuarioActual;

    public PagoController(PagoService pagoService, UsuarioActual usuarioActual) {
        this.pagoService = pagoService;
        this.usuarioActual = usuarioActual;
    }

    @GetMapping
    public Paginated<PagoResponse> listar(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int limit,
                                          @RequestParam(required = false) Long empresaId,
                                          @RequestParam(required = false) String estado) {
        return pagoService.listar(page, limit, empresaId, estado);
    }

    @GetMapping("/{id}")
    public PagoResponse obtener(@PathVariable Long id) {
        return pagoService.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_PAGOS_EDITAR')")
    public PagoResponse registrar(@Valid @RequestBody RegistrarPagoManualRequest request) {
        return pagoService.registrarManual(request, usuarioActual.id());
    }
}