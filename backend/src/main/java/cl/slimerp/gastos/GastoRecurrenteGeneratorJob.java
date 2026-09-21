package cl.slimerp.gastos;

import cl.slimerp.config.TenantContext;
import cl.slimerp.tenant.Tenant;
import cl.slimerp.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class GastoRecurrenteGeneratorJob {

    private static final Logger log = LoggerFactory.getLogger(GastoRecurrenteGeneratorJob.class);

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
            } catch (Exception e) {
                log.error("Error generando gastos recurrentes para el tenant {}", tenant.getId(), e);
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
            try {
                LocalDate inicioMes = hoy.withDayOfMonth(1);
                LocalDate finMes = hoy.withDayOfMonth(hoy.lengthOfMonth());
                boolean yaExiste = gastoRepository.existsByTenantIdAndGastoRecurrenteIdAndFechaBetween(
                        tenantId, recurrente.getId(), inicioMes, finMes);
                if (yaExiste) continue;

                gastoService.crearDesdeRecurrente(recurrente, hoy);
            } catch (Exception e) {
                log.error("Error generando el gasto de la plantilla recurrente {} para el tenant {}",
                        recurrente.getId(), tenantId, e);
            }
        }
    }

    private boolean correspondeGenerarHoy(GastoRecurrente recurrente, LocalDate hoy) {
        if (recurrente.getDiaMes() > hoy.getDayOfMonth()) return false;
        if (recurrente.getFechaInicio().isAfter(hoy)) return false;
        return recurrente.getFechaFin() == null || !recurrente.getFechaFin().isBefore(hoy);
    }
}
