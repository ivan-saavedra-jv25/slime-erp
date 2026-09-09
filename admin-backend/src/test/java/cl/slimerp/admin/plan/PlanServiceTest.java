package cl.slimerp.admin.plan;

import cl.slimerp.admin.suscripcion.SuscripcionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PlanServiceTest {

    private PlanRepository planRepository;
    private SuscripcionRepository suscripcionRepository;
    private PlanService service;

    @BeforeEach
    void setUp() {
        planRepository = mock(PlanRepository.class);
        suscripcionRepository = mock(SuscripcionRepository.class);
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new PlanService(planRepository, suscripcionRepository, new ObjectMapper());
    }

    private Plan plan(Long id, String estado) {
        return Plan.builder()
                .id(id)
                .nombre("Profesional")
                .descripcion("Para pymes")
                .precioMensual(BigDecimal.valueOf(29990))
                .precioAnual(BigDecimal.valueOf(299900))
                .maxUsuarios(10)
                .maxDocumentos(1000)
                .modulos("[\"ventas\",\"compras\"]")
                .caracteristicas(null)
                .estado(estado)
                .build();
    }

    private PlanRequest request() {
        return new PlanRequest("Nuevo Plan", "Descripción", BigDecimal.valueOf(9990),
                BigDecimal.valueOf(99900), 5, 500,
                List.of("ventas", "compras", "inventario"), null, null);
    }

    @Test
    void crearPersisteModulosComoJsonYEstadoActivoPorDefecto() {
        PlanResponse guardado = service.crear(request());

        assertEquals("Nuevo Plan", guardado.nombre());
        assertEquals(List.of("ventas", "compras", "inventario"), guardado.modulos());
        assertEquals("ACTIVE", guardado.estado());
        verify(planRepository).save(any(Plan.class));
    }

    @Test
    void crearRechazaNombreDuplicado() {
        when(planRepository.findAll()).thenReturn(List.of(plan(1L, "ACTIVE")));

        PlanRequest duplicado = new PlanRequest("Profesional", "Otro", BigDecimal.valueOf(1),
                BigDecimal.valueOf(10), 3, 500, List.of("ventas"), null, null);

        assertThrows(PlanConflictException.class, () -> service.crear(duplicado));
        verify(planRepository, never()).save(any());
    }

    @Test
    void actualizarMantieneNombreQueNoCambia() {
        Plan existente = plan(1L, "ACTIVE");
        when(planRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(planRepository.findAll()).thenReturn(List.of(existente));

        PlanRequest actualizado = new PlanRequest("Profesional", "Nueva desc", BigDecimal.valueOf(19990),
                BigDecimal.valueOf(199900), 15, 2000, List.of("ventas", "dte"), null, "INACTIVE");

        PlanResponse result = assertDoesNotThrow(() -> service.actualizar(1L, actualizado));
        assertEquals("Nueva desc", result.descripcion());
        assertEquals("INACTIVE", result.estado());
    }

    @Test
    void desactivarPermitidoSinSuscripcionesActivas() {
        Plan activo = plan(1L, "ACTIVE");
        when(planRepository.findById(1L)).thenReturn(Optional.of(activo));
        when(suscripcionRepository.countByPlanIdAndEstadoIn(eq(1L), any())).thenReturn(0L);

        PlanResponse result = service.cambiarEstado(1L, "INACTIVE");

        assertEquals("INACTIVE", result.estado());
    }

    @Test
    void desactivarBloqueadoConSuscripcionesActivas() {
        Plan activo = plan(1L, "ACTIVE");
        when(planRepository.findById(1L)).thenReturn(Optional.of(activo));
        when(suscripcionRepository.countByPlanIdAndEstadoIn(eq(1L), any())).thenReturn(2L);

        PlanConflictException ex = assertThrows(PlanConflictException.class, () -> service.cambiarEstado(1L, "INACTIVE"));
        assertTrue(ex.getMessage().contains("suscripciones activas"));
        verify(planRepository, never()).save(any());
    }

    @Test
    void reActivarUnPlanInactivoNoConsultaSuscripciones() {
        Plan inactivo = plan(1L, "INACTIVE");
        when(planRepository.findById(1L)).thenReturn(Optional.of(inactivo));

        PlanResponse result = service.cambiarEstado(1L, "ACTIVE");

        assertEquals("ACTIVE", result.estado());
        verify(suscripcionRepository, never()).countByPlanIdAndEstadoIn(any(), any());
    }

    @Test
    void estadoInvalidoLanzaError() {
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan(1L, "ACTIVE")));

        assertThrows(IllegalArgumentException.class, () -> service.cambiarEstado(1L, "PENDIENTE"));
    }
}