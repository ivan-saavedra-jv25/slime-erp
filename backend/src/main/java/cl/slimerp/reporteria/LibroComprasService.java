package cl.slimerp.reporteria;

import cl.slimerp.catalogo.Proveedor;
import cl.slimerp.catalogo.ProveedorRepository;
import cl.slimerp.compras.Compra;
import cl.slimerp.compras.CompraRepository;
import cl.slimerp.tesoreria.CuentaPorPagar;
import cl.slimerp.tesoreria.CuentaPorPagarRepository;
import cl.slimerp.tesoreria.EstadoCuentaPorPagar;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Arma el Libro de Compras: junta las compras activas de un rango de fechas
// (sin agrupar por dimensión, a diferencia del Libro de Ventas), resolviendo
// proveedor y estado de pago (cruce con CuentaPorPagar) de cada una, más un
// resumen general y una serie diaria para el gráfico de evolución.
@Service
public class LibroComprasService {

    private static final DateTimeFormatter FORMATO_DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final Map<EstadoCuentaPorPagar, String> ETIQUETAS_ESTADO = Map.of(
            EstadoCuentaPorPagar.DEUDA, "En deuda",
            EstadoCuentaPorPagar.PARCIAL, "Parcial",
            EstadoCuentaPorPagar.PAGADO, "Pagado",
            EstadoCuentaPorPagar.ANULADO, "Anulado");
    private static final String SIN_DATO = "—";

    private final CompraRepository compraRepository;
    private final ProveedorRepository proveedorRepository;
    private final CuentaPorPagarRepository cuentaPorPagarRepository;

    public LibroComprasService(CompraRepository compraRepository, ProveedorRepository proveedorRepository,
                                CuentaPorPagarRepository cuentaPorPagarRepository) {
        this.compraRepository = compraRepository;
        this.proveedorRepository = proveedorRepository;
        this.cuentaPorPagarRepository = cuentaPorPagarRepository;
    }

    public record LibroComprasFila(
            Long compraId,
            LocalDateTime fecha,
            String numeroDocumento,
            String proveedorNombre,
            String proveedorRut,
            int cantidadItems,
            BigDecimal montoNeto,
            BigDecimal montoIva,
            BigDecimal montoTotal,
            String estadoPago) {
    }

    public record LibroComprasResumen(
            int cantidadCompras,
            BigDecimal montoNeto,
            BigDecimal montoIva,
            BigDecimal montoTotal) {
    }

    public record LibroComprasPuntoEvolucion(String etiqueta, BigDecimal total) {
    }

    public record LibroComprasResponse(
            LocalDate desde,
            LocalDate hasta,
            List<LibroComprasFila> filas,
            LibroComprasResumen resumen,
            List<LibroComprasPuntoEvolucion> evolucion) {
    }

    public LibroComprasResponse generar(Long tenantId, LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'");
        }
        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin = hasta.atTime(LocalTime.MAX);

        List<Compra> compras = compraRepository
                .findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(tenantId, inicio, fin);

        if (compras.isEmpty()) {
            return new LibroComprasResponse(desde, hasta, List.of(),
                    new LibroComprasResumen(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                    evolucionVacia(desde, hasta));
        }

        List<Long> proveedorIds = compras.stream().map(Compra::getProveedorId).distinct().toList();
        Map<Long, Proveedor> proveedoresPorId = proveedorRepository.findByTenantIdAndIdIn(tenantId, proveedorIds)
                .stream().collect(Collectors.toMap(Proveedor::getId, p -> p));

        List<Long> compraIds = compras.stream().map(Compra::getId).toList();
        Map<Long, EstadoCuentaPorPagar> estadosPorCompraId = cuentaPorPagarRepository
                .findByTenantIdAndCompraIdIn(tenantId, compraIds).stream()
                .collect(Collectors.toMap(CuentaPorPagar::getCompraId, CuentaPorPagar::getEstado));

        List<LibroComprasFila> filas = compras.stream()
                .map(c -> mapearFila(c, proveedoresPorId.get(c.getProveedorId()), estadosPorCompraId.get(c.getId())))
                .toList();

        return new LibroComprasResponse(desde, hasta, filas, resumenDe(filas), evolucionDe(desde, hasta, filas));
    }

    private LibroComprasFila mapearFila(Compra compra, Proveedor proveedor, EstadoCuentaPorPagar estado) {
        return new LibroComprasFila(
                compra.getId(),
                compra.getFecha(),
                compra.getNumeroDocumento(),
                proveedor != null ? proveedor.getNombre() : SIN_DATO,
                proveedor != null ? proveedor.getRut() : null,
                compra.getDetalle().size(),
                compra.getMontoNeto(),
                compra.getMontoIva(),
                compra.getMontoNeto().add(compra.getMontoIva()),
                estado != null ? ETIQUETAS_ESTADO.get(estado) : SIN_DATO);
    }

    private LibroComprasResumen resumenDe(List<LibroComprasFila> filas) {
        return new LibroComprasResumen(
                filas.size(),
                sumar(filas, LibroComprasFila::montoNeto),
                sumar(filas, LibroComprasFila::montoIva),
                sumar(filas, LibroComprasFila::montoTotal));
    }

    private BigDecimal sumar(List<LibroComprasFila> filas, Function<LibroComprasFila, BigDecimal> extractor) {
        return filas.stream().map(extractor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<LibroComprasPuntoEvolucion> evolucionDe(LocalDate desde, LocalDate hasta, List<LibroComprasFila> filas) {
        LinkedHashMap<LocalDate, BigDecimal> buckets = new LinkedHashMap<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            buckets.put(dia, BigDecimal.ZERO);
        }
        for (LibroComprasFila fila : filas) {
            buckets.merge(fila.fecha().toLocalDate(), fila.montoTotal(), BigDecimal::add);
        }
        return buckets.entrySet().stream()
                .map(e -> new LibroComprasPuntoEvolucion(e.getKey().format(FORMATO_DIA), e.getValue()))
                .toList();
    }

    private List<LibroComprasPuntoEvolucion> evolucionVacia(LocalDate desde, LocalDate hasta) {
        return evolucionDe(desde, hasta, List.of());
    }
}
