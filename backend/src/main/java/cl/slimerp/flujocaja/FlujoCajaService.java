package cl.slimerp.flujocaja;

import cl.slimerp.catalogo.Cliente;
import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.config.TenantContext;
import cl.slimerp.gastos.CategoriaGasto;
import cl.slimerp.gastos.CategoriaGastoRepository;
import cl.slimerp.tesoreria.CuentaPorPagar;
import cl.slimerp.tesoreria.CuentaPorPagarRepository;
import cl.slimerp.tesoreria.EstadoTransaccion;
import cl.slimerp.tesoreria.TransaccionPago;
import cl.slimerp.tesoreria.TransaccionPagoCompra;
import cl.slimerp.tesoreria.TransaccionPagoCompraRepository;
import cl.slimerp.tesoreria.TransaccionPagoRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Flujo de caja calculado desde Tesorería.
 *
 * Ingresos = pagos CONFIRMADA de cuentas por cobrar.
 * Egresos  = pagos CONFIRMADA de cuentas por pagar, separados en Compras
 *            (origen compra) y Gastos (origen gasto, agrupados por categoría,
 *            sumando los de la misma categoría).
 *
 * El saldo es acumulado (caja real) desde el primer movimiento confirmado;
 * no existe saldo inicial configurado por el usuario.
 */
@Service
public class FlujoCajaService {

    private static final String CATEGORIA_DESCONOCIDA = "Sin categoría";
    private static final Long SIN_CATEGORIA = 0L;
    private static final DateTimeFormatter MES_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final TransaccionPagoRepository pagoRepository;
    private final TransaccionPagoCompraRepository pagoCompraRepository;
    private final CuentaPorPagarRepository cuentaPorPagarRepository;
    private final CategoriaGastoRepository categoriaGastoRepository;
    private final ClienteRepository clienteRepository;

    public FlujoCajaService(TransaccionPagoRepository pagoRepository,
                            TransaccionPagoCompraRepository pagoCompraRepository,
                            CuentaPorPagarRepository cuentaPorPagarRepository,
                            CategoriaGastoRepository categoriaGastoRepository,
                            ClienteRepository clienteRepository) {
        this.pagoRepository = pagoRepository;
        this.pagoCompraRepository = pagoCompraRepository;
        this.cuentaPorPagarRepository = cuentaPorPagarRepository;
        this.categoriaGastoRepository = categoriaGastoRepository;
        this.clienteRepository = clienteRepository;
    }

    public ResumenAnio resumenAnio(int anio) {
        Datos datos = cargarDatos();

        String inicioAnio = String.format("%04d-01", anio);
        BigDecimal saldo = datos.porMes.headMap(inicioAnio, false).values().stream()
                .map(Bucket::neto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<MesResumen> meses = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            String clave = String.format("%04d-%02d", anio, m);
            Bucket b = datos.porMes.get(clave);
            BigDecimal ingresos = b == null ? BigDecimal.ZERO : b.ingresos;
            BigDecimal compras = b == null ? BigDecimal.ZERO : b.compras;
            BigDecimal gastos = b == null ? BigDecimal.ZERO : b.gastos();
            BigDecimal resultado = ingresos.subtract(compras).subtract(gastos);
            saldo = saldo.add(resultado);

            var totales = b == null ? List.<GastoCategoriaTotal>of() : b.gastosPorCategoria.entrySet().stream()
                    .sorted(Comparator.comparing((Map.Entry<Long, CatBucket> e) -> e.getValue().total).reversed())
                    .map(e -> new GastoCategoriaTotal(nulificable(e.getKey()), e.getValue().nombre, e.getValue().total))
                    .toList();

            meses.add(new MesResumen(clave, ingresos, compras, gastos, resultado, saldo, totales));
        }
        return new ResumenAnio(anio, meses);
    }

    public DetalleMes detalleMes(String mes) {
        YearMonth periodo;
        try {
            periodo = YearMonth.parse(mes, MES_FORMAT);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Mes inválido. Formato esperado: yyyy-MM.");
        }
        Datos datos = cargarDatos();
        String clave = periodo.toString();

        BigDecimal saldo = datos.porMes.headMap(clave, true).values().stream()
                .map(Bucket::neto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<LineaIngreso> ingresosDetalle = new ArrayList<>();
        for (TransaccionPago c : datos.cobros) {
            if (!YearMonth.from(c.getFecha()).equals(periodo)) continue;
            String nombre = datos.clientes.getOrDefault(c.getClienteId(), "Cliente");
            ingresosDetalle.add(new LineaIngreso(c.getId(), c.getCuentaPorCobrarId(),
                    c.getFecha().toLocalDate(), "Cobro " + nombre + " — Venta V-" + c.getVentaId(), c.getMonto()));
        }

        List<LineaCompra> comprasDetalle = new ArrayList<>();
        Map<Long, CategoriaDetalleBuilder> porCategoria = new TreeMap<>();
        BigDecimal gastos = BigDecimal.ZERO;
        for (TransaccionPagoCompra p : datos.pagos) {
            if (!YearMonth.from(p.getFecha()).equals(periodo)) continue;
            CuentaPorPagar cuenta = datos.cuentas.get(p.getCuentaPorPagarId());
            String descripcion = cuenta != null ? cuenta.getDescripcion() : "Pago asociado a cuenta por pagar";
            if (cuenta != null && cuenta.getCompraId() != null) {
                comprasDetalle.add(new LineaCompra(p.getId(), p.getCuentaPorPagarId(),
                        p.getFecha().toLocalDate(), descripcion, p.getMonto()));
                continue;
            }
            Long categoriaId = cuenta != null ? cuenta.getCategoriaGastoId() : null;
            String nombre = datos.categorias.getOrDefault(categoriaId, CATEGORIA_DESCONOCIDA);
            Long claveCategoria = categoriaId == null ? SIN_CATEGORIA : categoriaId;
            porCategoria.computeIfAbsent(claveCategoria, k -> new CategoriaDetalleBuilder(nombre))
                    .lineas.add(new LineaGasto(p.getId(), p.getCuentaPorPagarId(),
                            p.getFecha().toLocalDate(), descripcion, p.getMonto()));
            gastos = gastos.add(p.getMonto());
        }

        BigDecimal ingresos = ingresosDetalle.stream().map(LineaIngreso::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal compras = comprasDetalle.stream().map(LineaCompra::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<GastoCategoriaDetalle> gastosPorCategoria = porCategoria.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<Long, CategoriaDetalleBuilder> e) -> e.getValue().total()).reversed())
                .map(e -> new GastoCategoriaDetalle(nulificable(e.getKey()), e.getValue().nombre,
                        e.getValue().total(), e.getValue().lineas))
                .toList();

        BigDecimal resultado = ingresos.subtract(compras).subtract(gastos);
        return new DetalleMes(clave, ingresos, compras, gastos, resultado, saldo,
                ingresosDetalle, comprasDetalle, gastosPorCategoria);
    }

