package cl.slimerp.tesoreria;

import cl.slimerp.config.TenantContext;
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tesoreria/cuentas")
public class CuentaPorCobrarController {

    private final CuentaPorCobrarService cuentaPorCobrarService;
    private final UsuarioRepository usuarioRepository;

    public CuentaPorCobrarController(CuentaPorCobrarService cuentaPorCobrarService, UsuarioRepository usuarioRepository) {
        this.cuentaPorCobrarService = cuentaPorCobrarService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TESORERIA_VER')")
    public List<CuentaPorCobrar> listar(@RequestParam(required = false) Long clienteId,
                                         @RequestParam(required = false) EstadoCuentaPorCobrar estado) {
        return cuentaPorCobrarService.listar(clienteId, estado);
    }

    @GetMapping("/resumen")
    @PreAuthorize("hasAuthority('TESORERIA_VER')")
    public ResumenTesoreria resumen() {
        return cuentaPorCobrarService.resumen();
    }

    @GetMapping("/venta/{ventaId}")
    @PreAuthorize("hasAuthority('TESORERIA_VER')")
    public CuentaPorCobrar obtenerPorVenta(@PathVariable Long ventaId) {
        return cuentaPorCobrarService.obtenerPorVenta(ventaId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TESORERIA_VER')")
    public CuentaPorCobrar obtener(@PathVariable Long id) {
        return cuentaPorCobrarService.obtener(id);
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize("hasAuthority('TESORERIA_ANULAR')")
    public CuentaPorCobrar anular(@PathVariable Long id, @Valid @RequestBody AnularRequest request) {
        Long usuarioId = resolveUsuarioId(TenantContext.getTenantId());
        return cuentaPorCobrarService.anular(id, request.motivo(), usuarioId);
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
