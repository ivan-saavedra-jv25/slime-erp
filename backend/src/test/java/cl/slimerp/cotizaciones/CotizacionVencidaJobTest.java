package cl.slimerp.cotizaciones;

import cl.slimerp.tenant.Tenant;
import cl.slimerp.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CotizacionVencidaJobTest {

    private TenantRepository tenantRepository;
    private CotizacionRepository cotizacionRepository;
    private CotizacionService cotizacionService;
    private CotizacionVencidaJob job;

    private final Long tenantId = 1L;
    private final LocalDate hoy = LocalDate.of(2026, 9, 22);

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        cotizacionRepository = mock(CotizacionRepository.class);
        cotizacionService = mock(CotizacionService.class);
        job = new CotizacionVencidaJob(tenantRepository, cotizacionRepository, cotizacionService);
    }

    private Cotizacion enviadaVencida(Long id) {
        return Cotizacion.builder().id(id).tenantId(tenantId).folio(id.intValue())
                .estado(EstadoCotizacion.ENVIADA)
                .fechaEmision(hoy.minusDays(40)).fechaVencimiento(hoy.minusDays(10))
                .build();
    }

    @Test
    void marcaLasCotizacionesEnviadasConVigenciaExpirada() {
        when(cotizacionRepository.findByTenantIdAndEstadoAndFechaVencimientoBefore(
                tenantId, EstadoCotizacion.ENVIADA, hoy))
                .thenReturn(List.of(enviadaVencida(1L), enviadaVencida(2L)));

        int marcadas = job.marcarVencidasParaTenant(tenantId, hoy);

        assertEquals(2, marcadas);
        verify(cotizacionService, times(2)).marcarVencida(any(Cotizacion.class));
    }

    @Test
    void soloConsultaCotizacionesEnviadas() {
        when(cotizacionRepository.findByTenantIdAndEstadoAndFechaVencimientoBefore(any(), any(), any()))
                .thenReturn(List.of());

        job.marcarVencidasParaTenant(tenantId, hoy);

        verify(cotizacionRepository).findByTenantIdAndEstadoAndFechaVencimientoBefore(
                tenantId, EstadoCotizacion.ENVIADA, hoy);
        verifyNoInteractions(cotizacionService);
    }

    @Test
    void unaCotizacionQueFallaNoAbortaLasDemas() {
        when(cotizacionRepository.findByTenantIdAndEstadoAndFechaVencimientoBefore(any(), any(), any()))
                .thenReturn(List.of(enviadaVencida(1L), enviadaVencida(2L)));
        doThrow(new IllegalStateException("falla")).when(cotizacionService)
                .marcarVencida(argThat(c -> c != null && c.getId().equals(1L)));

        int marcadas = job.marcarVencidasParaTenant(tenantId, hoy);

        assertEquals(1, marcadas);
    }

    @Test
    void unTenantQueFallaNoAbortaElResto() {
        when(tenantRepository.findByActivoTrue()).thenReturn(List.of(
                Tenant.builder().id(1L).nombre("Uno").build(),
                Tenant.builder().id(2L).nombre("Dos").build()));
        when(cotizacionRepository.findByTenantIdAndEstadoAndFechaVencimientoBefore(eq(1L), any(), any()))
                .thenThrow(new IllegalStateException("falla"));
        when(cotizacionRepository.findByTenantIdAndEstadoAndFechaVencimientoBefore(eq(2L), any(), any()))
                .thenReturn(List.of(enviadaVencida(3L)));

        job.marcarVencidas();

        verify(cotizacionService, times(1)).marcarVencida(any(Cotizacion.class));
    }
}
