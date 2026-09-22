package cl.slimerp.reporteria;

import cl.slimerp.catalogo.Cliente;
import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.cotizaciones.Cotizacion;
import cl.slimerp.cotizaciones.CotizacionDocumento;
import cl.slimerp.cotizaciones.CotizacionDocumentoRepository;
import cl.slimerp.cotizaciones.CotizacionRepository;
import cl.slimerp.cotizaciones.EstadoCotizacion;
import cl.slimerp.cotizaciones.NumeroCotizacion;
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Libro de Cotizaciones: registro histórico completo del período. A diferencia
// del listado del módulo, conserva TODOS los estados —incluidas rechazadas,
// vencidas y canceladas— porque es el registro que no se depura.
@Service
public class LibroCotizacionesService {

    private static final String SIN_DATO = "—";

    private final CotizacionRepository cotizacionRepository;
    private final CotizacionDocumentoRepository documentoRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;

    public LibroCotizacionesService(CotizacionRepository cotizacionRepository,
                                     CotizacionDocumentoRepository documentoRepository,
                                     ClienteRepository clienteRepository, UsuarioRepository usuarioRepository) {
        this.cotizacionRepository = cotizacionRepository;
        this.documentoRepository = documentoRepository;
        this.clienteRepository = clienteRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public record LibroCotizacionesFila(
            Long cotizacionId,
            String numero,
            LocalDate fecha,
            String clienteNombre,
            String clienteRut,
            EstadoCotizacion estado,
            BigDecimal montoNeto,
            BigDecimal montoIva,
            BigDecimal montoTotal,
            String usuario,
            String documentosRelacionados) {
    }

    public record LibroCotizacionesResumen(
            int cantidad, BigDecimal montoNeto, BigDecimal montoIva, BigDecimal montoTotal) {
    }

    public record LibroCotizacionesResponse(
            LocalDate desde, LocalDate hasta, EstadoCotizacion estado,
            List<LibroCotizacionesFila> filas, LibroCotizacionesResumen resumen) {
    }

    public LibroCotizacionesResponse generar(Long tenantId, LocalDate desde, LocalDate hasta,
                                              EstadoCotizacion estado) {
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'");
        }

        List<Cotizacion> cotizaciones = cotizacionRepository
                .findByTenantIdAndFechaEmisionBetweenOrderByFolioAsc(tenantId, desde, hasta).stream()
                .filter(c -> estado == null || c.getEstado() == estado)
                .toList();

        if (cotizaciones.isEmpty()) {
            return new LibroCotizacionesResponse(desde, hasta, estado, List.of(),
                    new LibroCotizacionesResumen(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        }

        List<Long> clienteIds = cotizaciones.stream().map(Cotizacion::getClienteId).distinct().toList();
        Map<Long, Cliente> clientes = clienteRepository.findByTenantIdAndIdIn(tenantId, clienteIds).stream()
                .collect(Collectors.toMap(Cliente::getId, Function.identity()));

        List<Long> vendedorIds = cotizaciones.stream().map(Cotizacion::getVendedorId).distinct().toList();
        Map<Long, String> vendedores = usuarioRepository.findAllById(vendedorIds).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getNombre));

        List<Long> cotizacionIds = cotizaciones.stream().map(Cotizacion::getId).toList();
        Map<Long, List<CotizacionDocumento>> documentos = documentoRepository
                .findByTenantIdAndCotizacionIdIn(tenantId, cotizacionIds).stream()
                .collect(Collectors.groupingBy(CotizacionDocumento::getCotizacionId));

        List<LibroCotizacionesFila> filas = cotizaciones.stream()
                .map(c -> mapearFila(c, clientes.get(c.getClienteId()), vendedores.get(c.getVendedorId()),
                        documentos.getOrDefault(c.getId(), List.of())))
                .toList();

        return new LibroCotizacionesResponse(desde, hasta, estado, filas, resumenDe(filas));
    }

    private LibroCotizacionesFila mapearFila(Cotizacion cotizacion, Cliente cliente, String vendedor,
                                              List<CotizacionDocumento> documentos) {
        String relacionados = documentos.isEmpty() ? SIN_DATO : documentos.stream()
                .map(d -> d.getNumero() != null ? d.getNumero() : d.getTipoDocumento() + " #" + d.getDocumentoId())
                .collect(Collectors.joining(", "));

        return new LibroCotizacionesFila(
                cotizacion.getId(),
                NumeroCotizacion.formatear(cotizacion.getFolio()),
                cotizacion.getFechaEmision(),
                cliente != null ? cliente.getNombre() : SIN_DATO,
                cliente != null ? cliente.getRut() : null,
                cotizacion.getEstado(),
                cotizacion.getMontoNeto(),
                cotizacion.getMontoIva(),
                cotizacion.getMontoTotal(),
                vendedor != null ? vendedor : SIN_DATO,
                relacionados);
    }

    private LibroCotizacionesResumen resumenDe(List<LibroCotizacionesFila> filas) {
        return new LibroCotizacionesResumen(
                filas.size(),
                sumar(filas, LibroCotizacionesFila::montoNeto),
                sumar(filas, LibroCotizacionesFila::montoIva),
                sumar(filas, LibroCotizacionesFila::montoTotal));
    }

    private BigDecimal sumar(List<LibroCotizacionesFila> filas, Function<LibroCotizacionesFila, BigDecimal> extractor) {
        return filas.stream().map(extractor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
