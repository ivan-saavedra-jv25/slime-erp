package cl.slimerp.catalogo;

import cl.slimerp.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ClienteControllerTest {

    private ClienteRepository clienteRepository;
    private ClienteController controller;

    @BeforeEach
    void setUp() {
        clienteRepository = mock(ClienteRepository.class);
        controller = new ClienteController(clienteRepository);
        TenantContext.setTenantId(1L);
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void crearAsociaElClienteAlTenantDelContexto() {
        var request = new ClienteRequest("Cliente Uno", "1-9", "c1@demo.cl", "+56911111111", "Calle 1", null, null, null, null);

        var response = controller.crear(request);

        assertEquals(1L, response.getBody().getTenantId());
        assertEquals("Cliente Uno", response.getBody().getNombre());
    }

    @Test
    void eliminarHaceSoftDeleteEnVezDeBorrarFisicamente() {
        Cliente existente = Cliente.builder().id(5L).tenantId(1L).nombre("Cliente Dos").activo(true).build();
        when(clienteRepository.findByIdAndTenantIdAndActivoTrue(5L, 1L)).thenReturn(Optional.of(existente));

        var response = controller.eliminar(5L);

        assertEquals(204, response.getStatusCode().value());
        assertFalse(existente.isActivo());
        verify(clienteRepository).save(existente);
    }

    @Test
    void actualizarDevuelve404SiNoExisteEnElTenant() {
        when(clienteRepository.findByIdAndTenantIdAndActivoTrue(99L, 1L)).thenReturn(Optional.empty());

        var response = controller.actualizar(99L, new ClienteRequest("X", null, null, null, null, null, null, null, null));

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void listarPaginaArmaElPatronLikeAPartirDelTermino() {
        Cliente cliente = Cliente.builder().id(1L).tenantId(1L).nombre("Constructora Los Andes").activo(true).build();
        when(clienteRepository.buscar(eq(1L), eq("%andes%"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cliente), PageRequest.of(0, 10), 1));

        var respuesta = controller.listarPagina("Andes", 0, 10);

        assertEquals(List.of(cliente), respuesta.contenido());
        assertEquals(1, respuesta.total());
    }

    @Test
    void listarPaginaSinTerminoDeBusquedaUsaUnPatronQueCoincideConTodo() {
        when(clienteRepository.buscar(eq(1L), eq("%%"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        var respuesta = controller.listarPagina(null, 0, 10);

        assertEquals(0, respuesta.total());
    }
}
