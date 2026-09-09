package cl.slimerp.admin.cobranza;

import cl.slimerp.admin.tenant.Tenant;
import cl.slimerp.admin.tenant.TenantRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CobranzaService {

    private static final String PLAN_PLATAFORMA = "plataforma";

    private final CobranzaEmpresaRepository cobranzaEmpresaRepository;
    private final CobranzaPagoRepository cobranzaPagoRepository;
    private final TenantRepository tenantRepository;

    public CobranzaService(CobranzaEmpresaRepository cobranzaEmpresaRepository,
                           CobranzaPagoRepository cobranzaPagoRepository,
                           TenantRepository tenantRepository) {
        this.cobranzaEmpresaRepository = cobranzaEmpresaRepository;
        this.cobranzaPagoRepository = cobranzaPagoRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public CobranzaEmpresa emitir(EmitirCobranzaRequest request, Long usuarioAdminId) {
        Tenant tenant = tenantRepository.findById(request.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada: " + request.tenantId()));
        if (PLAN_PLATAFORMA.equals(tenant.getPlan())) {
            throw new IllegalArgumentException("No se puede emitir cobranza al tenant de plataforma");
        }

        return cobranzaEmpresaRepository.save(CobranzaEmpresa.builder()
                .tenantId(tenant.getId())
                .concepto(request.concepto())
                .periodo(request.periodo())
                .montoTotal(request.montoTotal())
                .saldoPendiente(request.montoTotal())
                .fechaVencimiento(request.fechaVencimiento())
                .observaciones(request.observaciones())
                .usuarioAdminId(usuarioAdminId)
                .build());
    }

    public List<CobranzaEmpresa> listar(Long empresaId, EstadoCobranza estado,
                                        LocalDateTime fechaDesde, LocalDateTime fechaHasta) {
        Specification<CobranzaEmpresa> spec = (root, query, cb) -> cb.conjunction();
        if (empresaId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("tenantId"), empresaId));
        }
        if (estado != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("estado"), estado));
        }
        if (fechaDesde != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("fechaEmision"), fechaDesde));
        }
        if (fechaHasta != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("fechaEmision"), fechaHasta));
        }

        return cobranzaEmpresaRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "fechaEmision"));
    }

    public CobranzaEmpresa obtener(Long id) {
        return cobranzaEmpresaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cobranza no encontrada: " + id));
    }

    public List<CobranzaPago> listarPagos(Long cobranzaId) {
        return cobranzaPagoRepository.findByCobranzaEmpresaIdOrderByFechaDesc(cobranzaId);
    }

    @Transactional
    public CobranzaPago registrarPago(Long cobranzaId, PagoCobranzaRequest request, Long usuarioAdminId) {
        CobranzaEmpresa cobranza = obtener(cobranzaId);

        if (cobranza.getEstado() != EstadoCobranza.DEUDA && cobranza.getEstado() != EstadoCobranza.PARCIAL) {
            throw new IllegalArgumentException("No se puede registrar un pago sobre una cobranza " + cobranza.getEstado());
        }
        if (request.monto().compareTo(cobranza.getSaldoPendiente()) > 0) {
            throw new IllegalArgumentException(
                    "El monto excede el saldo pendiente (" + cobranza.getSaldoPendiente() + ")");
        }

        CobranzaPago pago = cobranzaPagoRepository.save(CobranzaPago.builder()
                .cobranzaEmpresaId(cobranza.getId())
                .tenantId(cobranza.getTenantId())
                .monto(request.monto())
                .medioPago(request.medioPago())
                .numeroOperacion(request.numeroOperacion())
                .observaciones(request.observaciones())
                .usuarioAdminId(usuarioAdminId)
                .build());

        cobranza.setMontoPagado(cobranza.getMontoPagado().add(request.monto()));
        cobranza.setSaldoPendiente(cobranza.getMontoTotal().subtract(cobranza.getMontoPagado()));
        cobranza.setEstado(cobranza.getSaldoPendiente().signum() == 0
                ? EstadoCobranza.PAGADO
                : EstadoCobranza.PARCIAL);
        cobranza.setFechaUltimoPago(pago.getFecha());
        cobranzaEmpresaRepository.save(cobranza);

        return pago;
    }

    @Transactional
    public CobranzaPago anularPago(Long pagoId, String motivo, Long usuarioAdminId) {
        CobranzaPago pago = cobranzaPagoRepository.findById(pagoId)
                .orElseThrow(() -> new IllegalArgumentException("Pago no encontrado: " + pagoId));
        if (pago.getEstado() == EstadoPagoCobranza.ANULADA) {
            throw new IllegalArgumentException("El pago ya está anulado");
        }

        CobranzaEmpresa cobranza = obtener(pago.getCobranzaEmpresaId());
        if (cobranza.getEstado() == EstadoCobranza.ANULADO) {
            throw new IllegalArgumentException("La cobranza asociada está anulada");
        }

        pago.setEstado(EstadoPagoCobranza.ANULADA);
        pago.setUsuarioAdminId(usuarioAdminId);
        pago.setFechaAnulacion(LocalDateTime.now());
        pago.setMotivoAnulacion(motivo);
        cobranzaPagoRepository.save(pago);

        // Recalcula la cobranza desde cero sumando solo los pagos confirmados.
        BigDecimal montoPagado = cobranzaPagoRepository
                .findByCobranzaEmpresaIdAndEstado(cobranza.getId(), EstadoPagoCobranza.CONFIRMADA)
                .stream()
                .map(CobranzaPago::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        cobranza.setMontoPagado(montoPagado);
        cobranza.setSaldoPendiente(cobranza.getMontoTotal().subtract(montoPagado));
        cobranza.setEstado(montoPagado.signum() == 0
                ? EstadoCobranza.DEUDA
                : cobranza.getSaldoPendiente().signum() == 0 ? EstadoCobranza.PAGADO : EstadoCobranza.PARCIAL);
        cobranzaEmpresaRepository.save(cobranza);

        return pago;
    }

    @Transactional
    public CobranzaEmpresa anular(Long id, String motivo, Long usuarioAdminId) {
        CobranzaEmpresa cobranza = obtener(id);
        if (cobranza.getEstado() == EstadoCobranza.ANULADO) {
            throw new IllegalArgumentException("La cobranza ya está anulada");
        }
        if (cobranza.getEstado() == EstadoCobranza.PAGADO) {
            throw new IllegalArgumentException("No se puede anular una cobranza pagada");
        }

        cobranza.setEstado(EstadoCobranza.ANULADO);
        cobranza.setUsuarioAdminId(usuarioAdminId);
        cobranza.setFechaAnulacion(LocalDateTime.now());
        cobranza.setMotivoAnulacion(motivo);
        return cobranzaEmpresaRepository.save(cobranza);
    }

    public ResumenCobranza resumen() {
        List<CobranzaEmpresa> todas = cobranzaEmpresaRepository.findAll();
        BigDecimal totalEmitido = BigDecimal.ZERO;
        BigDecimal totalCobrado = BigDecimal.ZERO;
        BigDecimal saldoPendiente = BigDecimal.ZERO;
        long enDeuda = 0;
        long parciales = 0;
        long pagadas = 0;

        for (CobranzaEmpresa c : todas) {
            if (c.getEstado() == EstadoCobranza.ANULADO) {
                continue;
            }
            totalEmitido = totalEmitido.add(c.getMontoTotal());
            totalCobrado = totalCobrado.add(c.getMontoPagado());
            saldoPendiente = saldoPendiente.add(c.getSaldoPendiente());
            enDeuda += c.getEstado() == EstadoCobranza.DEUDA ? 1 : 0;
            parciales += c.getEstado() == EstadoCobranza.PARCIAL ? 1 : 0;
            pagadas += c.getEstado() == EstadoCobranza.PAGADO ? 1 : 0;
        }

        return new ResumenCobranza(totalEmitido, totalCobrado, saldoPendiente, enDeuda, parciales, pagadas);
    }

    public BigDecimal saldoPendienteTenant(Long tenantId) {
        return cobranzaEmpresaRepository.sumSaldoPendienteTenant(tenantId,
                List.of(EstadoCobranza.DEUDA, EstadoCobranza.PARCIAL));
    }
}