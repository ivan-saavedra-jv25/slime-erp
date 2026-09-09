package cl.slimerp.admin.dte;

import cl.slimerp.admin.common.Paginated;
import cl.slimerp.admin.tenant.Tenant;
import cl.slimerp.admin.tenant.TenantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Monitoreo global de documentos de venta del ERP (spec §16, solo lectura). */
@Service
public class DteService {

    private static final java.util.List<String> ESTADOS = java.util.List.of("EMITIDA", "ANULADA");

    private final VentaDteRepository ventaDteRepository;
    private final TenantRepository tenantRepository;

    public DteService(VentaDteRepository ventaDteRepository, TenantRepository tenantRepository) {
        this.ventaDteRepository = ventaDteRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public Paginated<DteResponse> listar(int page, int limit, Long empresaId, String tipoDte,
                                         String estado, LocalDate fechaDesde, LocalDate fechaHasta,
                                         Integer folio, String rutReceptor) {
        Specification<VentaDte> spec = specFiltros(empresaId, tipoDte, fechaDesde, fechaHasta);
        if (estado != null && !estado.isBlank()) {
            String estadoNorm = estado.trim().toUpperCase();
            if (!ESTADOS.contains(estadoNorm)) {
                throw new IllegalArgumentException(
                        "Estado DTE no disponible: " + estado + " (disponibles: " + String.join(", ", ESTADOS) + ")");
            }
            spec = spec.and((root, query, cb) -> estadoNorm.equals("EMITIDA")
                    ? cb.isTrue(root.get("activo"))
                    : cb.isFalse(root.get("activo")));
        }
        if (folio != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("folio"), folio));
        }
        if (rutReceptor != null && !rutReceptor.isBlank()) {
            String rut = rutReceptor.trim().toLowerCase();
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.join("cliente").get("rut")), "%" + rut + "%"));
        }

        int pagina = Math.max(0, page);
        int tamano = Math.min(Math.max(1, limit), 100);
        Page<VentaDte> paginaDocs = ventaDteRepository.findAll(spec,
                PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "fecha")));

        return Paginated.desde(paginaDocs.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public DteDashboardResponse dashboard(Long empresaId, String tipoDte,
                                          LocalDate fechaDesde, LocalDate fechaHasta) {
        Specification<VentaDte> spec = specFiltros(empresaId, tipoDte, fechaDesde, fechaHasta);
        long emitidos = ventaDteRepository.count(spec);
        long anulados = ventaDteRepository.count(spec.and((root, query, cb) -> cb.isFalse(root.get("activo"))));
        return new DteDashboardResponse(emitidos, anulados);
    }

    private Specification<VentaDte> specFiltros(Long empresaId, String tipoDte,
                                                LocalDate fechaDesde, LocalDate fechaHasta) {
        Specification<VentaDte> spec = (root, query, cb) -> cb.conjunction();
        if (empresaId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("tenantId"), empresaId));
        }
        if (tipoDte != null && !tipoDte.isBlank()) {
            String tipo = tipoDte.trim().toUpperCase();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("tipoDocumento"), tipo));
        }
        if (fechaDesde != null) {
            LocalDateTime desde = fechaDesde.atStartOfDay();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("fecha"), desde));
        }
        if (fechaHasta != null) {
            LocalDateTime hasta = fechaHasta.atTime(LocalTime.MAX);
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("fecha"), hasta));
        }
        return spec;
    }

    private DteResponse toResponse(VentaDte venta) {
        return new DteResponse(
                venta.getId(),
                venta.getTenantId(),
                nombreEmpresa(venta.getTenantId()),
                venta.getTipoDocumento(),
                venta.getCodigoSii(),
                venta.isExento(),
                venta.getFolio(),
                venta.getCliente() != null ? venta.getCliente().getRut() : null,
                venta.getCliente() != null ? venta.getCliente().getRazonSocial() : null,
                venta.getFecha(),
                venta.getMontoTotal(),
                venta.isActivo() ? "EMITIDA" : "ANULADA");
    }

    private String nombreEmpresa(Long companyId) {
        return tenantRepository.findById(companyId).map(Tenant::getNombre).orElse(null);
    }
}