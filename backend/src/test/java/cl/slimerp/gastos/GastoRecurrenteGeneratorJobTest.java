package cl.slimerp.gastos;

import cl.slimerp.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GastoRecurrenteGeneratorJobTest {

    private GastoRecurrenteRepository gastoRecurrenteRepository;
    private GastoRepository gastoRepository;
    private GastoService gastoService;
    private GastoRecurrenteGeneratorJob job;

    private final Long tenantId = 1L;
    private final LocalDate hoy = LocalDate.of(2026, 9, 5);

    private final GastoRecurrente recurrente = GastoRecurrente.builder()
            .id(7L).tenantId(tenantId).categoriaGastoId(1L).monto(new BigDecimal("350000"))
            .descripcion("Arriendo oficina").diaMes((short) 5).fechaInicio(LocalDate.of(2026, 1, 1))
            .activo(true).build();

    @BeforeEach
    void setUp() {
        gastoRecurrenteRepository = mock(GastoRecurrenteRepository.class);
        gastoRepository = mock(GastoRepository.class);
        gastoService = mock(GastoService.class);
        job = new GastoRecurrenteGeneratorJob(mock(TenantRepository.class),
                gastoRecurrenteRepository, gastoRepository, gastoService);

        when(gastoRecurrenteRepository.findByTenantIdAndActivoTrue(tenantId)).thenReturn(List.of(recurrente));
    }

    @Test
    void generaLaInstanciaCuandoHoyEsElDiaDeLaPlantillaYNoExisteAun() {
        when(gastoRepository.existsByTenantIdAndGastoRecurrenteIdAndFechaBetween(
                eq(tenantId), eq(7L), any(), any())).thenReturn(false);

        job.generarParaTenant(tenantId, hoy);

        verify(gastoService).crearDesdeRecurrente(recurrente, hoy);
    }

    @Test
    void noDuplicaSiYaExisteUnaInstanciaEsteMes() {
        when(gastoRepository.existsByTenantIdAndGastoRecurrenteIdAndFechaBetween(
                eq(tenantId), eq(7L), any(), any())).thenReturn(true);

        job.generarParaTenant(tenantId, hoy);

        verify(gastoService, never()).crearDesdeRecurrente(any(), any());
    }

    @Test
    void noGeneraSiElDiaDeLaPlantillaAunNoLlega() {
        job.generarParaTenant(tenantId, LocalDate.of(2026, 9, 3));

        verify(gastoService, never()).crearDesdeRecurrente(any(), any());
    }

    @Test
    void generaLaInstanciaSiElDiaDeLaPlantillaYaPasoEsteMesYNoExisteAun() {
        LocalDate hoyMasTarde = LocalDate.of(2026, 9, 10);
        when(gastoRepository.existsByTenantIdAndGastoRecurrenteIdAndFechaBetween(
                eq(tenantId), eq(7L), any(), any())).thenReturn(false);

        job.generarParaTenant(tenantId, hoyMasTarde);

        verify(gastoService).crearDesdeRecurrente(recurrente, hoyMasTarde);
    }

    @Test
    void noGeneraSiLaFechaFinYaPaso() {
        GastoRecurrente vencida = GastoRecurrente.builder()
                .id(8L).tenantId(tenantId).categoriaGastoId(1L).monto(BigDecimal.TEN)
                .descripcion("X").diaMes((short) 5).fechaInicio(LocalDate.of(2025, 1, 1))
                .fechaFin(LocalDate.of(2026, 8, 31)).activo(true).build();
        when(gastoRecurrenteRepository.findByTenantIdAndActivoTrue(tenantId)).thenReturn(List.of(vencida));

        job.generarParaTenant(tenantId, hoy);

        verify(gastoService, never()).crearDesdeRecurrente(any(), any());
    }

    @Test
    void noGeneraSiLaFechaInicioEsFutura() {
        GastoRecurrente futura = GastoRecurrente.builder()
                .id(9L).tenantId(tenantId).categoriaGastoId(1L).monto(BigDecimal.TEN)
                .descripcion("X").diaMes((short) 5).fechaInicio(LocalDate.of(2027, 1, 1))
                .activo(true).build();
        when(gastoRecurrenteRepository.findByTenantIdAndActivoTrue(tenantId)).thenReturn(List.of(futura));

        job.generarParaTenant(tenantId, hoy);

        verify(gastoService, never()).crearDesdeRecurrente(any(), any());
    }

    @Test
    void noAbortaLasDemasPlantillasSiUnaFallaAlGenerar() {
        GastoRecurrente otra = GastoRecurrente.builder()
                .id(11L).tenantId(tenantId).categoriaGastoId(2L).monto(new BigDecimal("20000"))
                .descripcion("Internet").diaMes((short) 5).fechaInicio(LocalDate.of(2026, 1, 1))
                .activo(true).build();
        when(gastoRecurrenteRepository.findByTenantIdAndActivoTrue(tenantId)).thenReturn(List.of(recurrente, otra));
        when(gastoRepository.existsByTenantIdAndGastoRecurrenteIdAndFechaBetween(
                eq(tenantId), any(), any(), any())).thenReturn(false);
        when(gastoService.crearDesdeRecurrente(recurrente, hoy)).thenThrow(new IllegalArgumentException("Categoría no encontrada"));

        job.generarParaTenant(tenantId, hoy);

        verify(gastoService).crearDesdeRecurrente(recurrente, hoy);
        verify(gastoService).crearDesdeRecurrente(otra, hoy);
    }
}
