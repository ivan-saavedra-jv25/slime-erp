package cl.slimerp.admin.pago;

import cl.slimerp.admin.auditoria.AuditService;
import cl.slimerp.admin.cobranza.CobranzaEmpresa;
import cl.slimerp.admin.cobranza.CobranzaEmpresaRepository;
import cl.slimerp.admin.cobranza.CobranzaPago;
import cl.slimerp.admin.cobranza.CobranzaPagoRepository;
import cl.slimerp.admin.cobranza.CobranzaService;
import cl.slimerp.admin.cobranza.EstadoCobranza;
import cl.slimerp.admin.cobranza.EstadoPagoCobranza;
import cl.slimerp.admin.cobranza.MedioPago;
import cl.slimerp.admin.common.Paginated;
import cl.slimerp.admin.tenant.Tenant;
import cl.slimerp.admin.tenant.TenantRepository;
import cl.slimerp.admin.usuario.AdminUsuario;
import cl.slimerp.admin.usuario.AdminUsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PagoServiceTest {

    private CobranzaEmpresaRepository cobranzaEmpresaRepository;
    private CobranzaPagoRepository cobranzaPagoRepository;
    private CobranzaService cobranzaService;
    private TenantRepository tenantRepository;
    private AdminUsuarioRepository adminUsuarioRepository;
    private AuditService auditService;
    private PagoService service;

    @BeforeEach
    void setUp() {
        cobranzaEmpresaRepository = mock(CobranzaEmpresaRepository.class);
        cobranzaPagoRepository = mock(CobranzaPagoRepository.class);
        cobranzaService = mock(CobranzaService.class);
        tenantRepository = mock(TenantRepository.class);
        adminUsuarioRepository = mock(AdminUsuarioRepository.class);
        auditService = mock(AuditService.class);
        service = new PagoService(cobranzaEmpresaRepository, cobranzaPagoRepository, cobranzaService,
                tenantRepository, adminUsuarioRepository, auditService);
    }

    private Tenant tenant(Long id) {
        return Tenant.builder().id(id).nombre("Empresa ABC").plan("empresa-1").build();
    }

    private CobranzaPago pago(Long id) {
        return CobranzaPago.builder()
                .id(id)
                .cobranzaEmpresaId(2L)
                .tenantId(1L)
                .monto(new BigDecimal("50000"))
                .medioPago(MedioPago.TRANSFERENCIA)
                .numeroOperacion("REF-001")
                .estado(EstadoPagoCobranza.CONFIRMADA)
                .fecha(LocalDateTime.of(2026, 9, 8, 11, 0))
                .usuarioAdminId(5L)
                .build();
    }

    @Test
    void registrarManualValidaQueLaEmpresaExista() {
        when(tenantRepository.findById(99L)).thenReturn(Optional.empty());

        RegistrarPagoManualRequest request =
                new RegistrarPagoManualRequest(99L, null, new BigDecimal("50000"), MedioPago.TRANSFERENCIA, null, null);

        assertThrows(IllegalArgumentException.class, () -> service.registrarManual(request, 5L));
    }

    @Test
    void registrarManualDevolverPagoSobreCobranzaNuevaCuandoNoExiste() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant(1L)));
        when(cobranzaEmpresaRepository.findFirstByTenantIdAndEstadoInOrderByFechaEmisionDesc(any(), anyCollection()))
                .thenReturn(Optional.empty());
        when(cobranzaEmpresaRepository.save(any(CobranzaEmpresa.class)))
                .thenAnswer(inv -> {
                    CobranzaEmpresa c = inv.getArgument(0);
                    c.setId(2L);
                    return c;
                });
        when(cobranzaService.registrarPago(eq(2L), any(), eq(5L))).thenAnswer(inv -> {
            CobranzaPago p = pago(7L);
            p.setCobranzaEmpresaId(2L);
            return p;
        });
        when(adminUsuarioRepository.findById(5L))
                .thenReturn(Optional.of(AdminUsuario.builder().id(5L).nombre("Admin Uno").build()));

        RegistrarPagoManualRequest request =
                new RegistrarPagoManualRequest(1L, null, new BigDecimal("50000"), MedioPago.TRANSFERENCIA, "REF-001", null);

        PagoResponse resultado = service.registrarManual(request, 5L);

        assertEquals("PAID", resultado.estado());
        assertEquals("Empresa ABC", resultado.companyNombre());
        assertEquals("REF-001", resultado.referencia());
        verify(cobranzaEmpresaRepository).save(any(CobranzaEmpresa.class));
        verify(auditService).registrar(eq("PAYMENT_REGISTERED"), eq("pagos"), any(),
                eq("pago"), eq(7L), eq("{}"), any());
    }

    @Test
    void registrarManualReutilizaCobranzaAbiertaSinCrearNueva() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant(1L)));
        CobranzaEmpresa cobranza = CobranzaEmpresa.builder()
                .id(2L).tenantId(1L).montoTotal(new BigDecimal("50000"))
                .saldoPendiente(new BigDecimal("20000")).estado(EstadoCobranza.PARCIAL).build();
        when(cobranzaEmpresaRepository.findFirstByTenantIdAndEstadoInOrderByFechaEmisionDesc(any(), anyCollection()))
                .thenReturn(Optional.of(cobranza));
        when(cobranzaService.registrarPago(eq(2L), any(), eq(5L))).thenReturn(pago(7L));
        when(adminUsuarioRepository.findById(5L))
                .thenReturn(Optional.of(AdminUsuario.builder().id(5L).nombre("Admin Uno").build()));

        RegistrarPagoManualRequest request =
                new RegistrarPagoManualRequest(1L, null, new BigDecimal("20000"), MedioPago.TRANSFERENCIA, null, null);

        PagoResponse resultado = service.registrarManual(request, 5L);

        assertEquals("PAID", resultado.estado());
        verify(cobranzaEmpresaRepository, never()).save(any(CobranzaEmpresa.class));
        verify(auditService).registrar(eq("PAYMENT_REGISTERED"), eq("pagos"), any(),
                eq("pago"), eq(7L), eq("{}"), any());
    }

    @Test
    void listarMapeaEstadoEspecYDelegaFiltros() {
        when(cobranzaPagoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pago(1L))));
        when(tenantRepository.findAllById(anyCollection()))
                .thenReturn(List.of(Tenant.builder().id(1L).nombre("Empresa ABC").build()));
        when(adminUsuarioRepository.findAllById(anyCollection()))
                .thenReturn(List.of(AdminUsuario.builder().id(5L).nombre("Admin Uno").build()));

        Paginated<PagoResponse> resultado = service.listar(0, 20, 1L, "PAID");

        assertEquals(1, resultado.content().size());
        PagoResponse r = resultado.content().get(0);
        assertEquals("PAID", r.estado());
        assertEquals("Empresa ABC", r.companyNombre());
        assertEquals("Admin Uno", r.adminNombre());
        assertEquals("TRANSFERENCIA", r.metodo());
        verify(cobranzaPagoRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void listarRechazaEstadoInvalido() {
        assertThrows(IllegalArgumentException.class, () -> service.listar(0, 20, null, "PENDING"));
    }

    @Test
    void obtenerDevuelvePagoConNombresResueltos() {
        when(cobranzaPagoRepository.findById(1L)).thenReturn(Optional.of(pago(1L)));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant(1L)));
        when(adminUsuarioRepository.findById(5L))
                .thenReturn(Optional.of(AdminUsuario.builder().id(5L).nombre("Admin Uno").build()));

        PagoResponse r = service.obtener(1L);

        assertEquals("PAID", r.estado());
        assertEquals("Empresa ABC", r.companyNombre());
        assertEquals("Admin Uno", r.adminNombre());
    }
}