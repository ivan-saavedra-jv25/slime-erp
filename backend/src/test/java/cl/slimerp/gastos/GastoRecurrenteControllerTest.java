package cl.slimerp.gastos;

import cl.slimerp.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GastoRecurrenteControllerTest {

    private GastoRecurrenteRepository gastoRecurrenteRepository;
    private CategoriaGastoRepository categoriaGastoRepository;
    private GastoRecurrenteController controller;

    private final CategoriaGasto categoria = CategoriaGasto.builder().id(1L).tenantId(1L).nombre("Arriendo").activo(true).build();

    @BeforeEach
    void setUp() {
        gastoRecurrenteRepository = mock(GastoRecurrenteRepository.class);
        categoriaGastoRepository = mock(CategoriaGastoRepository.class);
        controller = new GastoRecurrenteController(gastoRecurrenteRepository, categoriaGastoRepository);
        TenantContext.setTenantId(1L);
        when(gastoRecurrenteRepository.save(any(GastoRecurrente.class))).thenAnswer(inv -> inv.getArgument(0));
        when(categoriaGastoRepository.findByIdAndTenantIdAndActivoTrue(1L, 1L)).thenReturn(Optional.of(categoria));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private GastoRecurrenteRequest request() {
        return new GastoRecurrenteRequest(1L, new BigDecimal("350000"), "Arriendo oficina", 5,
                LocalDate.of(2026, 1, 1), null);
    }

    @Test
    void crearAsociaLaPlantillaAlTenantDelContexto() {
        var response = controller.crear(request());

        assertEquals(1L, response.getBody().getTenantId());
        assertEquals(new BigDecimal("350000"), response.getBody().getMonto());
        assertEquals((short) 5, response.getBody().getDiaMes());
    }

    @Test
    void rechazaLaPlantillaSiLaCategoriaNoExiste() {
        var req = new GastoRecurrenteRequest(999L, new BigDecimal("100"), "X", 1, LocalDate.now(), null);

        assertThrows(IllegalArgumentException.class, () -> controller.crear(req));
    }

    @Test
    void eliminarHaceSoftDeleteEnVezDeBorrarFisicamente() {
        GastoRecurrente existente = GastoRecurrente.builder().id(7L).tenantId(1L).categoriaGastoId(1L)
                .monto(new BigDecimal("1000")).descripcion("X").diaMes((short) 1)
                .fechaInicio(LocalDate.now()).activo(true).build();
        when(gastoRecurrenteRepository.findByIdAndTenantIdAndActivoTrue(7L, 1L)).thenReturn(Optional.of(existente));

        var response = controller.eliminar(7L);

        assertEquals(204, response.getStatusCode().value());
        assertFalse(existente.isActivo());
    }
}
