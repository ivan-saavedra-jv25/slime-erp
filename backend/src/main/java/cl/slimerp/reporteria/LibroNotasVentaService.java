package cl.slimerp.reporteria;

import cl.slimerp.catalogo.Cliente;
import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.cotizaciones.Cotizacion;
import cl.slimerp.cotizaciones.CotizacionRepository;
import cl.slimerp.cotizaciones.NumeroCotizacion;
import cl.slimerp.notasventa.EstadoNotaVenta;
import cl.slimerp.notasventa.NotaVenta;
import cl.slimerp.notasventa.NotaVentaDocumento;
import cl.slimerp.notasventa.NotaVentaDocumentoRepository;
import cl.slimerp.notasventa.NotaVentaRepository;
import cl.slimerp.notasventa.NumeroNotaVenta;
import cl.slimerp.notasventa.OrigenNotaVenta;
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Libro de Notas de Venta: registro histórico completo del período. Conserva
// TODOS los estados —incluido CANCELADA— porque es el registro que no se depura
// ni elimina (spec §15).
@Service
public class LibroNotasVentaService {

    private static final String SIN_DATO = "—";

    private final NotaVentaRepository notaVentaRepository;
    private final NotaVentaDocumentoRepository documentoRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final CotizacionRepository cotizacionRepository;

    public LibroNotasVentaService(NotaVentaRepository notaVentaRepository,
                                  NotaVentaDocumentoRepository documentoRepository,
                                  ClienteRepository clienteRepository, UsuarioRepository usuarioRepository,
                                  CotizacionRepository cotizacionRepository) {
        this.notaVentaRepository = notaVentaRepository;
        this.documentoRepository = documentoRepository;
        this.clienteRepository = clienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.cotizacionRepository = cotizacionRepository;
    }

    public record LibroNotasVentaFila(
            Long notaVentaId,
            String numero,
            LocalDate fecha,
            String clienteNombre,
            String clienteRut,
            EstadoNotaVenta estado,
            BigDecimal montoNeto,
            BigDecimal montoIva,
            BigDecimal montoTotal,
            String vendedor,
            String origen,
            String documentosRelacionados) {
    }

    public record LibroNotasVentaResumen(
            int cantidad, BigDecimal montoNeto, BigDecimal montoIva, BigDecimal montoTotal) {
    }

    public record LibroNotasVentaResponse(
            LocalDate desde, LocalDate hasta, EstadoNotaVenta estado,
            List<LibroNotasVentaFila> filas, LibroNotasVentaResumen resumen) {
    }

    public LibroNotasVentaResponse generar(Long tenantId, LocalDate desde, LocalDate hasta,
                                            EstadoNotaVenta estado) {
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'");
        }

        List<NotaVenta> notas = notaVentaRepository
                .findByTenantIdAndFechaEmisionBetweenOrderByFolioAsc(tenantId, desde, hasta).stream()
                .filter(n -> estado == null || n.getEstado() == estado)
                .toList();

        if (notas.isEmpty()) {
            return new LibroNotasVentaResponse(desde, hasta, estado, List.of(),
                    new LibroNotasVentaResumen(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        }

        List<Long> clienteIds = notas.stream().map(NotaVenta::getClienteId).distinct().toList();
        Map<Long, Cliente> clientes = clienteRepository.findByTenantIdAndIdIn(tenantId, clienteIds).stream()
                .collect(Collectors.toMap(Cliente::getId, Function.identity()));

        List<Long> vendedorIds = notas.stream().map(NotaVenta::getVendedorId).distinct().toList();
        Map<Long, String> vendedores = usuarioRepository.findAllById(vendedorIds).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getNombre));

        List<Long> notaIds = notas.stream().map(NotaVenta::getId).toList();
        Map<Long, List<NotaVentaDocumento>> documentos = documentoRepository
                .findByTenantIdAndNotaVentaIdIn(tenantId, notaIds).stream()
                .collect(Collectors.groupingBy(NotaVentaDocumento::getNotaVentaId));

        List<Long> cotizacionIds = notas.stream()
                .map(NotaVenta::getCotizacionId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, Cotizacion> cotizaciones = cotizacionIds.isEmpty() ? Map.of()
                : cotizacionRepository.findAllById(cotizacionIds).stream()
                        .collect(Collectors.toMap(Cotizacion::getId, Function.identity()));

        List<LibroNotasVentaFila> filas = notas.stream()
                .map(n -> mapearFila(n, clientes.get(n.getClienteId()), vendedores.get(n.getVendedorId()),
                        cotizacionDe(cotizaciones, n),
                        documentos.getOrDefault(n.getId(), List.of())))
                .toList();

        return new LibroNotasVentaResponse(desde, hasta, estado, filas, resumenDe(filas));
    }

    private Cotizacion cotizacionDe(Map<Long, Cotizacion> cotizaciones, NotaVenta nota) {
        return nota.getCotizacionId() == null ? null : cotizaciones.get(nota.getCotizacionId());
    }

    private LibroNotasVentaFila mapearFila(NotaVenta nota, Cliente cliente, String vendedor,
                                            Cotizacion cotizacion, List<NotaVentaDocumento> documentos) {
        String relacionados = documentos.isEmpty() ? SIN_DATO : documentos.stream()
                .map(d -> d.getNumero() != null ? d.getNumero() : d.getTipoDocumento() + " #" + d.getDocumentoId())
                .collect(Collectors.joining(", "));

        return new LibroNotasVentaFila(
                nota.getId(),
                NumeroNotaVenta.formatear(nota.getFolio()),
                nota.getFechaEmision(),
                cliente != null ? cliente.getNombre() : SIN_DATO,
                cliente != null ? cliente.getRut() : null,
                nota.getEstado(),
                nota.getMontoNeto(),
                nota.getMontoIva(),
                nota.getMontoTotal(),
                vendedor != null ? vendedor : SIN_DATO,
                origenDe(nota, cotizacion),
                relacionados);
    }

    private String origenDe(NotaVenta nota, Cotizacion cotizacion) {
        if (nota.getOrigen() == OrigenNotaVenta.COTIZACION) {
            return "Cotización " + (cotizacion != null
                    ? NumeroCotizacion.formatear(cotizacion.getFolio())
                    : "#" + nota.getCotizacionId());
        }
        return "Venta directa";
    }

    private LibroNotasVentaResumen resumenDe(List<LibroNotasVentaFila> filas) {
        return new LibroNotasVentaResumen(
                filas.size(),
                sumar(filas, LibroNotasVentaFila::montoNeto),
                sumar(filas, LibroNotasVentaFila::montoIva),
                sumar(filas, LibroNotasVentaFila::montoTotal));
    }

    private BigDecimal sumar(List<LibroNotasVentaFila> filas, Function<LibroNotasVentaFila, BigDecimal> extractor) {
        return filas.stream().map(extractor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}