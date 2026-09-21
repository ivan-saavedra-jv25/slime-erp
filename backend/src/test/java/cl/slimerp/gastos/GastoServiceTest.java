package cl.slimerp.gastos;

import cl.slimerp.common.PaginaResponse;
import cl.slimerp.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GastoServiceTest {

    private GastoRepository gastoRepository;
    private CategoriaGastoRepository categoriaGastoRepository;
    private GastoService service;

    private final Long tenantId = 1L;
    private final CategoriaGasto categoria = CategoriaGasto.builder().id(1L).tenantId(1L).nombre("Arriendo").activo(true).build();

    @BeforeEach
    void setUp() {
        gastoRepository = mock(GastoRepository.class);
        categoriaGastoRepository = mock(CategoriaGastoRepository.class);
        service = new GastoService(gastoRepository, categoriaGastoRepository);
        TenantContext.setTenantId(tenantId);

        when(categoriaGastoRepository.findByIdAndTenantIdAndActivoTrue(1L, tenantId)).thenReturn(Optional.of(categoria));
        when(gastoRepository.save(any(Gasto.class))).thenAnswer(inv -> {
            Gasto g = inv.getArgument(0);
            if (g.getId() == null) g.setId(100L);
            return g;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private GastoRequest request() {
        return new GastoRequest(1L, new BigDecimal("50000"), "Internet oficina", LocalDate.of(2026, 9, 5));
    }

    @Test
    void crearAsociaElGastoAlTenantDelContexto() {
        Gasto gasto = service.crear(request());

        assertEquals(tenantId, gasto.getTenantId());
        assertEquals(new BigDecimal("50000"), gasto.getMonto());
        assertNull(gasto.getGastoRecurrenteId());
    }

    @Test
    void rechazaElGastoSiLaCategoriaNoExiste() {
        var req = new GastoRequest(999L, BigDecimal.TEN, "X", LocalDate.now());

        assertThrows(IllegalArgumentException.class, () -> service.crear(req));
    }

    @Test
    void crearDesdeRecurrenteDejaElVinculoALaPlantilla() {
        GastoRecurrente recurrente = GastoRecurrente.builder().id(7L).tenantId(tenantId).categoriaGastoId(1L)
                .monto(new BigDecimal("350000")).descripcion("Arriendo oficina").diaMes((short) 5)
                .fechaInicio(LocalDate.of(2026, 1, 1)).activo(true).build();

        Gasto gasto = service.crearDesdeRecurrente(recurrente, LocalDate.of(2026, 9, 5));

        assertEquals(7L, gasto.getGastoRecurrenteId());
        assertEquals(new BigDecimal("350000"), gasto.getMonto());
        assertEquals(LocalDate.of(2026, 9, 5), gasto.getFecha());
    }

    @Test
    void eliminarHaceSoftDelete() {
        Gasto existente = Gasto.builder().id(5L).tenantId(tenantId).categoriaGastoId(1L)
                .monto(BigDecimal.TEN).descripcion("X").fecha(LocalDate.now()).activo(true).build();
        when(gastoRepository.findByIdAndTenantIdAndActivoTrue(5L, tenantId)).thenReturn(Optional.of(existente));

        service.eliminar(5L);

        assertFalse(existente.isActivo());
        verify(gastoRepository).save(existente);
    }

    @Test
    @SuppressWarnings("unchecked")
    void buscarEnvuelveLaPaginaDevueltaPorElRepositorio() {
        Gasto gasto = Gasto.builder().id(1L).tenantId(tenantId).categoriaGastoId(1L)
                .monto(BigDecimal.TEN).descripcion("X").fecha(LocalDate.now()).activo(true).build();
        Pageable pageable = PageRequest.of(0, 10);
        when(gastoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(gasto), pageable, 1));

        PaginaResponse<Gasto> resultado = service.buscar(null, null, null, null, 0, 10);

        assertEquals(1, resultado.total());
        assertEquals(1, resultado.contenido().size());
    }

    @Test
    void buscarRechazaPaginaNegativa() {
        assertThrows(IllegalArgumentException.class, () -> service.buscar(null, null, null, null, -1, 10));
    }
}
