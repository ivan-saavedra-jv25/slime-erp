package cl.slimerp.inventario;

import cl.slimerp.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BodegaControllerTest {

    private BodegaRepository bodegaRepository;
    private BodegaController controller;

    @BeforeEach
    void setUp() {
        bodegaRepository = mock(BodegaRepository.class);
        controller = new BodegaController(bodegaRepository);
        TenantContext.setTenantId(1L);
        when(bodegaRepository.save(any(Bodega.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void crearAsociaLaBodegaAlTenantDelContexto() {
        var request = new BodegaRequest("Sucursal Centro", TipoBodega.BODEGAJE);

        var response = controller.crear(request);

        assertEquals(1L, response.getBody().getTenantId());
        assertEquals("Sucursal Centro", response.getBody().getNombre());
        assertEquals(TipoBodega.BODEGAJE, response.getBody().getTipo());
        assertFalse(response.getBody().isPrincipal());
    }

    @Test
    void eliminarHaceSoftDeleteEnVezDeBorrarFisicamente() {
        Bodega existente = Bodega.builder().id(5L).tenantId(1L).nombre("Sucursal Sur").activo(true).build();
        when(bodegaRepository.findByIdAndTenantIdAndActivoTrue(5L, 1L)).thenReturn(Optional.of(existente));

        var response = controller.eliminar(5L);

        assertEquals(204, response.getStatusCode().value());
        assertFalse(existente.isActivo());
        verify(bodegaRepository).save(existente);
    }

    @Test
    void eliminarRechazaLaBodegaPrincipal() {
        Bodega principal = Bodega.builder().id(1L).tenantId(1L).nombre("Principal").principal(true).activo(true).build();
        when(bodegaRepository.findByIdAndTenantIdAndActivoTrue(1L, 1L)).thenReturn(Optional.of(principal));

        assertThrows(IllegalArgumentException.class, () -> controller.eliminar(1L));
        assertTrue(principal.isActivo());
    }

    @Test
    void actualizarDevuelve404SiNoExisteEnElTenant() {
        when(bodegaRepository.findByIdAndTenantIdAndActivoTrue(99L, 1L)).thenReturn(Optional.empty());

        var response = controller.actualizar(99L, new BodegaRequest("X", TipoBodega.BODEGAJE));

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void marcarPrincipalQuitaLaMarcaALaBodegaAnteriorYLaAsignaALaNueva() {
        Bodega actual = Bodega.builder().id(1L).tenantId(1L).nombre("Principal").tipo(TipoBodega.PRINCIPAL)
                .principal(true).activo(true).build();
        Bodega nueva = Bodega.builder().id(2L).tenantId(1L).nombre("Sucursal Norte").tipo(TipoBodega.BODEGAJE)
                .principal(false).activo(true).build();
        when(bodegaRepository.findByIdAndTenantIdAndActivoTrue(2L, 1L)).thenReturn(Optional.of(nueva));
        when(bodegaRepository.findByTenantIdAndPrincipalTrueAndActivoTrue(1L)).thenReturn(Optional.of(actual));

        var response = controller.marcarPrincipal(2L);

        assertTrue(response.getBody().isPrincipal());
        assertEquals(TipoBodega.PRINCIPAL, response.getBody().getTipo());
        assertFalse(actual.isPrincipal());
        assertEquals(TipoBodega.BODEGAJE, actual.getTipo());
        verify(bodegaRepository).save(actual);
        verify(bodegaRepository).save(nueva);
    }

    @Test
    void marcarPrincipalEsIdempotenteSiYaEsLaPrincipal() {
        Bodega actual = Bodega.builder().id(1L).tenantId(1L).nombre("Principal").tipo(TipoBodega.PRINCIPAL)
                .principal(true).activo(true).build();
        when(bodegaRepository.findByIdAndTenantIdAndActivoTrue(1L, 1L)).thenReturn(Optional.of(actual));
        when(bodegaRepository.findByTenantIdAndPrincipalTrueAndActivoTrue(1L)).thenReturn(Optional.of(actual));

        var response = controller.marcarPrincipal(1L);

        assertTrue(response.getBody().isPrincipal());
        verify(bodegaRepository, times(1)).save(actual);
    }

    @Test
    void marcarPrincipalDevuelve400SiLaBodegaNoExiste() {
        when(bodegaRepository.findByIdAndTenantIdAndActivoTrue(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> controller.marcarPrincipal(99L));
    }
}
