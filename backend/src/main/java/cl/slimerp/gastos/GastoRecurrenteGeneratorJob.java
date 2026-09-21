package cl.slimerp.gastos;

import cl.slimerp.config.TenantContext;
import cl.slimerp.tenant.Tenant;
import cl.slimerp.tenant.TenantRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class GastoRecurrenteGeneratorJob {

    private final TenantRepository tenantRepository;
    private final GastoRecurrenteRepository gastoRecurrenteRepository;
    private final GastoRepository gastoRepository;
    private final GastoService gastoService;

    public GastoRecurrenteGeneratorJob(TenantRepository tenantRepository,
                                        GastoRecurrenteRepository gastoRecurrenteRepository,
                                        GastoRepository gastoRepository,
                                        GastoService gastoService) {
        this.tenantRepository = tenantRepository;
        this.gastoRecurrenteRepository = gastoRecurrenteRepository;
        this.gastoRepository = gastoRepository;
        this.gastoService = gastoService;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void generar() {
        LocalDate hoy = LocalDate.now();
        for (Tenant tenant : tenantRepository.findByActivoTrue()) {
            try {
                TenantContext.setTenantId(tenant.getId());
                generarParaTenant(tenant.getId(), hoy);
            } finally {
                TenantContext.clear();
            }
        }
    }

    // Separado de generar() para poder testear la lógica de un tenant sin
    // depender del bucle, de @Scheduled ni de LocalDate.now().
    void generarParaTenant(Long tenantId, LocalDate hoy) {
        for (GastoRecurrente recurrente : gastoRecurrenteRepository.findByTenantIdAndActivoTrue(tenantId)) {
            if (!correspondeGenerarHoy(recurrente, hoy)) continue;

            LocalDate inicioMes = hoy.withDayOfMonth(1);
            LocalDate finMes = hoy.withDayOfMonth(hoy.lengthOfMonth());
            boolean yaExiste = gastoRepository.existsByTenantIdAndGastoRecurrenteIdAndFechaBetween(
                    tenantId, recurrente.getId(), inicioMes, finMes);
            if (yaExiste) continue;

            gastoService.crearDesdeRecurrente(recurrente, hoy);
        }
    }

    private boolean correspondeGenerarHoy(GastoRecurrente recurrente, LocalDate hoy) {
        if (recurrente.getDiaMes() != hoy.getDayOfMonth()) return false;
        if (recurrente.getFechaInicio().isAfter(hoy)) return false;
        return recurrente.getFechaFin() == null || !recurrente.getFechaFin().isBefore(hoy);
    }
}
