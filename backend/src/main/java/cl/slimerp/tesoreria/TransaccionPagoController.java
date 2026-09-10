package cl.slimerp.tesoreria;

import cl.slimerp.common.PaginaResponse;
import cl.slimerp.config.TenantContext;
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
public class TransaccionPagoController {

    private final TransaccionPagoService transaccionPagoService;
    private final UsuarioRepository usuarioRepository;

    public TransaccionPagoController(TransaccionPagoService transaccionPagoService, UsuarioRepository usuarioRepository) {
        this.transaccionPagoService = transaccionPagoService;
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping("/api/tesoreria/cuentas/{cuentaId}/pagos")
    @PreAuthorize("hasAuthority('TESORERIA_EDITAR')")
    public TransaccionPago registrarPago(@PathVariable Long cuentaId, @Valid @RequestBody TransaccionPagoRequest request) {
        Long usuarioId = resolveUsuarioId(TenantContext.getTenantId());
        return transaccionPagoService.registrarPago(cuentaId, request, usuarioId);
    }

    @GetMapping("/api/tesoreria/cuentas/{cuentaId}/pagos")
    @PreAuthorize("hasAuthority('TESORERIA_VER')")
    public List<TransaccionPago> listarPorCuenta(@PathVariable Long cuentaId) {
        return transaccionPagoService.listarPorCuenta(cuentaId);
    }

    @GetMapping("/api/tesoreria/pagos")
    @PreAuthorize("hasAuthority('TESORERIA_VER')")
    public PaginaResponse<TransaccionPago> buscar(
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) EstadoTransaccion estado,
            @RequestParam(required = false) MedioPago medioPago,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaHasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano) {
        return transaccionPagoService.buscar(busqueda, estado, medioPago, fechaDesde, fechaHasta, pagina, tamano);
    }

    @GetMapping("/api/tesoreria/pagos/{id}")
    @PreAuthorize("hasAuthority('TESORERIA_VER')")
    public TransaccionPago obtener(@PathVariable Long id) {
        return transaccionPagoService.obtener(id);
    }

    @PostMapping("/api/tesoreria/pagos/{id}/anular")
    @PreAuthorize("hasAuthority('TESORERIA_ANULAR')")
    public TransaccionPago anular(@PathVariable Long id, @Valid @RequestBody AnularRequest request) {
        Long usuarioId = resolveUsuarioId(TenantContext.getTenantId());
        return transaccionPagoService.anular(id, request.motivo(), usuarioId);
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
