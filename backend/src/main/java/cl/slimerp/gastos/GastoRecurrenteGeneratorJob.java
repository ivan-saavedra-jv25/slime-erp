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

    // Permite disparar la generación manualmente para el tenant de la petición
    // (por ejemplo desde un endpoint), sin depender del cron diario.
    public int generarParaTenantActual() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) return 0;
        return generarParaTenant(tenantId, LocalDate.now());
    }

    // Separado de generar() para poder testear la lógica de un tenant sin
    // depender del bucle, de @Scheduled ni de LocalDate.now(). Retorna la
    // cantidad de gastos generados.
    int generarParaTenant(Long tenantId, LocalDate hoy) {
        int generados = 0;
        for (GastoRecurrente recurrente : gastoRecurrenteRepository.findByTenantIdAndActivoTrue(tenantId)) {
            if (!enVigencia(recurrente, hoy)) continue;
            if (!esDiaDeGeneracion(recurrente, hoy)) continue;
            try {
                RangoFecha rango = ventanaDeDedupe(recurrente, hoy);
                boolean yaExiste = gastoRepository.existsByTenantIdAndGastoRecurrenteIdAndFechaBetween(
                        tenantId, recurrente.getId(), rango.desde(), rango.hasta());
                if (yaExiste) continue;

                gastoService.crearDesdeRecurrente(recurrente, hoy);
                generados++;
            } catch (Exception e) {
                log.error("Error generando el gasto de la plantilla recurrente {} para el tenant {}",
                        recurrente.getId(), tenantId, e);
            }
        }
        return generados;
    }

    private boolean enVigencia(GastoRecurrente recurrente, LocalDate hoy) {
        if (recurrente.getFechaInicio().isAfter(hoy)) return false;
        return recurrente.getFechaFin() == null || !recurrente.getFechaFin().isBefore(hoy);
    }

    // Determina si hoy es un día de generación para la frecuencia de la plantilla.
    // DIARIO: todos los días. SEMANAL: el mismo día de la semana que la fecha de
    // inicio. MENSUAL: desde el día del mes en adelante (recupera el registro si
    // el cron no contó ese día). ANUAL: desde la fecha del aniversario en
    // adelante dentro del mismo año.
    private boolean esDiaDeGeneracion(GastoRecurrente recurrente, LocalDate hoy) {
        return switch (recurrente.getFrecuencia()) {
            case DIARIO -> true;
            case SEMANAL -> hoy.getDayOfWeek() == recurrente.getFechaInicio().getDayOfWeek();
            case MENSUAL -> recurrente.getDiaMes() != null
                    && recurrente.getDiaMes() <= hoy.getDayOfMonth();
            case ANUAL -> {
                LocalDate inicio = recurrente.getFechaInicio();
                yield hoy.getMonthValue() > inicio.getMonthValue()
                        || (hoy.getMonthValue() == inicio.getMonthValue()
                            && hoy.getDayOfMonth() >= inicio.getDayOfMonth());
            }
        };
    }

    // Ventana usada para no duplicar la instancia del período correspondiente.
    private RangoFecha ventanaDeDedupe(GastoRecurrente recurrente, LocalDate hoy) {
        return switch (recurrente.getFrecuencia()) {
            case DIARIO, SEMANAL -> new RangoFecha(hoy, hoy);
            case MENSUAL -> new RangoFecha(hoy.withDayOfMonth(1),
                    hoy.withDayOfMonth(hoy.lengthOfMonth()));
            case ANUAL -> new RangoFecha(hoy.withDayOfYear(1),
                    hoy.withDayOfYear(hoy.lengthOfYear()));
        };
    }

    private record RangoFecha(LocalDate desde, LocalDate hasta) {
    }
}
