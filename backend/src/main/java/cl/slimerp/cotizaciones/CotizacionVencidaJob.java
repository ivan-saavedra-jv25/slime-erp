package cl.slimerp.cotizaciones;

import cl.slimerp.config.TenantContext;
import cl.slimerp.tenant.Tenant;
import cl.slimerp.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

// Marca como VENCIDAS las cotizaciones enviadas cuya vigencia ya expiró. Solo
// aplica a ENVIADA: un borrador no vence, y aceptada/rechazada/cancelada ya son
// estados finales.
@Component
public class CotizacionVencidaJob {

    private static final Logger log = LoggerFactory.getLogger(CotizacionVencidaJob.class);

    private final TenantRepository tenantRepository;
    private final CotizacionRepository cotizacionRepository;
    private final CotizacionService cotizacionService;

    public CotizacionVencidaJob(TenantRepository tenantRepository, CotizacionRepository cotizacionRepository,
                                 CotizacionService cotizacionService) {
        this.tenantRepository = tenantRepository;
        this.cotizacionRepository = cotizacionRepository;
        this.cotizacionService = cotizacionService;
    }

    @Scheduled(cron = "0 15 3 * * *")
    public void marcarVencidas() {
        LocalDate hoy = LocalDate.now();
        for (Tenant tenant : tenantRepository.findByActivoTrue()) {
            try {
                TenantContext.setTenantId(tenant.getId());
                marcarVencidasParaTenant(tenant.getId(), hoy);
            } catch (Exception e) {
                log.error("Error marcando cotizaciones vencidas para el tenant {}", tenant.getId(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    // Separado de marcarVencidas() para poder testear la lógica de un tenant sin
    // depender del bucle, de @Scheduled ni de LocalDate.now(). Retorna la
    // cantidad de cotizaciones marcadas.
    int marcarVencidasParaTenant(Long tenantId, LocalDate hoy) {
        int marcadas = 0;
        for (Cotizacion cotizacion : cotizacionRepository.findByTenantIdAndEstadoAndFechaVencimientoBefore(
                tenantId, EstadoCotizacion.ENVIADA, hoy)) {
            try {
                cotizacionService.marcarVencida(cotizacion);
                marcadas++;
            } catch (Exception e) {
                log.error("Error marcando como vencida la cotización {} del tenant {}",
                        cotizacion.getId(), tenantId, e);
            }
        }
        return marcadas;
    }
}
