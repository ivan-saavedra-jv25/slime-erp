package cl.slimerp.gastos;

import cl.slimerp.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CategoriaGastoControllerTest {

    private CategoriaGastoRepository categoriaGastoRepository;
    private CategoriaGastoController controller;

    @BeforeEach
    void setUp() {
        categoriaGastoRepository = mock(CategoriaGastoRepository.class);
        controller = new CategoriaGastoController(categoriaGastoRepository);
        TenantContext.setTenantId(1L);
        when(categoriaGastoRepository.save(any(CategoriaGasto.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void crearAsociaLaCategoriaAlTenantDelContexto() {
        var request = new CategoriaGastoRequest("Arriendo");

        var response = controller.crear(request);

        assertEquals(1L, response.getBody().getTenantId());
        assertEquals("Arriendo", response.getBody().getNombre());
    }

    @Test
    void eliminarHaceSoftDeleteEnVezDeBorrarFisicamente() {
        CategoriaGasto existente = CategoriaGasto.builder().id(5L).tenantId(1L).nombre("Servicios").activo(true).build();
        when(categoriaGastoRepository.findByIdAndTenantIdAndActivoTrue(5L, 1L)).thenReturn(Optional.of(existente));

        var response = controller.eliminar(5L);

        assertEquals(204, response.getStatusCode().value());
        assertFalse(existente.isActivo());
        verify(categoriaGastoRepository).save(existente);
    }

    @Test
    void actualizarDevuelve404SiNoExisteEnElTenant() {
        when(categoriaGastoRepository.findByIdAndTenantIdAndActivoTrue(99L, 1L)).thenReturn(Optional.empty());

        var response = controller.actualizar(99L, new CategoriaGastoRequest("X"));

        assertEquals(404, response.getStatusCode().value());
    }
}
