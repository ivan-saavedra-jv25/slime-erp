package cl.slimerp.admin.dte;

import cl.slimerp.admin.tenant.Tenant;
import cl.slimerp.admin.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DteServiceTest {

    private VentaDteRepository ventaDteRepository;
    private TenantRepository tenantRepository;
    private DteService service;

    @BeforeEach
    void setUp() {
        ventaDteRepository = mock(VentaDteRepository.class);
        tenantRepository = mock(TenantRepository.class);
        service = new DteService(ventaDteRepository, tenantRepository);
    }

    private VentaDte venta(boolean activa) {
        ClienteDte cliente = mock(ClienteDte.class);
        when(cliente.getRut()).thenReturn("76543210-1");
        when(cliente.getRazonSocial()).thenReturn("Receptor Demo");
        VentaDte venta = mock(VentaDte.class);
        when(venta.getId()).thenReturn(1L);
        when(venta.getTenantId()).thenReturn(1L);
        when(venta.getTipoDocumento()).thenReturn("FACTURA");
        when(venta.getCodigoSii()).thenReturn(33);
        when(venta.isExento()).thenReturn(false);
        when(venta.getFolio()).thenReturn(12);
        when(venta.getCliente()).thenReturn(cliente);
        when(venta.getFecha()).thenReturn(LocalDateTime.of(2026, 2, 1, 12, 0));
        when(venta.getMontoTotal()).thenReturn(new BigDecimal("35590"));
        when(venta.isActivo()).thenReturn(activa);
        return venta;
    }

    @Test
    void listarResuelveEmpresaYEstado() {
        VentaDte revt = venta(true);
        Page<VentaDte> pagina = new PageImpl<>(List.of(revt), PageRequest.of(0, 20), 1);
        when(ventaDteRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(pagina);
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(
                Tenant.builder().id(1L).nombre("Empresa Demo").rut("76543210-1").plan("basico")
                        .status("ACTIVE").activo(true).build()));

        var resultado = service.listar(0, 20, 1L, "FACTURA", "EMITIDA", null, null, 12, null);

        assertEquals(1, resultado.totalElements());
        DteResponse dte = resultado.content().get(0);
        assertEquals("Empresa Demo", dte.empresaNombre());
        assertEquals("EMITIDA", dte.estado());
        assertEquals("76543210-1", dte.rutReceptor());
        assertEquals("Receptor Demo", dte.razonSocialReceptor());
        assertEquals(33, dte.codigoSii());
    }

    @Test
    void listarMapeaAnuladaCuandoVentaInactiva() {
        VentaDte revt = venta(false);
        Page<VentaDte> pagina = new PageImpl<>(List.of(revt), PageRequest.of(0, 20), 1);
        when(ventaDteRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(pagina);

        var resultado = service.listar(0, 20, null, null, "ANULADA", null, null, null, null);

        assertEquals("ANULADA", resultado.content().get(0).estado());
    }

    @Test
    void listarRechazaEstadoNoDisponible() {
        assertThrows(IllegalArgumentException.class,
                () -> service.listar(0, 20, null, null, "ACCEPTED", null, null, null, null));
    }

    @Test
    void dashboardCuentaEmitidosYAnulados() {
        when(ventaDteRepository.count(any(Specification.class))).thenReturn(10L, 3L);

        DteDashboardResponse resumen = service.dashboard(null, null, null, null);

        assertEquals(10L, resumen.emitidos());
        assertEquals(3L, resumen.anulados());
    }
}