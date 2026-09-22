package cl.slimerp.tesoreria;

import cl.slimerp.common.PaginaResponse;
import cl.slimerp.config.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TransaccionPagoCompraService {

    private final TransaccionPagoCompraRepository transaccionPagoCompraRepository;
    private final CuentaPorPagarRepository cuentaPorPagarRepository;

    public TransaccionPagoCompraService(TransaccionPagoCompraRepository transaccionPagoCompraRepository,
                                         CuentaPorPagarRepository cuentaPorPagarRepository) {
        this.transaccionPagoCompraRepository = transaccionPagoCompraRepository;
        this.cuentaPorPagarRepository = cuentaPorPagarRepository;
    }

    @Transactional
    public TransaccionPagoCompra registrarPago(Long cuentaId, TransaccionPagoRequest request, Long usuarioId) {
        Long tenantId = TenantContext.getTenantId();
        CuentaPorPagar cuenta = cuentaPorPagarRepository.findByIdAndTenantId(cuentaId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta por pagar no encontrada: " + cuentaId));

        if (cuenta.getEstado() != EstadoCuentaPorPagar.DEUDA && cuenta.getEstado() != EstadoCuentaPorPagar.PARCIAL) {
            throw new IllegalArgumentException("No se puede registrar un pago sobre una cuenta " + cuenta.getEstado());
        }
        if (request.monto().compareTo(cuenta.getSaldoPendiente()) > 0) {
            throw new IllegalArgumentException(
                    "El monto excede el saldo pendiente (" + cuenta.getSaldoPendiente() + ")");
        }
        validarDatosMedioPago(request);

        TransaccionPagoCompra.TransaccionPagoCompraBuilder pagoBuilder = TransaccionPagoCompra.builder()
                .tenantId(tenantId)
                .cuentaPorPagarId(cuenta.getId())
                .compraId(cuenta.getCompraId())
                .gastoId(cuenta.getGastoId())
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
                .usuarioId(usuarioId);
        if (request.fecha() != null) {
            // Permite registrar pagos realizados en fechas anteriores (ej.
            // pagos de meses previos). Si no se indica, la entidad usa now().
            pagoBuilder.fecha(request.fecha());
        }
        TransaccionPagoCompra pago = pagoBuilder.build();
        pago = transaccionPagoCompraRepository.save(pago);

        cuenta.setMontoPagado(cuenta.getMontoPagado().add(request.monto()));
        cuenta.setSaldoPendiente(cuenta.getMontoTotal().subtract(cuenta.getMontoPagado()));
        cuenta.setEstado(cuenta.getSaldoPendiente().signum() == 0
                ? EstadoCuentaPorPagar.PAGADO
                : EstadoCuentaPorPagar.PARCIAL);
        cuenta.setFechaUltimoPago(pago.getFecha());
        cuentaPorPagarRepository.save(cuenta);

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

    public List<TransaccionPagoCompra> listarPorCuenta(Long cuentaId) {
        return transaccionPagoCompraRepository.findByTenantIdAndCuentaPorPagarIdOrderByFechaDesc(
                TenantContext.getTenantId(), cuentaId);
    }

    public PaginaResponse<TransaccionPagoCompra> buscar(EstadoTransaccion estado, MedioPago medioPago,
                                                          LocalDateTime fechaDesde, LocalDateTime fechaHasta,
                                                          int pagina, int tamano) {
        if (pagina < 0) throw new IllegalArgumentException("La página debe ser 0 o mayor");
        if (tamano < 1) throw new IllegalArgumentException("El tamaño de página debe ser mayor que 0");
        Long tenantId = TenantContext.getTenantId();

        Specification<TransaccionPagoCompra> spec = (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
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

        var pageable = PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "fecha"));
        return PaginaResponse.de(transaccionPagoCompraRepository.findAll(spec, pageable));
    }

    public TransaccionPagoCompra obtener(Long id) {
        return transaccionPagoCompraRepository.findByIdAndTenantId(id, TenantContext.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Transacción no encontrada: " + id));
    }

    @Transactional
    public TransaccionPagoCompra anular(Long id, String motivo, Long usuarioId) {
        Long tenantId = TenantContext.getTenantId();
        TransaccionPagoCompra pago = obtener(id);

        if (pago.getEstado() == EstadoTransaccion.ANULADA) {
            throw new IllegalArgumentException("La transacción ya está anulada");
        }

        CuentaPorPagar cuenta = cuentaPorPagarRepository.findByIdAndTenantId(pago.getCuentaPorPagarId(), tenantId)
                .orElseThrow(() -> new IllegalStateException("Cuenta por pagar no encontrada: " + pago.getCuentaPorPagarId()));
        if (cuenta.getEstado() == EstadoCuentaPorPagar.ANULADO) {
            throw new IllegalArgumentException("La cuenta asociada está anulada");
        }

        pago.setEstado(EstadoTransaccion.ANULADA);
        pago.setUsuarioAnuloId(usuarioId);
        pago.setFechaAnulacion(LocalDateTime.now());
        pago.setMotivoAnulacion(motivo);
        transaccionPagoCompraRepository.save(pago);

        BigDecimal montoPagado = transaccionPagoCompraRepository
                .findByTenantIdAndCuentaPorPagarIdAndEstado(tenantId, cuenta.getId(), EstadoTransaccion.CONFIRMADA)
                .stream()
                .map(TransaccionPagoCompra::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        cuenta.setMontoPagado(montoPagado);
        cuenta.setSaldoPendiente(cuenta.getMontoTotal().subtract(montoPagado));
        cuenta.setEstado(montoPagado.signum() == 0
                ? EstadoCuentaPorPagar.DEUDA
                : cuenta.getSaldoPendiente().signum() == 0 ? EstadoCuentaPorPagar.PAGADO : EstadoCuentaPorPagar.PARCIAL);
        cuentaPorPagarRepository.save(cuenta);

        return pago;
    }
}