    private Datos cargarDatos() {
        Long tenantId = TenantContext.getTenantId();

        List<TransaccionPago> cobros = pagoRepository
                .findByTenantIdAndEstadoOrderByFechaAsc(tenantId, EstadoTransaccion.CONFIRMADA);
        List<TransaccionPagoCompra> pagos = pagoCompraRepository
                .findByTenantIdAndEstadoOrderByFechaAsc(tenantId, EstadoTransaccion.CONFIRMADA);

        Set<Long> cuentaIds = pagos.stream()
                .map(TransaccionPagoCompra::getCuentaPorPagarId)
                .collect(Collectors.toSet());
        Map<Long, CuentaPorPagar> cuentas = cuentaIds.isEmpty() ? new HashMap<>()
                : cuentaPorPagarRepository.findByTenantIdAndIdIn(tenantId, cuentaIds).stream()
                        .collect(Collectors.toMap(CuentaPorPagar::getId, c -> c));

        Set<Long> categoriaIds = cuentas.values().stream()
                .map(CuentaPorPagar::getCategoriaGastoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> categorias = categoriaIds.isEmpty() ? new HashMap<>()
                : categoriaGastoRepository.findByTenantIdAndIdIn(tenantId, categoriaIds).stream()
                        .collect(Collectors.toMap(CategoriaGasto::getId, CategoriaGasto::getNombre));

        Set<Long> clienteIds = cobros.stream()
                .map(TransaccionPago::getClienteId)
                .collect(Collectors.toSet());
        Map<Long, String> clientes = clienteIds.isEmpty() ? new HashMap<>()
                : clienteRepository.findByTenantIdAndIdIn(tenantId, List.copyOf(clienteIds)).stream()
                        .collect(Collectors.toMap(Cliente::getId, Cliente::getNombre));

        TreeMap<String, Bucket> porMes = new TreeMap<>();
        for (TransaccionPago c : cobros) {
            Bucket b = porMes.computeIfAbsent(YearMonth.from(c.getFecha()).toString(), k -> new Bucket());
            b.ingresos = b.ingresos.add(c.getMonto());
        }
        for (TransaccionPagoCompra p : pagos) {
            CuentaPorPagar cuenta = cuentas.get(p.getCuentaPorPagarId());
            Bucket b = porMes.computeIfAbsent(YearMonth.from(p.getFecha()).toString(), k -> new Bucket());
            if (cuenta != null && cuenta.getCompraId() != null) {
                b.compras = b.compras.add(p.getMonto());
            } else {
                Long catId = cuenta != null ? cuenta.getCategoriaGastoId() : null;
                String nombre = categorias.getOrDefault(catId, CATEGORIA_DESCONOCIDA);
                Long claveCategoria = catId == null ? SIN_CATEGORIA : catId;
                CatBucket cb = b.gastosPorCategoria.computeIfAbsent(claveCategoria, k -> new CatBucket(nombre));
                cb.total = cb.total.add(p.getMonto());
            }
        }
        return new Datos(cobros, pagos, cuentas, categorias, clientes, porMes);
    }

    private static Long nulificable(Long categoriaGastoId) {
        return categoriaGastoId == null || categoriaGastoId.equals(SIN_CATEGORIA) ? null : categoriaGastoId;
    }

    private record Datos(List<TransaccionPago> cobros,
                         List<TransaccionPagoCompra> pagos,
                         Map<Long, CuentaPorPagar> cuentas,
                         Map<Long, String> categorias,
                         Map<Long, String> clientes,
                         TreeMap<String, Bucket> porMes) {
    }

    private static final class Bucket {
        private BigDecimal ingresos = BigDecimal.ZERO;
        private BigDecimal compras = BigDecimal.ZERO;
        private final TreeMap<Long, CatBucket> gastosPorCategoria = new TreeMap<>();

        BigDecimal gastos() {
            return gastosPorCategoria.values().stream()
                    .map(c -> c.total)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal neto() {
            return ingresos.subtract(compras).subtract(gastos());
        }
    }

    private static final class CatBucket {
        private final String nombre;
        private BigDecimal total = BigDecimal.ZERO;

        private CatBucket(String nombre) {
            this.nombre = nombre;
        }
    }

    private static final class CategoriaDetalleBuilder {
        private final String nombre;
        private final List<LineaGasto> lineas = new ArrayList<>();

        private CategoriaDetalleBuilder(String nombre) {
            this.nombre = nombre;
        }

        BigDecimal total() {
            return lineas.stream().map(LineaGasto::monto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }
}