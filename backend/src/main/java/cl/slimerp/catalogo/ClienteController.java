package cl.slimerp.catalogo;

import cl.slimerp.config.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteRepository clienteRepository;

    public ClienteController(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CLIENTES_VER')")
    public List<Cliente> listar() {
        return clienteRepository.findByTenantIdAndActivoTrue(TenantContext.getTenantId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CLIENTES_VER')")
    public ResponseEntity<Cliente> obtener(@PathVariable Long id) {
        return clienteRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CLIENTES_EDITAR')")
    public ResponseEntity<Cliente> crear(@Valid @RequestBody ClienteRequest request) {
        Cliente cliente = Cliente.builder()
                .tenantId(TenantContext.getTenantId())
                .nombre(request.nombre())
                .rut(request.rut())
                .email(request.email())
                .telefono(request.telefono())
                .direccion(request.direccion())
                .razonSocial(request.razonSocial())
                .giro(request.giro())
                .comuna(request.comuna())
                .ciudad(request.ciudad())
                .build();
        return ResponseEntity.ok(clienteRepository.save(cliente));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CLIENTES_EDITAR')")
    public ResponseEntity<Cliente> actualizar(@PathVariable Long id, @Valid @RequestBody ClienteRequest request) {
        return clienteRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(cliente -> {
                    cliente.setNombre(request.nombre());
                    cliente.setRut(request.rut());
                    cliente.setEmail(request.email());
                    cliente.setTelefono(request.telefono());
                    cliente.setDireccion(request.direccion());
                    cliente.setRazonSocial(request.razonSocial());
                    cliente.setGiro(request.giro());
                    cliente.setComuna(request.comuna());
                    cliente.setCiudad(request.ciudad());
                    return ResponseEntity.ok(clienteRepository.save(cliente));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CLIENTES_EDITAR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        return clienteRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(cliente -> {
                    cliente.setActivo(false);
                    clienteRepository.save(cliente);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
