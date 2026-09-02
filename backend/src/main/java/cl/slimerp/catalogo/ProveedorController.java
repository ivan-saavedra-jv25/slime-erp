package cl.slimerp.catalogo;

import cl.slimerp.config.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/proveedores")
public class ProveedorController {

    private final ProveedorRepository proveedorRepository;

    public ProveedorController(ProveedorRepository proveedorRepository) {
        this.proveedorRepository = proveedorRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROVEEDORES_VER')")
    public List<Proveedor> listar() {
        return proveedorRepository.findByTenantIdAndActivoTrue(TenantContext.getTenantId());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PROVEEDORES_EDITAR')")
    public ResponseEntity<Proveedor> crear(@Valid @RequestBody ProveedorRequest request) {
        Proveedor proveedor = Proveedor.builder()
                .tenantId(TenantContext.getTenantId())
                .nombre(request.nombre())
                .rut(request.rut())
                .email(request.email())
                .telefono(request.telefono())
                .direccion(request.direccion())
                .build();
        return ResponseEntity.ok(proveedorRepository.save(proveedor));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PROVEEDORES_EDITAR')")
    public ResponseEntity<Proveedor> actualizar(@PathVariable Long id, @Valid @RequestBody ProveedorRequest request) {
        return proveedorRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(proveedor -> {
                    proveedor.setNombre(request.nombre());
                    proveedor.setRut(request.rut());
                    proveedor.setEmail(request.email());
                    proveedor.setTelefono(request.telefono());
                    proveedor.setDireccion(request.direccion());
                    return ResponseEntity.ok(proveedorRepository.save(proveedor));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PROVEEDORES_EDITAR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        return proveedorRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(proveedor -> {
                    proveedor.setActivo(false);
                    proveedorRepository.save(proveedor);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
