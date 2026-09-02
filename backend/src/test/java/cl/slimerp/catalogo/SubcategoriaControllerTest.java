package cl.slimerp.catalogo;

import cl.slimerp.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SubcategoriaControllerTest {

    private SubcategoriaRepository subcategoriaRepository;
    private CategoriaRepository categoriaRepository;
    private SubcategoriaController controller;

    @BeforeEach
    void setUp() {
        subcategoriaRepository = mock(SubcategoriaRepository.class);
        categoriaRepository = mock(CategoriaRepository.class);
        controller = new SubcategoriaController(subcategoriaRepository, categoriaRepository);
        TenantContext.setTenantId(1L);
        when(subcategoriaRepository.save(any(Subcategoria.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void crearRechazaSiLaCategoriaNoExisteEnElTenant() {
        when(categoriaRepository.findByIdAndTenantIdAndActivoTrue(1L, 1L)).thenReturn(Optional.empty());
        var request = new SubcategoriaRequest(1L, "Notebooks");

        assertThrows(IllegalArgumentException.class, () -> controller.crear(request));
    }

    @Test
    void crearAsociaLaSubcategoriaATenantYCategoria() {
        Categoria categoria = Categoria.builder().id(1L).tenantId(1L).nombre("Tecnología").activo(true).build();
        when(categoriaRepository.findByIdAndTenantIdAndActivoTrue(1L, 1L)).thenReturn(Optional.of(categoria));
        var request = new SubcategoriaRequest(1L, "Notebooks");

        var response = controller.crear(request);

        assertEquals(1L, response.getBody().getTenantId());
        assertEquals(1L, response.getBody().getCategoriaId());
        assertEquals("Notebooks", response.getBody().getNombre());
    }

    @Test
    void eliminarHaceSoftDelete() {
        Subcategoria existente = Subcategoria.builder().id(9L).tenantId(1L).categoriaId(1L).nombre("Monitores").activo(true).build();
        when(subcategoriaRepository.findByIdAndTenantIdAndActivoTrue(9L, 1L)).thenReturn(Optional.of(existente));

        var response = controller.eliminar(9L);

        assertEquals(204, response.getStatusCode().value());
        assertFalse(existente.isActivo());
    }
}
