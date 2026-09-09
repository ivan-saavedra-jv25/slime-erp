package cl.slimerp.admin.auditoria;

import cl.slimerp.admin.auth.AdminActual;
import cl.slimerp.admin.common.Paginated;
import cl.slimerp.admin.tenant.Tenant;
import cl.slimerp.admin.usuario.AdminUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditServiceTest {

    private AuditLogRepository auditLogRepository;
    private AdminActual adminActual;
    private AuditService service;

    @BeforeEach
    void setUp() {
        auditLogRepository = mock(AuditLogRepository.class);
        adminActual = mock(AdminActual.class);
        service = new AuditService(auditLogRepository, adminActual);
    }

    private AuditLog registro(Long id, String modulo, String action) {
        return AuditLog.builder()
                .id(id)
                .adminUsuario(AdminUsuario.builder()
                        .id(7L).nombre("Admin Kim").email("admin@slimerp.cl")
                        .rol(cl.slimerp.admin.rbac.AdminRol.SUPER_ADMIN)
                        .build())
                .company(Tenant.builder().id(2L).nombre("Empresa ABC").build())
                .action(action)
                .modulo(modulo)
                .entityType("suscripcion")
                .entityId(3L)
                .oldValue("{\"fecha\":\"2026-09-08\"}")
                .newValue("{\"fecha\":\"2026-10-08\"}")
                .creadoEn(LocalDateTime.of(2026, 9, 8, 19, 30))
                .build();
    }

    @Test
    void registrarPersisteAccionConOldYNew() {
        when(adminActual.id()).thenReturn(7L);
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditLog guardado = service.registrar("PLAN_CHANGE", "suscripciones", null,
                "suscripcion", 3L, "{\"a\":1}", "{\"a\":2}");

        assertEquals("PLAN_CHANGE", guardado.getAction());
        assertEquals("suscripciones", guardado.getModulo());
        assertEquals("{\"a\":1}", guardado.getOldValue());
        assertEquals("{\"a\":2}", guardado.getNewValue());
        assertNotNull(guardado.getAdminUsuario());
        assertEquals(7L, guardado.getAdminUsuario().getId());
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void registrarIgnoraContextoSinSesion() {
        when(adminActual.id()).thenThrow(new IllegalStateException("sin sesión"));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditLog guardado = service.registrar("LOGOUT", "seguridad", null, null, null, null, null);

        assertNull(guardado.getAdminUsuario());
    }

    @Test
    void registrarNoLanzaCuandoElRepositorioFalla() {
        when(adminActual.id()).thenReturn(1L);
        when(auditLogRepository.save(any(AuditLog.class)))
                .thenThrow(new RuntimeException("base de datos caída"));

        assertDoesNotThrow(() -> service.registrar("CREATE", "planes", null, "plan", 1L, null, null));
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void listarFiltraPorCombinacionesYPagina() {
        AuditLog registro = registro(1L, "suscripciones", "PLAN_CHANGE");
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(registro)));

        Paginated<AuditLogResponse> resultado = service.listar(0, 20, 7L, 2L,
                "suscripciones", "PLAN_CHANGE",
                LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 30, 23, 59));

        assertEquals(1, resultado.content().size());
        AuditLogResponse r = resultado.content().get(0);
        assertEquals("PLAN_CHANGE", r.action());
        assertEquals("suscripciones", r.modulo());
        assertEquals(7L, r.adminUserId());
        assertEquals("Admin Kim", r.adminNombre());
        assertEquals(2L, r.companyId());
        assertEquals("Empresa ABC", r.companyNombre());
        assertEquals("{\"fecha\":\"2026-09-08\"}", r.oldValue());
        verify(auditLogRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void listarSinFiltrosDevuelveTodoPaginado() {
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(registro(1L, "empresas", "EMPRESA_CREADA"))));

        Paginated<AuditLogResponse> resultado = service.listar(0, 10, null, null, null, null, null, null);

        assertEquals(1, resultado.content().size());
        verify(auditLogRepository).findAll(any(Specification.class), any(Pageable.class));
    }
}