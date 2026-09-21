package cl.slimerp.tesoreria;

import cl.slimerp.config.TenantContext;
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tesoreria/cuentas-por-pagar")
public class CuentaPorPagarController {

    private final CuentaPorPagarService cuentaPorPagarService;
    private final UsuarioRepository usuarioRepository;

    public CuentaPorPagarController(CuentaPorPagarService cuentaPorPagarService, UsuarioRepository usuarioRepository) {
        this.cuentaPorPagarService = cuentaPorPagarService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TESORERIA_VER')")
    public List<CuentaPorPagar> listar(@RequestParam(required = false) Long proveedorId,
                                        @RequestParam(required = false) Long categoriaGastoId,
                                        @RequestParam(required = false) EstadoCuentaPorPagar estado) {
        return cuentaPorPagarService.listar(proveedorId, categoriaGastoId, estado);
    }

    @GetMapping("/resumen")
    @PreAuthorize("hasAuthority('TESORERIA_VER')")
    public ResumenCuentasPorPagar resumen() {
        return cuentaPorPagarService.resumen();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TESORERIA_VER')")
    public CuentaPorPagar obtener(@PathVariable Long id) {
        return cuentaPorPagarService.obtener(id);
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize("hasAuthority('TESORERIA_ANULAR')")
    public CuentaPorPagar anular(@PathVariable Long id, @Valid @RequestBody AnularRequest request) {
        Long usuarioId = resolveUsuarioId(TenantContext.getTenantId());
        return cuentaPorPagarService.anular(id, request.motivo(), usuarioId);
    }

    private Long resolveUsuarioId(Long tenantId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth != null ? auth.getName() : null;
        if (email == null) throw new IllegalStateException("No hay usuario autenticado");
        return usuarioRepository.findByEmailAndTenantId(email, tenantId)
                .map(Usuario::getId)
                .orElseThrow(() -> new IllegalStateException("Usuario no encontrado: " + email));
    }
}
