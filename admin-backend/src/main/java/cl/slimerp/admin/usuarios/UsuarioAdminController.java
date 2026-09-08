package cl.slimerp.admin.usuarios;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/usuarios")
@PreAuthorize("hasAuthority('PERM_USUARIOS_ADMIN_VER')")
public class UsuarioAdminController {

    private final UsuarioAdminService usuarioAdminService;

    public UsuarioAdminController(UsuarioAdminService usuarioAdminService) {
        this.usuarioAdminService = usuarioAdminService;
    }

    @GetMapping
    public List<UsuarioAdminResponse> listar(@RequestParam(required = false) Long tenantId,
                                             @RequestParam(required = false) Boolean activo) {
        return usuarioAdminService.listar(tenantId, activo);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_USUARIOS_ADMIN_EDITAR')")
    public ResponseEntity<UsuarioAdminResponse> crear(@Valid @RequestBody UsuarioAdminRequest request) {
        return ResponseEntity.ok(usuarioAdminService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_USUARIOS_ADMIN_EDITAR')")
    public ResponseEntity<UsuarioAdminResponse> actualizar(@PathVariable Long id,
                                                           @Valid @RequestBody UsuarioAdminRequest request) {
        return ResponseEntity.ok(usuarioAdminService.actualizar(id, request));
    }

    @PatchMapping("/{id}/activar")
    @PreAuthorize("hasAuthority('PERM_USUARIOS_ADMIN_EDITAR')")
    public UsuarioAdminResponse activar(@PathVariable Long id,
                                        @RequestBody(required = false) MotivoRequest motivo) {
        return usuarioAdminService.cambiarEstado(id, true, motivo == null ? null : motivo.motivo());
    }

    @PatchMapping("/{id}/desactivar")
    @PreAuthorize("hasAuthority('PERM_USUARIOS_ADMIN_EDITAR')")
    public UsuarioAdminResponse desactivar(@PathVariable Long id,
                                           @RequestBody(required = false) MotivoRequest motivo) {
        return usuarioAdminService.cambiarEstado(id, false, motivo == null ? null : motivo.motivo());
    }

    @PostMapping("/{id}/bloquear")
    @PreAuthorize("hasAuthority('PERM_USUARIOS_ADMIN_EDITAR')")
    public UsuarioAdminResponse bloquear(@PathVariable Long id,
                                         @Valid @RequestBody UsuarioAdminBloquearRequest request) {
        return usuarioAdminService.bloquear(id, request.motivo());
    }

    @PostMapping("/{id}/revocar-sesiones")
    @PreAuthorize("hasAuthority('PERM_USUARIOS_ADMIN_EDITAR')")
    public ResponseEntity<Void> revocarSesiones(@PathVariable Long id,
                                                @Valid @RequestBody RevocarSesionesRequest request) {
        usuarioAdminService.revocarSesiones(id, request.motivo());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/password")
    @PreAuthorize("hasAuthority('PERM_USUARIOS_ADMIN_EDITAR')")
    public ResponseEntity<Void> resetearPassword(@PathVariable Long id,
                                                 @Valid @RequestBody ResetPasswordRequest request) {
        usuarioAdminService.resetearPassword(id, request.password());
        return ResponseEntity.noContent().build();
    }
}