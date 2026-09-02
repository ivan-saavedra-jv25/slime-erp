package cl.slimerp.catalogo;

import cl.slimerp.config.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/formas-pago")
public class FormaPagoController {

    private final FormaPagoRepository formaPagoRepository;

    public FormaPagoController(FormaPagoRepository formaPagoRepository) {
        this.formaPagoRepository = formaPagoRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('FORMAS_PAGO_VER')")
    public List<FormaPago> listar() {
        return formaPagoRepository.findByTenantIdAndActivoTrue(TenantContext.getTenantId());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FORMAS_PAGO_EDITAR')")
    public ResponseEntity<FormaPago> crear(@Valid @RequestBody FormaPagoRequest request) {
        FormaPago formaPago = FormaPago.builder()
                .tenantId(TenantContext.getTenantId())
                .nombre(request.nombre())
                .categoria(request.categoria())
                .build();
        return ResponseEntity.ok(formaPagoRepository.save(formaPago));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('FORMAS_PAGO_EDITAR')")
    public ResponseEntity<FormaPago> actualizar(@PathVariable Long id, @Valid @RequestBody FormaPagoRequest request) {
        return formaPagoRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(formaPago -> {
                    formaPago.setNombre(request.nombre());
                    formaPago.setCategoria(request.categoria());
                    return ResponseEntity.ok(formaPagoRepository.save(formaPago));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('FORMAS_PAGO_EDITAR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        return formaPagoRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(formaPago -> {
                    formaPago.setActivo(false);
                    formaPagoRepository.save(formaPago);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
