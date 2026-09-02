package cl.slimerp.tesoreria;

import cl.slimerp.config.TenantContext;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TransaccionPagoService {

    private final TransaccionPagoRepository transaccionPagoRepository;
    private final CuentaPorCobrarRepository cuentaPorCobrarRepository;

    public TransaccionPagoService(TransaccionPagoRepository transaccionPagoRepository,
                                   CuentaPorCobrarRepository cuentaPorCobrarRepository) {
        this.transaccionPagoRepository = transaccionPagoRepository;
        this.cuentaPorCobrarRepository = cuentaPorCobrarRepository;
    }

    @Transactional
    public TransaccionPago registrarPago(Long cuentaId, TransaccionPagoRequest request, Long usuarioId) {
        Long tenantId = TenantContext.getTenantId();
        CuentaPorCobrar cuenta = cuentaPorCobrarRepository.findByIdAndTenantId(cuentaId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta por cobrar no encontrada: " + cuentaId));

        if (cuenta.getEstado() != EstadoCuentaPorCobrar.DEUDA && cuenta.getEstado() != EstadoCuentaPorCobrar.PARCIAL) {
            throw new IllegalArgumentException("No se puede registrar un pago sobre una cuenta " + cuenta.getEstado());
        }
        if (request.monto().compareTo(cuenta.getSaldoPendiente()) > 0) {
            throw new IllegalArgumentException(
                    "El monto excede el saldo pendiente (" + cuenta.getSaldoPendiente() + ")");
        }
        validarDatosMedioPago(request);

        TransaccionPago pago = TransaccionPago.builder()
                .tenantId(tenantId)
                .cuentaPorCobrarId(cuenta.getId())
                .ventaId(cuenta.getVentaId())
                .clienteId(cuenta.getClienteId())
                .monto(request.monto())
                .medioPago(request.medioPago())
                .observaciones(request.observaciones())
                .transferenciaBancoOrigen(request.transferenciaBancoOrigen())
                .transferenciaBancoDestino(request.transferenciaBancoDestino())
                .transferenciaNumeroOperacion(request.transferenciaNumeroOperacion())
                .transferenciaFecha(request.transferenciaFecha())
                .tarjetaEntidad(request.tarjetaEntidad())
                .tarjetaTipo(request.tarjetaTipo())
                .tarjetaNumeroOperacion(request.tarjetaNumeroOperacion())
                .tarjetaFecha(request.tarjetaFecha())
                .chequeBanco(request.chequeBanco())
                .chequeNumero(request.chequeNumero())
                .chequeFechaEmision(request.chequeFechaEmision())
                .chequeFechaPago(request.chequeFechaPago())
                .usuarioId(usuarioId)
                .build();
        pago = transaccionPagoRepository.save(pago);

        cuenta.setMontoPagado(cuenta.getMontoPagado().add(request.monto()));
        cuenta.setSaldoPendiente(cuenta.getMontoTotal().subtract(cuenta.getMontoPagado()));
        cuenta.setEstado(cuenta.getSaldoPendiente().signum() == 0
                ? EstadoCuentaPorCobrar.PAGADO
                : EstadoCuentaPorCobrar.PARCIAL);
        cuenta.setFechaUltimoPago(pago.getFecha());
        cuentaPorCobrarRepository.save(cuenta);

        return pago;
    }

    private void validarDatosMedioPago(TransaccionPagoRequest request) {
        switch (request.medioPago()) {
            case TRANSFERENCIA -> {
                if (esVacio(request.transferenciaBancoOrigen()) || esVacio(request.transferenciaBancoDestino())) {
                    throw new IllegalArgumentException("Debes indicar el banco de origen y destino de la transferencia");
                }
            }
            case TARJETA -> {
                if (esVacio(request.tarjetaEntidad())) {
                    throw new IllegalArgumentException("Debes indicar la entidad de la tarjeta");
                }
            }
            case CHEQUE -> {
                if (esVacio(request.chequeBanco()) || esVacio(request.chequeNumero())) {
                    throw new IllegalArgumentException("Debes indicar el banco y número del cheque");
                }
            }
            case EFECTIVO -> {
                // Sin datos adicionales requeridos.
            }
        }
    }

    private boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }

    public List<TransaccionPago> listarPorCuenta(Long cuentaId) {
        return transaccionPagoRepository.findByTenantIdAndCuentaPorCobrarIdOrderByFechaDesc(
                TenantContext.getTenantId(), cuentaId);
    }

    public List<TransaccionPago> buscar(Long clienteId, EstadoTransaccion estado, MedioPago medioPago,
                                         LocalDateTime fechaDesde, LocalDateTime fechaHasta) {
        Long tenantId = TenantContext.getTenantId();

        // Se arma dinámicamente en vez de usar "(:param IS NULL OR ...)" en JPQL:
        // el driver de Postgres no logra inferir el tipo de un parámetro que solo
        // se compara contra IS NULL, y falla con "could not determine data type".
        Specification<TransaccionPago> spec = (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
        if (clienteId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("clienteId"), clienteId));
        }
        if (estado != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("estado"), estado));
        }
        if (medioPago != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("medioPago"), medioPago));
        }
        if (fechaDesde != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("fecha"), fechaDesde));
        }
        if (fechaHasta != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("fecha"), fechaHasta));
        }

        return transaccionPagoRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "fecha"));
    }

    public TransaccionPago obtener(Long id) {
        return transaccionPagoRepository.findByIdAndTenantId(id, TenantContext.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Transacción no encontrada: " + id));
    }

    @Transactional
    public TransaccionPago anular(Long id, String motivo, Long usuarioId) {
        Long tenantId = TenantContext.getTenantId();
        TransaccionPago pago = obtener(id);

        if (pago.getEstado() == EstadoTransaccion.ANULADA) {
            throw new IllegalArgumentException("La transacción ya está anulada");
        }

        CuentaPorCobrar cuenta = cuentaPorCobrarRepository.findByIdAndTenantId(pago.getCuentaPorCobrarId(), tenantId)
                .orElseThrow(() -> new IllegalStateException("Cuenta por cobrar no encontrada: " + pago.getCuentaPorCobrarId()));
        if (cuenta.getEstado() == EstadoCuentaPorCobrar.ANULADO) {
            throw new IllegalArgumentException("La cuenta asociada está anulada");
        }

        pago.setEstado(EstadoTransaccion.ANULADA);
        pago.setUsuarioAnuloId(usuarioId);
        pago.setFechaAnulacion(LocalDateTime.now());
        pago.setMotivoAnulacion(motivo);
        transaccionPagoRepository.save(pago);

        // Recalcula la cuenta desde cero sumando solo los pagos aún confirmados.
        BigDecimal montoPagado = transaccionPagoRepository
                .findByTenantIdAndCuentaPorCobrarIdAndEstado(tenantId, cuenta.getId(), EstadoTransaccion.CONFIRMADA)
                .stream()
                .map(TransaccionPago::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        cuenta.setMontoPagado(montoPagado);
        cuenta.setSaldoPendiente(cuenta.getMontoTotal().subtract(montoPagado));
        cuenta.setEstado(montoPagado.signum() == 0
                ? EstadoCuentaPorCobrar.DEUDA
                : cuenta.getSaldoPendiente().signum() == 0 ? EstadoCuentaPorCobrar.PAGADO : EstadoCuentaPorCobrar.PARCIAL);
        cuentaPorCobrarRepository.save(cuenta);

        return pago;
    }
}
