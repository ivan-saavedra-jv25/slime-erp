package cl.slimerp.gastos;

import cl.slimerp.config.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gastos/recurrentes")
public class GastoRecurrenteController {

    private final GastoRecurrenteRepository gastoRecurrenteRepository;
    private final CategoriaGastoRepository categoriaGastoRepository;
    private final GastoRecurrenteGeneratorJob gastoRecurrenteGeneratorJob;

    public GastoRecurrenteController(GastoRecurrenteRepository gastoRecurrenteRepository,
                                      CategoriaGastoRepository categoriaGastoRepository,
                                      GastoRecurrenteGeneratorJob gastoRecurrenteGeneratorJob) {
        this.gastoRecurrenteRepository = gastoRecurrenteRepository;
        this.categoriaGastoRepository = categoriaGastoRepository;
        this.gastoRecurrenteGeneratorJob = gastoRecurrenteGeneratorJob;
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
        validarCategoria(request.categoriaGastoId(), tenantId);
        validarPeriodicidad(request);

        GastoRecurrente recurrente = GastoRecurrente.builder()
                .tenantId(tenantId)
                .categoriaGastoId(request.categoriaGastoId())
                .monto(request.monto())
                .descripcion(request.descripcion())
                .frecuencia(request.frecuencia())
                .diaMes(diaMesPara(request))
                .fechaInicio(request.fechaInicio())
                .fechaFin(request.fechaFin())
                .build();
        return ResponseEntity.ok(gastoRecurrenteRepository.save(recurrente));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TESORERIA_EDITAR')")
    public ResponseEntity<GastoRecurrente> actualizar(@PathVariable Long id, @Valid @RequestBody GastoRecurrenteRequest request) {
        Long tenantId = TenantContext.getTenantId();
        validarCategoria(request.categoriaGastoId(), tenantId);
        validarPeriodicidad(request);

        return gastoRecurrenteRepository.findByIdAndTenantIdAndActivoTrue(id, tenantId)
                .map(recurrente -> {
                    recurrente.setCategoriaGastoId(request.categoriaGastoId());
                    recurrente.setMonto(request.monto());
                    recurrente.setDescripcion(request.descripcion());
                    recurrente.setFrecuencia(request.frecuencia());
                    recurrente.setDiaMes(diaMesPara(request));
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

    // Dispara la generación de las plantillas vigentes del tenant de la petición,
    // cargando cada gasto y su cuenta por pagar en tesorería. Es idempotente: el
    // cron diario usa la misma lógica y nunca duplica la instancia de un período.
    @PostMapping("/generar")
    @PreAuthorize("hasAuthority('TESORERIA_EDITAR')")
    public ResponseEntity<Map<String, Integer>> generarInstancias() {
        int generados = gastoRecurrenteGeneratorJob.generarParaTenantActual();
        return ResponseEntity.ok(Map.of("generados", generados));
    }

    private void validarCategoria(Long categoriaGastoId, Long tenantId) {
        categoriaGastoRepository.findByIdAndTenantIdAndActivoTrue(categoriaGastoId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Categoría de gasto no encontrada: " + categoriaGastoId));
    }

    private void validarPeriodicidad(GastoRecurrenteRequest request) {
        if (request.frecuencia() == FrecuenciaGastoRecurrente.MENSUAL && request.diaMes() == null) {
            throw new IllegalArgumentException("El día del mes es obligatorio para plantillas de gasto mensuales");
        }
    }

    // El día del mes solo aplica a plantillas MENSUAL; las demás frecuencias no lo usan.
    private Short diaMesPara(GastoRecurrenteRequest request) {
        return request.frecuencia() == FrecuenciaGastoRecurrente.MENSUAL
                ? request.diaMes().shortValue()
                : null;
    }
}
