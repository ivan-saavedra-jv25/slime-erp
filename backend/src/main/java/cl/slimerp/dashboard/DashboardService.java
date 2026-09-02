package cl.slimerp.dashboard;

import cl.slimerp.catalogo.Cliente;
import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.catalogo.Producto;
import cl.slimerp.catalogo.ProductoRepository;
import cl.slimerp.compras.Compra;
import cl.slimerp.compras.CompraRepository;
import cl.slimerp.inventario.StockProductoBodega;
import cl.slimerp.inventario.StockProductoBodegaRepository;
import cl.slimerp.tesoreria.CuentaPorCobrarService;
import cl.slimerp.tesoreria.ResumenTesoreria;
import cl.slimerp.ventas.Venta;
import cl.slimerp.ventas.VentaRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final DateTimeFormatter FORMATO_DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter FORMATO_MES = DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("es-CL"));

    private final VentaRepository ventaRepository;
    private final CompraRepository compraRepository;
    private final ClienteRepository clienteRepository;
    private final ProductoRepository productoRepository;
    private final StockProductoBodegaRepository stockRepository;
    private final CuentaPorCobrarService cuentaPorCobrarService;

    public DashboardService(VentaRepository ventaRepository, CompraRepository compraRepository,
                             ClienteRepository clienteRepository, ProductoRepository productoRepository,
                             StockProductoBodegaRepository stockRepository,
                             CuentaPorCobrarService cuentaPorCobrarService) {
        this.ventaRepository = ventaRepository;
        this.compraRepository = compraRepository;
        this.clienteRepository = clienteRepository;
        this.productoRepository = productoRepository;
        this.stockRepository = stockRepository;
        this.cuentaPorCobrarService = cuentaPorCobrarService;
    }

    public DashboardResponse resumen(Long tenantId) {
        LocalDate hoy = LocalDate.now();
        LocalDateTime inicioMes = hoy.withDayOfMonth(1).atStartOfDay();
        LocalDateTime finHoy = hoy.atTime(LocalTime.MAX);

        LocalDate inicioMesAnteriorFecha = hoy.minusMonths(1).withDayOfMonth(1);
        int diaCap = Math.min(hoy.getDayOfMonth(), inicioMesAnteriorFecha.lengthOfMonth());
        LocalDateTime inicioMesAnterior = inicioMesAnteriorFecha.atStartOfDay();
        LocalDateTime finMesAnterior = inicioMesAnteriorFecha.withDayOfMonth(diaCap).atTime(LocalTime.MAX);

        List<Venta> ventasPeriodo = ventaRepository.findByTenantIdAndActivoTrueAndFechaBetween(tenantId, inicioMes, finHoy);
        List<Venta> ventasPeriodoAnterior = ventaRepository.findByTenantIdAndActivoTrueAndFechaBetween(
                tenantId, inicioMesAnterior, finMesAnterior);
        List<Compra> comprasPeriodo = compraRepository.findByTenantIdAndActivoTrueAndFechaBetween(tenantId, inicioMes, finHoy);

        BigDecimal ventasTotal = sumar(ventasPeriodo, Venta::getMontoTotal);
        BigDecimal ventasTotalAnterior = sumar(ventasPeriodoAnterior, Venta::getMontoTotal);
        BigDecimal comprasTotal = sumar(comprasPeriodo, Compra::getTotal);

        Double variacionPct = ventasTotalAnterior.signum() == 0 ? null
                : ventasTotal.subtract(ventasTotalAnterior)
                        .divide(ventasTotalAnterior, 4, RoundingMode.HALF_UP)
                        .multiply(CIEN)
                        .doubleValue();

        long totalClientes = clienteRepository.countByTenantIdAndActivoTrue(tenantId);
        long totalProductos = productoRepository.countByTenantIdAndActivoTrue(tenantId);

        Map<Long, BigDecimal> stockPorProducto = stockRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.groupingBy(StockProductoBodega::getProductoId,
                        Collectors.reducing(BigDecimal.ZERO, StockProductoBodega::getCantidad, BigDecimal::add)));
        BigDecimal stockDisponible = stockPorProducto.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        long sinStock = 0;
        long stockBajo = 0;
        for (Producto producto : productoRepository.findByTenantIdAndActivoTrue(tenantId)) {
            BigDecimal cantidad = stockPorProducto.getOrDefault(producto.getId(), BigDecimal.ZERO);
            if (cantidad.signum() <= 0) {
                sinStock++;
            } else if (producto.getStockMinimo() != null && producto.getStockMinimo().signum() > 0
                    && cantidad.compareTo(producto.getStockMinimo()) <= 0) {
                stockBajo++;
            }
        }

        ResumenTesoreria resumenTesoreria = cuentaPorCobrarService.resumen();
        long cuentasPendientes = resumenTesoreria.cuentasEnDeuda() + resumenTesoreria.cuentasParciales();

        KpiResumen kpis = new KpiResumen(
                ventasTotal, ventasTotalAnterior, variacionPct,
                comprasTotal, ventasPeriodo.size() + (long) comprasPeriodo.size(),
                totalClientes, totalProductos,
                stockDisponible, stockBajo, sinStock,
                resumenTesoreria.saldoPendiente(), cuentasPendientes);

        List<Alerta> alertas = construirAlertas(sinStock, stockBajo, cuentasPendientes);

        Map<Long, String> nombresClientes = clienteRepository.findByTenantIdAndActivoTrue(tenantId).stream()
                .collect(Collectors.toMap(Cliente::getId, Cliente::getNombre));
        List<VentaResumenItem> ultimasVentas = ventaRepository.findTop8ByTenantIdAndActivoTrueOrderByFechaDesc(tenantId)
                .stream()
                .map(v -> new VentaResumenItem(v.getId(), v.getFecha(),
                        nombresClientes.getOrDefault(v.getClienteId(), "Cliente #" + v.getClienteId()),
                        v.getMontoTotal(), v.getTipoDocumento()))
                .toList();

        return new DashboardResponse(kpis, alertas, ultimasVentas);
    }

    private List<Alerta> construirAlertas(long sinStock, long stockBajo, long cuentasPendientes) {
        List<Alerta> alertas = new ArrayList<>();
        if (sinStock > 0) {
            alertas.add(new Alerta(TipoAlerta.SIN_STOCK, SeveridadAlerta.ALTA,
                    sinStock + (sinStock == 1 ? " producto sin stock" : " productos sin stock"), "/productos"));
        }
        if (stockBajo > 0) {
            alertas.add(new Alerta(TipoAlerta.STOCK_BAJO, SeveridadAlerta.MEDIA,
                    stockBajo + (stockBajo == 1 ? " producto con stock bajo" : " productos con stock bajo"), "/productos"));
        }
        if (cuentasPendientes > 0) {
            alertas.add(new Alerta(TipoAlerta.CUENTAS_PENDIENTES, SeveridadAlerta.MEDIA,
                    cuentasPendientes + (cuentasPendientes == 1 ? " cuenta por cobrar pendiente" : " cuentas por cobrar pendientes"),
                    "/tesoreria/cuentas"));
        }
        return alertas;
    }

    public List<PuntoVenta> ventasEvolucion(Long tenantId, String rango) {
        LocalDate hoy = LocalDate.now();
        String r = (rango == null || rango.isBlank()) ? "7d" : rango;

        return switch (r) {
            case "hoy" -> agruparPorDia(tenantId, hoy, hoy);
            case "mes" -> agruparPorDia(tenantId, hoy.withDayOfMonth(1), hoy);
            case "mes_anterior" -> {
                LocalDate inicio = hoy.minusMonths(1).withDayOfMonth(1);
                yield agruparPorDia(tenantId, inicio, inicio.withDayOfMonth(inicio.lengthOfMonth()));
            }
            case "anio" -> agruparPorMes(tenantId, hoy.withDayOfMonth(1).withMonth(1), hoy);
            default -> agruparPorDia(tenantId, hoy.minusDays(6), hoy);
        };
    }

    private List<PuntoVenta> agruparPorDia(Long tenantId, LocalDate desde, LocalDate hasta) {
        LinkedHashMap<LocalDate, BigDecimal> buckets = new LinkedHashMap<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            buckets.put(dia, BigDecimal.ZERO);
        }
        for (Venta venta : ventaRepository.findByTenantIdAndActivoTrueAndFechaBetween(
                tenantId, desde.atStartOfDay(), hasta.atTime(LocalTime.MAX))) {
            buckets.merge(venta.getFecha().toLocalDate(), venta.getMontoTotal(), BigDecimal::add);
        }
        return buckets.entrySet().stream()
                .map(e -> new PuntoVenta(e.getKey().format(FORMATO_DIA), e.getValue()))
                .toList();
    }

    private List<PuntoVenta> agruparPorMes(Long tenantId, LocalDate desde, LocalDate hasta) {
        LinkedHashMap<YearMonth, BigDecimal> buckets = new LinkedHashMap<>();
        for (YearMonth mes = YearMonth.from(desde); !mes.isAfter(YearMonth.from(hasta)); mes = mes.plusMonths(1)) {
            buckets.put(mes, BigDecimal.ZERO);
        }
        for (Venta venta : ventaRepository.findByTenantIdAndActivoTrueAndFechaBetween(
                tenantId, desde.atStartOfDay(), hasta.atTime(LocalTime.MAX))) {
            buckets.merge(YearMonth.from(venta.getFecha()), venta.getMontoTotal(), BigDecimal::add);
        }
        return buckets.entrySet().stream()
                .map(e -> new PuntoVenta(capitalizar(e.getKey().format(FORMATO_MES)), e.getValue()))
                .toList();
    }

    private String capitalizar(String texto) {
        return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    private static <T> BigDecimal sumar(List<T> items, Function<T, BigDecimal> extractor) {
        return items.stream().map(extractor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
