package cl.slimerp.reporteria;

import cl.slimerp.catalogo.Cliente;
import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.ventas.CodigoSiiVenta;
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

    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2);

    private final VentaRepository ventaRepository;
    private final ClienteRepository clienteRepository;

    public LibroVentasService(VentaRepository ventaRepository, ClienteRepository clienteRepository) {
        this.ventaRepository = ventaRepository;
        this.clienteRepository = clienteRepository;
    }

    public record LibroVentasFila(
            Long ventaId,
            Integer folio,
            Integer codigoSii,
            LocalDateTime fecha,
            String tipoDocumento,
            String clienteRut,
            String clienteNombre,
            BigDecimal montoNetoAfecto,
            BigDecimal montoNetoExento,
            BigDecimal montoIva,
            BigDecimal montoTotal) {
    }

    public record LibroVentasSubtotal(
            String tipoDocumento,
            int cantidad,
            BigDecimal montoNetoAfecto,
            BigDecimal montoNetoExento,
            BigDecimal montoIva,
            BigDecimal montoTotal) {
    }

    public record LibroVentasResponse(
            LocalDate desde,
            LocalDate hasta,
            String tipoDocumento,
            String busqueda,
            List<LibroVentasFila> filas,
            List<LibroVentasSubtotal> subtotales,
            LibroVentasSubtotal totalGeneral,
            long totalFilas,
            int pagina,
            int tamano) {
    }

    // Página del libro para la pantalla: subtotales/total sobre TODO lo que
    // calza con los filtros (fecha+tipo+búsqueda), pero "filas" trae solo la
    // página pedida — así el resumen nunca miente aunque el detalle esté paginado.
    public LibroVentasResponse generar(Long tenantId, LocalDate desde, LocalDate hasta, String tipoDocumento,
                                        String busqueda, int pagina, int tamano) {
        if (pagina < 0) {
            throw new IllegalArgumentException("La página debe ser 0 o mayor");
        }
        if (tamano < 1) {
            throw new IllegalArgumentException("El tamaño de página debe ser mayor que 0");
        }
        String tipoNormalizado = normalizarTipoDocumento(tipoDocumento);
        List<LibroVentasFila> todasLasFilas = filasFiltradas(tenantId, desde, hasta, tipoNormalizado, busqueda);
        List<LibroVentasFila> filasPagina = paginar(todasLasFilas, pagina, tamano);

        return new LibroVentasResponse(desde, hasta, tipoNormalizado, busqueda, filasPagina,
                agruparSubtotales(todasLasFilas), totalizar(todasLasFilas), todasLasFilas.size(), pagina, tamano);
    }

    // Libro completo sin paginar, para la exportación a Excel: siempre trae
    // todas las filas que calzan con los filtros, sin importar qué página
    // esté viendo el usuario en pantalla en ese momento.
    public LibroVentasResponse generarCompleto(Long tenantId, LocalDate desde, LocalDate hasta,
                                                String tipoDocumento, String busqueda) {
        String tipoNormalizado = normalizarTipoDocumento(tipoDocumento);
        List<LibroVentasFila> todasLasFilas = filasFiltradas(tenantId, desde, hasta, tipoNormalizado, busqueda);

        return new LibroVentasResponse(desde, hasta, tipoNormalizado, busqueda, todasLasFilas,
                agruparSubtotales(todasLasFilas), totalizar(todasLasFilas), todasLasFilas.size(),
                0, todasLasFilas.size());
    }

    private List<LibroVentasFila> filasFiltradas(Long tenantId, LocalDate desde, LocalDate hasta,
                                                  String tipoNormalizado, String busqueda) {
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'");
        }
        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin = hasta.atTime(LocalTime.MAX);

        List<Venta> ventas = ventaRepository
                .findByTenantIdAndActivoTrueAndFechaBetweenOrderByFechaAsc(tenantId, inicio, fin)
                .stream()
                .filter(v -> tipoNormalizado == null
                        || CodigoSiiVenta.etiqueta(v.getTipoDocumento(), v.isExento()).equals(tipoNormalizado))
                .toList();

        List<Long> clienteIds = ventas.stream().map(Venta::getClienteId).distinct().toList();
        Map<Long, Cliente> clientesPorId = clienteIds.isEmpty()
                ? Map.of()
                : clienteRepository.findByTenantIdAndIdIn(tenantId, clienteIds).stream()
                        .collect(Collectors.toMap(Cliente::getId, c -> c));

        return ventas.stream()
                .map(v -> mapearFila(v, clientesPorId.get(v.getClienteId())))
                .filter(f -> coincideBusqueda(f, busqueda))
                .toList();
    }

    // Busca por nombre o RUT del cliente, o por N° de venta — todo como
    // substring (no exige coincidencia exacta), igual que el resto de los
    // buscadores del proyecto.
    private boolean coincideBusqueda(LibroVentasFila fila, String busqueda) {
        if (busqueda == null || busqueda.isBlank()) {
            return true;
        }
        String texto = busqueda.trim().toLowerCase();
        boolean coincideCliente = fila.clienteNombre().toLowerCase().contains(texto)
                || (fila.clienteRut() != null && fila.clienteRut().toLowerCase().contains(texto));
        boolean coincideNumero = String.valueOf(fila.ventaId()).contains(texto);
        return coincideCliente || coincideNumero;
    }

    private List<LibroVentasFila> paginar(List<LibroVentasFila> filas, int pagina, int tamano) {
        int desdeIdx = pagina * tamano;
        if (desdeIdx >= filas.size()) {
            return List.of();
        }
        int hastaIdx = Math.min(desdeIdx + tamano, filas.size());
        return filas.subList(desdeIdx, hastaIdx);
    }

    private String normalizarTipoDocumento(String tipoDocumento) {
        if (tipoDocumento == null || tipoDocumento.isBlank()) {
            return null;
        }
        if (!CodigoSiiVenta.ORDEN_ETIQUETAS.contains(tipoDocumento)) {
            throw new IllegalArgumentException("Tipo de documento no reconocido: " + tipoDocumento);
        }
        return tipoDocumento;
    }

    // El neto de una venta va siempre a una sola columna: Afecto si la venta
    // lleva IVA, Exento si no — nunca a ambas (una venta es una u otra, nunca mixta).
    private LibroVentasFila mapearFila(Venta venta, Cliente cliente) {
        return new LibroVentasFila(
                venta.getId(),
                venta.getFolio(),
                venta.getCodigoSii(),
                venta.getFecha(),
                CodigoSiiVenta.etiqueta(venta.getTipoDocumento(), venta.isExento()),
                cliente != null ? cliente.getRut() : null,
                cliente != null ? cliente.getNombre() : "—",
                venta.isExento() ? CERO : venta.getMontoNeto(),
                venta.isExento() ? venta.getMontoNeto() : CERO,
                venta.getMontoIva(),
                venta.getMontoTotal());
    }

    private List<LibroVentasSubtotal> agruparSubtotales(List<LibroVentasFila> filas) {
        Map<String, List<LibroVentasFila>> porTipo = filas.stream()
                .collect(Collectors.groupingBy(LibroVentasFila::tipoDocumento));

        return CodigoSiiVenta.ORDEN_ETIQUETAS.stream()
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
                sumar(filas, LibroVentasFila::montoNetoAfecto),
                sumar(filas, LibroVentasFila::montoNetoExento),
                sumar(filas, LibroVentasFila::montoIva),
                sumar(filas, LibroVentasFila::montoTotal));
    }

    private BigDecimal sumar(List<LibroVentasFila> filas, Function<LibroVentasFila, BigDecimal> extractor) {
        return filas.stream().map(extractor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
