package cl.slimerp.catalogo;

import cl.slimerp.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CategoriaControllerTest {

    private CategoriaRepository categoriaRepository;
    private CategoriaController controller;

    @BeforeEach
    void setUp() {
        categoriaRepository = mock(CategoriaRepository.class);
        controller = new CategoriaController(categoriaRepository);
        TenantContext.setTenantId(1L);
        when(categoriaRepository.save(any(Categoria.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void crearAsociaLaCategoriaAlTenantDelContexto() {
        var request = new CategoriaRequest("Tecnología");

        var response = controller.crear(request);

        assertEquals(1L, response.getBody().getTenantId());
        assertEquals("Tecnología", response.getBody().getNombre());
    }

    @Test
    void eliminarHaceSoftDeleteEnVezDeBorrarFisicamente() {
        Categoria existente = Categoria.builder().id(5L).tenantId(1L).nombre("Cafetería").activo(true).build();
        when(categoriaRepository.findByIdAndTenantIdAndActivoTrue(5L, 1L)).thenReturn(Optional.of(existente));

        var response = controller.eliminar(5L);

        assertEquals(204, response.getStatusCode().value());
        assertFalse(existente.isActivo());
        verify(categoriaRepository).save(existente);
    }

    @Test
    void actualizarDevuelve404SiNoExisteEnElTenant() {
        when(categoriaRepository.findByIdAndTenantIdAndActivoTrue(99L, 1L)).thenReturn(Optional.empty());

        var response = controller.actualizar(99L, new CategoriaRequest("X"));

        assertEquals(404, response.getStatusCode().value());
    }
}
