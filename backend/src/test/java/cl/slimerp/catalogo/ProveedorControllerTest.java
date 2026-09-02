package cl.slimerp.catalogo;

import cl.slimerp.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProveedorControllerTest {

    private ProveedorRepository proveedorRepository;
    private ProveedorController controller;

    @BeforeEach
    void setUp() {
        proveedorRepository = mock(ProveedorRepository.class);
        controller = new ProveedorController(proveedorRepository);
        TenantContext.setTenantId(1L);
        when(proveedorRepository.save(any(Proveedor.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void crearAsociaElProveedorAlTenantDelContexto() {
        var request = new ProveedorRequest("Proveedor Uno", "1-9", "p1@demo.cl", "+56911111111", "Calle 1");

        var response = controller.crear(request);

        assertEquals(1L, response.getBody().getTenantId());
        assertEquals("Proveedor Uno", response.getBody().getNombre());
    }

    @Test
    void eliminarHaceSoftDeleteEnVezDeBorrarFisicamente() {
        Proveedor existente = Proveedor.builder().id(5L).tenantId(1L).nombre("Proveedor Dos").activo(true).build();
        when(proveedorRepository.findByIdAndTenantIdAndActivoTrue(5L, 1L)).thenReturn(Optional.of(existente));

        var response = controller.eliminar(5L);

        assertEquals(204, response.getStatusCode().value());
        assertFalse(existente.isActivo());
        verify(proveedorRepository).save(existente);
    }

    @Test
    void actualizarDevuelve404SiNoExisteEnElTenant() {
        when(proveedorRepository.findByIdAndTenantIdAndActivoTrue(99L, 1L)).thenReturn(Optional.empty());

        var response = controller.actualizar(99L, new ProveedorRequest("X", null, null, null, null));

        assertEquals(404, response.getStatusCode().value());
    }
}
