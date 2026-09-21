package cl.slimerp.gastos;

import cl.slimerp.config.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/gastos/recurrentes")
public class GastoRecurrenteController {

    private final GastoRecurrenteRepository gastoRecurrenteRepository;
    private final CategoriaGastoRepository categoriaGastoRepository;

    public GastoRecurrenteController(GastoRecurrenteRepository gastoRecurrenteRepository,
                                      CategoriaGastoRepository categoriaGastoRepository) {
        this.gastoRecurrenteRepository = gastoRecurrenteRepository;
        this.categoriaGastoRepository = categoriaGastoRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TESORERIA_VER')")
    public List<GastoRecurrente> listar() {
        return gastoRecurrenteRepository.findByTenantIdAndActivoTrue(TenantContext.getTenantId());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TESORERIA_EDITAR')")
    public ResponseEntity<GastoRecurrente> crear(@Valid @RequestBody GastoRecurrenteRequest request) {
        Long tenantId = TenantContext.getTenantId();
        categoriaGastoRepository.findByIdAndTenantIdAndActivoTrue(request.categoriaGastoId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Categoría de gasto no encontrada: " + request.categoriaGastoId()));

        GastoRecurrente recurrente = GastoRecurrente.builder()
                .tenantId(tenantId)
                .categoriaGastoId(request.categoriaGastoId())
                .monto(request.monto())
                .descripcion(request.descripcion())
                .diaMes(request.diaMes().shortValue())
                .fechaInicio(request.fechaInicio())
                .fechaFin(request.fechaFin())
                .build();
        return ResponseEntity.ok(gastoRecurrenteRepository.save(recurrente));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TESORERIA_EDITAR')")
    public ResponseEntity<GastoRecurrente> actualizar(@PathVariable Long id, @Valid @RequestBody GastoRecurrenteRequest request) {
        Long tenantId = TenantContext.getTenantId();
        categoriaGastoRepository.findByIdAndTenantIdAndActivoTrue(request.categoriaGastoId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Categoría de gasto no encontrada: " + request.categoriaGastoId()));

        return gastoRecurrenteRepository.findByIdAndTenantIdAndActivoTrue(id, tenantId)
                .map(recurrente -> {
                    recurrente.setCategoriaGastoId(request.categoriaGastoId());
                    recurrente.setMonto(request.monto());
                    recurrente.setDescripcion(request.descripcion());
                    recurrente.setDiaMes(request.diaMes().shortValue());
                    recurrente.setFechaInicio(request.fechaInicio());
                    recurrente.setFechaFin(request.fechaFin());
                    return ResponseEntity.ok(gastoRecurrenteRepository.save(recurrente));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('TESORERIA_EDITAR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        return gastoRecurrenteRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .map(recurrente -> {
                    recurrente.setActivo(false);
                    gastoRecurrenteRepository.save(recurrente);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
