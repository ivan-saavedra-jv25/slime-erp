package cl.slimerp.reporteria;

import cl.slimerp.catalogo.Cliente;
import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.ventas.TipoDocumentoVenta;
import cl.slimerp.ventas.Venta;
import cl.slimerp.ventas.VentaRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Arma el Libro de Ventas: junta las ventas activas de un rango de fechas,
// agrupadas y subtotalizadas por tipo de documento (Factura/Factura
// Exenta/Boleta/Boleta Exenta/Voucher). No recalcula montos: los toma tal
// cual los dejó CalculadoraMontosVenta al confirmar cada venta.
@Service
public class LibroVentasService {

    private static final List<String> ORDEN_TIPOS =
            List.of("Factura", "Factura Exenta", "Boleta", "Boleta Exenta", "Voucher");

    private final VentaRepository ventaRepository;
    private final ClienteRepository clienteRepository;

    public LibroVentasService(VentaRepository ventaRepository, ClienteRepository clienteRepository) {
        this.ventaRepository = ventaRepository;
        this.clienteRepository = clienteRepository;
    }

    public record LibroVentasFila(
            Long ventaId,
            LocalDateTime fecha,
            String tipoDocumento,
            String clienteRut,
            String clienteNombre,
            BigDecimal montoNeto,
            BigDecimal montoIva,
            BigDecimal montoTotal) {
    }

    public record LibroVentasSubtotal(
            String tipoDocumento,
            int cantidad,
            BigDecimal montoNeto,
            BigDecimal montoIva,
            BigDecimal montoTotal) {
    }

    public record LibroVentasResponse(
            LocalDate desde,
            LocalDate hasta,
            List<LibroVentasFila> filas,
            List<LibroVentasSubtotal> subtotales,
            LibroVentasSubtotal totalGeneral) {
    }

    public LibroVentasResponse generar(Long tenantId, LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'");
        }
        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin = hasta.atTime(LocalTime.MAX);

        List<Venta> ventas = ventaRepository
                .findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(tenantId, inicio, fin);

        List<Long> clienteIds = ventas.stream().map(Venta::getClienteId).distinct().toList();
        Map<Long, Cliente> clientesPorId = clienteIds.isEmpty()
                ? Map.of()
                : clienteRepository.findByTenantIdAndIdIn(tenantId, clienteIds).stream()
                        .collect(Collectors.toMap(Cliente::getId, c -> c));

        List<LibroVentasFila> filas = ventas.stream()
                .map(v -> mapearFila(v, clientesPorId.get(v.getClienteId())))
                .toList();

        return new LibroVentasResponse(desde, hasta, filas, agruparSubtotales(filas), totalizar(filas));
    }

    private LibroVentasFila mapearFila(Venta venta, Cliente cliente) {
        return new LibroVentasFila(
                venta.getId(),
                venta.getFecha(),
                etiquetaTipoDocumento(venta.getTipoDocumento(), venta.isExento()),
                cliente != null ? cliente.getRut() : null,
                cliente != null ? cliente.getNombre() : "—",
                venta.getMontoNeto(),
                venta.getMontoIva(),
                venta.getMontoTotal());
    }

    // Orden fijo Factura -> Factura Exenta -> Boleta -> Boleta Exenta -> Voucher (no
    // alfabético ni de aparición) para que el libro se lea siempre igual aunque un
    // tipo no tenga ventas en el período.
    static String etiquetaTipoDocumento(TipoDocumentoVenta tipo, boolean exento) {
        return switch (tipo) {
            case FACTURA -> exento ? "Factura Exenta" : "Factura";
            case BOLETA -> exento ? "Boleta Exenta" : "Boleta";
            case VOUCHER -> "Voucher";
        };
    }

    private List<LibroVentasSubtotal> agruparSubtotales(List<LibroVentasFila> filas) {
        Map<String, List<LibroVentasFila>> porTipo = filas.stream()
                .collect(Collectors.groupingBy(LibroVentasFila::tipoDocumento));

        return ORDEN_TIPOS.stream()
                .filter(porTipo::containsKey)
                .map(tipo -> subtotalDe(tipo, porTipo.get(tipo)))
                .toList();
    }

    private LibroVentasSubtotal totalizar(List<LibroVentasFila> filas) {
        return subtotalDe("Total", filas);
    }

    private LibroVentasSubtotal subtotalDe(String etiqueta, List<LibroVentasFila> filas) {
        return new LibroVentasSubtotal(
                etiqueta,
                filas.size(),
                sumar(filas, LibroVentasFila::montoNeto),
                sumar(filas, LibroVentasFila::montoIva),
                sumar(filas, LibroVentasFila::montoTotal));
    }

    private BigDecimal sumar(List<LibroVentasFila> filas, Function<LibroVentasFila, BigDecimal> extractor) {
        return filas.stream().map(extractor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
