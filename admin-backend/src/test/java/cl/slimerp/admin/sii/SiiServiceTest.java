package cl.slimerp.admin.sii;

import cl.slimerp.admin.dte.VentaDte;
import cl.slimerp.admin.dte.VentaDteRepository;
import cl.slimerp.admin.tenant.Tenant;
import cl.slimerp.admin.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SiiServiceTest {

    private TenantRepository tenantRepository;
    private VentaDteRepository ventaDteRepository;
    private SiiService service;

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        ventaDteRepository = mock(VentaDteRepository.class);
        service = new SiiService(tenantRepository, ventaDteRepository);
    }

    private Tenant tenant(Long id, String nombre, String plan) {
        return Tenant.builder().id(id).nombre(nombre).rut("76543210-1").plan(plan)
                .status("ACTIVE").activo(true).build();
    }

    @Test
    void listarCalculaEstadoNotConfiguredYUlimoDte() {
        VentaDte ultimo = mock(VentaDte.class);
        when(ultimo.getFecha()).thenReturn(LocalDateTime.of(2026, 2, 1, 12, 0));
        Page<Tenant> pagina = new PageImpl<>(List.of(tenant(1L, "Empresa Demo", "basico")),
                PageRequest.of(0, 20), 1);
        when(tenantRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(pagina);
        when(ventaDteRepository.findFirstByTenantIdOrderByFechaDesc(1L)).thenReturn(Optional.of(ultimo));

        var resultado = service.listar(0, 20, null);

        SiiEstadoResponse item = resultado.content().get(0);
        assertEquals("Empresa Demo", item.empresaNombre());
        assertEquals("NOT_CONFIGURED", item.estado());
        assertNull(item.certificadoVence());
        assertNull(item.ambiente());
        assertEquals(LocalDateTime.of(2026, 2, 1, 12, 0), item.ultimoDte());
    }

    @Test
    void empresaDevuelveDetalleSinDatosDeCertificado() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant(1L, "Empresa Demo", "basico")));

        SiiEstadoResponse detalle = service.empresa(1L);

        assertEquals("Empresa Demo", detalle.empresaNombre());
        assertEquals("NOT_CONFIGURED", detalle.estado());
        assertNull(detalle.certificadoVence());
        assertNull(detalle.ultimaComunicacion());
    }

    @Test
    void listarRechazaEstadoInvalido() {
        assertThrows(IllegalArgumentException.class, () -> service.listar(0, 20, "INVENTADO"));
    }

    @Test
    void empresaDePlataformaEsRechazada() {
        when(tenantRepository.findById(2L)).thenReturn(Optional.of(tenant(2L, "Plataforma", "plataforma")));

        assertThrows(IllegalArgumentException.class, () -> service.empresa(2L));
    }

    @Test
    void empresaInexistenteLanza() {
        when(tenantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.empresa(99L));
    }
}