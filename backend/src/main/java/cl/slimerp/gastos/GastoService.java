package cl.slimerp.gastos;

import cl.slimerp.common.PaginaResponse;
import cl.slimerp.config.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class GastoService {

    private final GastoRepository gastoRepository;
    private final CategoriaGastoRepository categoriaGastoRepository;

    public GastoService(GastoRepository gastoRepository, CategoriaGastoRepository categoriaGastoRepository) {
        this.gastoRepository = gastoRepository;
        this.categoriaGastoRepository = categoriaGastoRepository;
    }

    @Transactional
    public Gasto crear(GastoRequest request) {
        Long tenantId = TenantContext.getTenantId();
        validarCategoria(tenantId, request.categoriaGastoId());

        Gasto gasto = Gasto.builder()
                .tenantId(tenantId)
                .categoriaGastoId(request.categoriaGastoId())
                .monto(request.monto())
                .descripcion(request.descripcion())
                .fecha(request.fecha())
                .build();
        return gastoRepository.save(gasto);
    }

    // Usado únicamente por GastoRecurrenteGeneratorJob: a diferencia de crear(),
    // deja registrado de qué plantilla proviene la instancia generada.
    @Transactional
    public Gasto crearDesdeRecurrente(GastoRecurrente recurrente, LocalDate fecha) {
        Gasto gasto = Gasto.builder()
                .tenantId(recurrente.getTenantId())
                .categoriaGastoId(recurrente.getCategoriaGastoId())
                .gastoRecurrenteId(recurrente.getId())
                .monto(recurrente.getMonto())
                .descripcion(recurrente.getDescripcion())
                .fecha(fecha)
                .build();
        return gastoRepository.save(gasto);
    }

    @Transactional
    public Gasto actualizar(Long id, GastoRequest request) {
        Long tenantId = TenantContext.getTenantId();
        validarCategoria(tenantId, request.categoriaGastoId());

        Gasto gasto = gastoRepository.findByIdAndTenantIdAndActivoTrue(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Gasto no encontrado: " + id));
        gasto.setCategoriaGastoId(request.categoriaGastoId());
        gasto.setMonto(request.monto());
        gasto.setDescripcion(request.descripcion());
        gasto.setFecha(request.fecha());
        return gastoRepository.save(gasto);
    }

    @Transactional
    public void eliminar(Long id) {
        Long tenantId = TenantContext.getTenantId();
        Gasto gasto = gastoRepository.findByIdAndTenantIdAndActivoTrue(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Gasto no encontrado: " + id));
        gasto.setActivo(false);
        gastoRepository.save(gasto);
    }

    public Gasto obtener(Long id) {
        return gastoRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Gasto no encontrado: " + id));
    }

    public PaginaResponse<Gasto> buscar(Long categoriaGastoId, LocalDate fechaDesde, LocalDate fechaHasta,
                                         String busqueda, int pagina, int tamano) {
        if (pagina < 0) throw new IllegalArgumentException("La página debe ser 0 o mayor");
        if (tamano < 1) throw new IllegalArgumentException("El tamaño de página debe ser mayor que 0");
        Long tenantId = TenantContext.getTenantId();

        Specification<Gasto> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("tenantId"), tenantId), cb.isTrue(root.get("activo")));
        if (categoriaGastoId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("categoriaGastoId"), categoriaGastoId));
        }
        if (fechaDesde != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("fecha"), fechaDesde));
        }
        if (fechaHasta != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("fecha"), fechaHasta));
        }
        if (busqueda != null && !busqueda.isBlank()) {
            String patron = "%" + busqueda.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("descripcion")), patron));
        }

        var pageable = PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "fecha"));
        return PaginaResponse.de(gastoRepository.findAll(spec, pageable));
    }

    private void validarCategoria(Long tenantId, Long categoriaGastoId) {
        categoriaGastoRepository.findByIdAndTenantIdAndActivoTrue(categoriaGastoId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Categoría de gasto no encontrada: " + categoriaGastoId));
    }
}
