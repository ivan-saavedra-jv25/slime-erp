package cl.slimerp.catalogo;

import cl.slimerp.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FormaPagoControllerTest {

    private FormaPagoRepository formaPagoRepository;
    private FormaPagoController controller;

    @BeforeEach
    void setUp() {
        formaPagoRepository = mock(FormaPagoRepository.class);
        controller = new FormaPagoController(formaPagoRepository);
        TenantContext.setTenantId(1L);
        when(formaPagoRepository.save(any(FormaPago.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void crearAsociaLaFormaDePagoAlTenantDelContexto() {
        var request = new FormaPagoRequest("Transferencia", CategoriaFormaPago.CONTADO);

        var response = controller.crear(request);

        assertEquals(1L, response.getBody().getTenantId());
        assertEquals("Transferencia", response.getBody().getNombre());
        assertEquals(CategoriaFormaPago.CONTADO, response.getBody().getCategoria());
    }

    @Test
    void eliminarHaceSoftDeleteEnVezDeBorrarFisicamente() {
        FormaPago existente = FormaPago.builder().id(5L).tenantId(1L).nombre("Efectivo")
                .categoria(CategoriaFormaPago.CONTADO).activo(true).build();
        when(formaPagoRepository.findByIdAndTenantIdAndActivoTrue(5L, 1L)).thenReturn(Optional.of(existente));

        var response = controller.eliminar(5L);

        assertEquals(204, response.getStatusCode().value());
        assertFalse(existente.isActivo());
        verify(formaPagoRepository).save(existente);
    }

    @Test
    void actualizarDevuelve404SiNoExisteEnElTenant() {
        when(formaPagoRepository.findByIdAndTenantIdAndActivoTrue(99L, 1L)).thenReturn(Optional.empty());

        var response = controller.actualizar(99L, new FormaPagoRequest("X", CategoriaFormaPago.GRATIS));

        assertEquals(404, response.getStatusCode().value());
    }
}
