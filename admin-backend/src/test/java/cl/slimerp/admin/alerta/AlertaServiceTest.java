package cl.slimerp.admin.alerta;

import cl.slimerp.admin.auditoria.AuditService;
import cl.slimerp.admin.common.Paginated;
import cl.slimerp.admin.tenant.Tenant;
import cl.slimerp.admin.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AlertaServiceTest {

    private AlertaRepository alertaRepository;
    private TenantRepository tenantRepository;
    private AuditService auditService;
    private AlertaService service;

    @BeforeEach
    void setUp() {
        alertaRepository = mock(AlertaRepository.class);
        tenantRepository = mock(TenantRepository.class);
        auditService = mock(AuditService.class);
        service = new AlertaService(alertaRepository, tenantRepository, auditService);
    }

    private Alerta alerta(Long id, String status) {
        return Alerta.builder()
                .id(id)
                .companyId(2L)
                .severity("CRITICAL")
                .tipo("CERTIFICATE_EXPIRED")
                .titulo("Certificado vencido")
                .descripcion("El certificado de Empresa ABC venció")
                .status(status)
                .creadaEn(LocalDateTime.of(2026, 9, 8, 10, 0))
                .build();
    }

    @Test
    void listarFiltraPorSeveridadYStatusYResuelveNombreDeEmpresa() {
        when(tenantRepository.findAllById(anyCollection()))
                .thenReturn(List.of(Tenant.builder().id(2L).nombre("Empresa ABC").build()));
        when(alertaRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(alerta(1L, "OPEN"))));

        Paginated<AlertaResponse> resultado = service.listar(0, 20, "CRITICAL", "OPEN", 2L, "CERTIFICATE_EXPIRED");

        assertEquals(1, resultado.content().size());
        AlertaResponse r = resultado.content().get(0);
        assertEquals("CRITICAL", r.severity());
        assertEquals("CERTIFICATE_EXPIRED", r.type());
        assertEquals("Empresa ABC", r.companyNombre());
        assertEquals("OPEN", r.status());
        verify(alertaRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void marcarLeidaSeteaStatusYLeidaEn() {
        Alerta alerta = alerta(1L, "OPEN");
        when(alertaRepository.findById(1L)).thenReturn(Optional.of(alerta));
        when(alertaRepository.save(any(Alerta.class))).thenAnswer(inv -> inv.getArgument(0));

        AlertaResponse resultado = service.marcarLeida(1L);

        assertEquals("READ", resultado.status());
        assertNotNull(resultado.readAt());
        assertNull(resultado.resolvedAt());
    }

    @Test
    void resolverRequiereQueLaAlertaExista() {
        when(alertaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.resolver(99L, null));
    }

    @Test
    void resolverMarcaResueltaYAudita() {
        Alerta alerta = alerta(1L, "OPEN");
        when(alertaRepository.findById(1L)).thenReturn(Optional.of(alerta));
        when(alertaRepository.save(any(Alerta.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tenantRepository.findById(2L))
                .thenReturn(Optional.of(Tenant.builder().id(2L).nombre("Empresa ABC").build()));

        AlertaResponse resultado = service.resolver(1L, "Se renovó el certificado");

        assertEquals("RESOLVED", resultado.status());
        assertNotNull(resultado.resolvedAt());
        verify(auditService).registrar(eq("ALERTA_RESUELTA"), eq("alertas"), any(), eq("alerta"), eq(1L),
                eq("{ \"status\": \"OPEN\" }"),
                eq("{ \"status\": \"RESOLVED\", \"motivo\": \"Se renovó el certificado\" }"));
    }

    @Test
    void crearEvitaDuplicadoAbiertoIdentico() {
        when(alertaRepository.existsByCompanyIdAndTipoAndTituloAndSeverityAndStatus(
                2L, "CERTIFICATE_EXPIRED", "Título", "CRITICAL", "OPEN")).thenReturn(true);

        Alerta resultado = service.crear(2L, "critical", "CERTIFICATE_EXPIRED", "Título", "desc");

        assertNull(resultado);
        verify(alertaRepository, never()).save(any());
    }

    @Test
    void crearRechazaSeveridadInvalida() {
        assertThrows(IllegalArgumentException.class, () -> service.crear(2L, "URGENTE", "X", "T", "d"));
    }

    @Test
    void resumenCuentaAbiertasPorSeveridad() {
        when(alertaRepository.countBySeverityAndStatus("CRITICAL", "OPEN")).thenReturn(2L);
        when(alertaRepository.countBySeverityAndStatus("WARNING", "OPEN")).thenReturn(5L);
        when(alertaRepository.countBySeverityAndStatus("INFO", "OPEN")).thenReturn(1L);

        Map<String, Long> resumen = service.resumenAbiertas();

        assertEquals(2L, resumen.get("critical"));
        assertEquals(5L, resumen.get("warning"));
        assertEquals(1L, resumen.get("info"));
    }
}