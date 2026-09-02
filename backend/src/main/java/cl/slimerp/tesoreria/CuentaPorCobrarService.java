package cl.slimerp.tesoreria;

import cl.slimerp.config.TenantContext;
import cl.slimerp.ventas.Venta;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CuentaPorCobrarService {

    private final CuentaPorCobrarRepository cuentaPorCobrarRepository;

    public CuentaPorCobrarService(CuentaPorCobrarRepository cuentaPorCobrarRepository) {
        this.cuentaPorCobrarRepository = cuentaPorCobrarRepository;
    }

    // Se invoca desde VentaService al confirmar una venta con forma de pago a
    // crédito. Idempotente: si ya existe una cuenta para esa venta, no crea otra.
    @Transactional
    public void crearParaVenta(Venta venta) {
        Long tenantId = venta.getTenantId();
        if (cuentaPorCobrarRepository.findByTenantIdAndVentaId(tenantId, venta.getId()).isPresent()) {
            return;
        }

        cuentaPorCobrarRepository.save(CuentaPorCobrar.builder()
                .tenantId(tenantId)
                .ventaId(venta.getId())
                .clienteId(venta.getClienteId())
                .montoTotal(venta.getMontoTotal())
                .montoPagado(BigDecimal.ZERO)
                .saldoPendiente(venta.getMontoTotal())
                .estado(EstadoCuentaPorCobrar.DEUDA)
                .build());
    }

    public List<CuentaPorCobrar> listar(Long clienteId, EstadoCuentaPorCobrar estado) {
        Long tenantId = TenantContext.getTenantId();
        if (clienteId != null) {
            return cuentaPorCobrarRepository.findByTenantIdAndClienteIdOrderByFechaGeneracionDesc(tenantId, clienteId);
        }
        if (estado != null) {
            return cuentaPorCobrarRepository.findByTenantIdAndEstadoOrderByFechaGeneracionDesc(tenantId, estado);
        }
        return cuentaPorCobrarRepository.findByTenantIdOrderByFechaGeneracionDesc(tenantId);
    }

    public CuentaPorCobrar obtener(Long id) {
        return cuentaPorCobrarRepository.findByIdAndTenantId(id, TenantContext.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Cuenta por cobrar no encontrada: " + id));
    }

    public CuentaPorCobrar obtenerPorVenta(Long ventaId) {
        return cuentaPorCobrarRepository.findByTenantIdAndVentaId(TenantContext.getTenantId(), ventaId)
                .orElseThrow(() -> new IllegalArgumentException("La venta no tiene una cuenta por cobrar asociada: " + ventaId));
    }

    public ResumenTesoreria resumen() {
        List<CuentaPorCobrar> cuentas = cuentaPorCobrarRepository.findByTenantIdOrderByFechaGeneracionDesc(TenantContext.getTenantId());

        BigDecimal totalPorCobrar = BigDecimal.ZERO;
        BigDecimal totalCobrado = BigDecimal.ZERO;
        BigDecimal saldoPendiente = BigDecimal.ZERO;
        long enDeuda = 0;
        long parciales = 0;
        long pagadas = 0;

        for (CuentaPorCobrar cuenta : cuentas) {
            if (cuenta.getEstado() == EstadoCuentaPorCobrar.ANULADO) continue;
            totalPorCobrar = totalPorCobrar.add(cuenta.getMontoTotal());
            totalCobrado = totalCobrado.add(cuenta.getMontoPagado());
            saldoPendiente = saldoPendiente.add(cuenta.getSaldoPendiente());
            switch (cuenta.getEstado()) {
                case DEUDA -> enDeuda++;
                case PARCIAL -> parciales++;
                case PAGADO -> pagadas++;
                default -> { }
            }
        }

        return new ResumenTesoreria(totalPorCobrar, totalCobrado, saldoPendiente, enDeuda, parciales, pagadas);
    }

    @Transactional
    public CuentaPorCobrar anular(Long id, String motivo, Long usuarioId) {
        CuentaPorCobrar cuenta = obtener(id);

        if (cuenta.getEstado() == EstadoCuentaPorCobrar.ANULADO) {
            throw new IllegalArgumentException("La cuenta ya está anulada");
        }
        if (cuenta.getEstado() == EstadoCuentaPorCobrar.PAGADO) {
            throw new IllegalArgumentException("No se puede anular una cuenta ya pagada");
        }

        cuenta.setEstado(EstadoCuentaPorCobrar.ANULADO);
        cuenta.setUsuarioAnuloId(usuarioId);
        cuenta.setFechaAnulacion(LocalDateTime.now());
        cuenta.setMotivoAnulacion(motivo);
        return cuentaPorCobrarRepository.save(cuenta);
    }
}
