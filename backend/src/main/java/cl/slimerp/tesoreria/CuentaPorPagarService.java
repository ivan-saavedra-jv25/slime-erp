package cl.slimerp.tesoreria;

import cl.slimerp.compras.Compra;
import cl.slimerp.config.TenantContext;
import cl.slimerp.gastos.Gasto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CuentaPorPagarService {

    private final CuentaPorPagarRepository cuentaPorPagarRepository;

    public CuentaPorPagarService(CuentaPorPagarRepository cuentaPorPagarRepository) {
        this.cuentaPorPagarRepository = cuentaPorPagarRepository;
    }

    // Se invoca desde CompraService al confirmar una compra. Idempotente: si
    // ya existe una cuenta para esa compra, no crea otra.
    @Transactional
    public void crearParaCompra(Compra compra, String nombreProveedor) {
        Long tenantId = compra.getTenantId();
        if (cuentaPorPagarRepository.findByTenantIdAndCompraId(tenantId, compra.getId()).isPresent()) {
            return;
        }

        cuentaPorPagarRepository.save(CuentaPorPagar.builder()
                .tenantId(tenantId)
                .compraId(compra.getId())
                .proveedorId(compra.getProveedorId())
                .descripcion("Compra C-" + compra.getId() + " — " + nombreProveedor)
                .montoTotal(compra.getTotal())
                .montoPagado(BigDecimal.ZERO)
                .saldoPendiente(compra.getTotal())
                .estado(EstadoCuentaPorPagar.DEUDA)
                .build());
    }

    // Se invoca desde GastoService al crear un gasto puntual o generado desde
    // una plantilla recurrente. Idempotente: si ya existe una cuenta para ese
    // gasto, no crea otra.
    @Transactional
    public void crearParaGasto(Gasto gasto, String nombreCategoria) {
        Long tenantId = gasto.getTenantId();
        if (cuentaPorPagarRepository.findByTenantIdAndGastoId(tenantId, gasto.getId()).isPresent()) {
            return;
        }

        cuentaPorPagarRepository.save(CuentaPorPagar.builder()
                .tenantId(tenantId)
                .gastoId(gasto.getId())
                .categoriaGastoId(gasto.getCategoriaGastoId())
                .descripcion(nombreCategoria + ": " + gasto.getDescripcion())
                .montoTotal(gasto.getMonto())
                .montoPagado(BigDecimal.ZERO)
                .saldoPendiente(gasto.getMonto())
                .estado(EstadoCuentaPorPagar.DEUDA)
                .build());
    }

    public List<CuentaPorPagar> listar(Long proveedorId, Long categoriaGastoId, EstadoCuentaPorPagar estado) {
        Long tenantId = TenantContext.getTenantId();
        if (proveedorId != null) {
            return cuentaPorPagarRepository.findByTenantIdAndProveedorIdOrderByFechaGeneracionDesc(tenantId, proveedorId);
        }
        if (categoriaGastoId != null) {
            return cuentaPorPagarRepository.findByTenantIdAndCategoriaGastoIdOrderByFechaGeneracionDesc(tenantId, categoriaGastoId);
        }
        if (estado != null) {
            return cuentaPorPagarRepository.findByTenantIdAndEstadoOrderByFechaGeneracionDesc(tenantId, estado);
        }
        return cuentaPorPagarRepository.findByTenantIdOrderByFechaGeneracionDesc(tenantId);
    }

    public CuentaPorPagar obtener(Long id) {
        return cuentaPorPagarRepository.findByIdAndTenantId(id, TenantContext.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Cuenta por pagar no encontrada: " + id));
    }

    public ResumenCuentasPorPagar resumen() {
        List<CuentaPorPagar> cuentas = cuentaPorPagarRepository.findByTenantIdOrderByFechaGeneracionDesc(TenantContext.getTenantId());

        BigDecimal totalPorPagar = BigDecimal.ZERO;
        BigDecimal totalPagado = BigDecimal.ZERO;
        BigDecimal saldoPendiente = BigDecimal.ZERO;
        long enDeuda = 0;
        long parciales = 0;
        long pagadas = 0;

        for (CuentaPorPagar cuenta : cuentas) {
            if (cuenta.getEstado() == EstadoCuentaPorPagar.ANULADO) continue;
            totalPorPagar = totalPorPagar.add(cuenta.getMontoTotal());
            totalPagado = totalPagado.add(cuenta.getMontoPagado());
            saldoPendiente = saldoPendiente.add(cuenta.getSaldoPendiente());
            switch (cuenta.getEstado()) {
                case DEUDA -> enDeuda++;
                case PARCIAL -> parciales++;
                case PAGADO -> pagadas++;
                default -> { }
            }
        }

        return new ResumenCuentasPorPagar(totalPorPagar, totalPagado, saldoPendiente, enDeuda, parciales, pagadas);
    }

    @Transactional
    public CuentaPorPagar anular(Long id, String motivo, Long usuarioId) {
        CuentaPorPagar cuenta = obtener(id);

        if (cuenta.getEstado() == EstadoCuentaPorPagar.ANULADO) {
            throw new IllegalArgumentException("La cuenta ya está anulada");
        }
        if (cuenta.getEstado() == EstadoCuentaPorPagar.PAGADO) {
            throw new IllegalArgumentException("No se puede anular una cuenta ya pagada");
        }

        cuenta.setEstado(EstadoCuentaPorPagar.ANULADO);
        cuenta.setUsuarioAnuloId(usuarioId);
        cuenta.setFechaAnulacion(LocalDateTime.now());
        cuenta.setMotivoAnulacion(motivo);
        return cuentaPorPagarRepository.save(cuenta);
    }
}
