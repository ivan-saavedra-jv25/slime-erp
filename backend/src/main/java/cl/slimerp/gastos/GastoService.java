package cl.slimerp.gastos;

import cl.slimerp.common.PaginaResponse;
import cl.slimerp.config.TenantContext;
import cl.slimerp.tesoreria.CuentaPorPagar;
import cl.slimerp.tesoreria.CuentaPorPagarService;
import cl.slimerp.tesoreria.EstadoCuentaPorPagar;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class GastoService {

    private final GastoRepository gastoRepository;
    private final CategoriaGastoRepository categoriaGastoRepository;
    private final CuentaPorPagarService cuentaPorPagarService;

    public GastoService(GastoRepository gastoRepository, CategoriaGastoRepository categoriaGastoRepository,
                         CuentaPorPagarService cuentaPorPagarService) {
        this.gastoRepository = gastoRepository;
        this.categoriaGastoRepository = categoriaGastoRepository;
        this.cuentaPorPagarService = cuentaPorPagarService;
    }

    @Transactional
    public Gasto crear(GastoRequest request) {
        Long tenantId = TenantContext.getTenantId();
        CategoriaGasto categoria = validarCategoria(tenantId, request.categoriaGastoId());

        Gasto gasto = Gasto.builder()
                .tenantId(tenantId)
                .categoriaGastoId(request.categoriaGastoId())
                .monto(request.monto())
                .descripcion(request.descripcion())
                .fecha(request.fecha())
                .build();
        gasto = gastoRepository.save(gasto);
        cuentaPorPagarService.crearParaGasto(gasto, categoria.getNombre());
        return gasto;
    }

    // Usado únicamente por GastoRecurrenteGeneratorJob: a diferencia de crear(),
    // deja registrado de qué plantilla proviene la instancia generada. Valida
    // la categoría igual que crear() — si la plantilla apunta a una categoría
    // borrada, el job la salta (ver el try/catch por plantilla en
    // GastoRecurrenteGeneratorJob) en vez de generar un gasto sin categoría.
    @Transactional
    public Gasto crearDesdeRecurrente(GastoRecurrente recurrente, LocalDate fecha) {
        CategoriaGasto categoria = validarCategoria(recurrente.getTenantId(), recurrente.getCategoriaGastoId());

        Gasto gasto = Gasto.builder()
                .tenantId(recurrente.getTenantId())
                .categoriaGastoId(recurrente.getCategoriaGastoId())
                .gastoRecurrenteId(recurrente.getId())
                .monto(recurrente.getMonto())
                .descripcion(recurrente.getDescripcion())
                .fecha(fecha)
                .build();
        gasto = gastoRepository.save(gasto);
        cuentaPorPagarService.crearParaGasto(gasto, categoria.getNombre());
        return gasto;
    }

    @Transactional
    public Gasto actualizar(Long id, GastoRequest request) {
        Long tenantId = TenantContext.getTenantId();
        validarCategoria(tenantId, request.categoriaGastoId());
        validarGastoNoGestionado(id, "modificar");

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
        validarGastoNoGestionado(id, "eliminar");
        Gasto gasto = gastoRepository.findByIdAndTenantIdAndActivoTrue(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Gasto no encontrado: " + id));
        gasto.setActivo(false);
        gastoRepository.save(gasto);
    }

    public Gasto obtener(Long id) {
        Gasto gasto = gastoRepository.findByIdAndTenantIdAndActivoTrue(id, TenantContext.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Gasto no encontrado: " + id));
        poblarEstadoCuentaPorPagar(gasto);
        return gasto;
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
        PaginaResponse<Gasto> respuesta = PaginaResponse.de(gastoRepository.findAll(spec, pageable));
        poblarEstadoCuentaPorPagar(respuesta.contenido());
        return respuesta;
    }

    private void validarGastoNoGestionado(Long id, String accion) {
        cuentaPorPagarService.encontrarPorGasto(id)
                .filter(cuenta -> cuenta.getEstado() != EstadoCuentaPorPagar.ANULADO)
                .ifPresent(cuenta -> {
                    throw new IllegalArgumentException(
                            "No puedes " + accion + " este gasto porque ya está gestionado en Tesorería "
                                    + "(Cuenta por Pagar #" + cuenta.getId() + "). Anula la cuenta "
                                    + "en Tesorería > Cuentas por Pagar para continuar.");
                });
    }

    private void poblarEstadoCuentaPorPagar(List<Gasto> gastos) {
        var estados = cuentaPorPagarService.encontrarPorGastos(gastos.stream().map(Gasto::getId).toList());
        gastos.forEach(gasto -> poblarEstadoCuentaPorPagar(gasto, estados));
    }

    private void poblarEstadoCuentaPorPagar(Gasto gasto) {
        cuentaPorPagarService.encontrarPorGasto(gasto.getId())
                .ifPresent(cuenta -> gasto.setCuentaPorPagarEstado(cuenta.getEstado().name()));
    }

    private void poblarEstadoCuentaPorPagar(Gasto gasto, java.util.Map<Long, CuentaPorPagar> estados) {
        CuentaPorPagar cuenta = estados.get(gasto.getId());
        if (cuenta != null) {
            gasto.setCuentaPorPagarEstado(cuenta.getEstado().name());
        }
    }

    private CategoriaGasto validarCategoria(Long tenantId, Long categoriaGastoId) {
        return categoriaGastoRepository.findByIdAndTenantIdAndActivoTrue(categoriaGastoId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Categoría de gasto no encontrada: " + categoriaGastoId));
    }
}
