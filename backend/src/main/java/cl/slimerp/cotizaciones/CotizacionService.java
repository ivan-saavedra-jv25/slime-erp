package cl.slimerp.cotizaciones;

import cl.slimerp.catalogo.Cliente;
import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.catalogo.FormaPago;
import cl.slimerp.catalogo.FormaPagoRepository;
import cl.slimerp.catalogo.Producto;
import cl.slimerp.catalogo.ProductoRepository;
import cl.slimerp.common.PaginaResponse;
import cl.slimerp.common.UsuarioActualService;
import cl.slimerp.config.TenantContext;
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import cl.slimerp.ventas.CalculadoraMontosVenta;
import cl.slimerp.ventas.TipoDocumentoVenta;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// Cotizaciones: documento comercial con ciclo de vida propio. No compromete
// stock ni genera deuda — solo registra lo ofrecido al cliente y su estado.
// Cada cambio queda asentado en cotizacion_evento (historial append-only).
@Service
public class CotizacionService {

    private static final Set<String> CAMPOS_ORDEN = Set.of("fechaEmision", "folio", "montoTotal");

    private final CotizacionRepository cotizacionRepository;
    private final CotizacionEventoRepository eventoRepository;
    private final CotizacionDocumentoRepository documentoRepository;
    private final CotizacionFolioService folioService;
    private final ClienteRepository clienteRepository;
    private final ProductoRepository productoRepository;
    private final FormaPagoRepository formaPagoRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioActualService usuarioActualService;

    public CotizacionService(CotizacionRepository cotizacionRepository, CotizacionEventoRepository eventoRepository,
                              CotizacionDocumentoRepository documentoRepository,
                              CotizacionFolioService folioService, ClienteRepository clienteRepository,
                              ProductoRepository productoRepository, FormaPagoRepository formaPagoRepository,
                              UsuarioRepository usuarioRepository, UsuarioActualService usuarioActualService) {
        this.cotizacionRepository = cotizacionRepository;
        this.eventoRepository = eventoRepository;
        this.documentoRepository = documentoRepository;
        this.folioService = folioService;
        this.clienteRepository = clienteRepository;
        this.productoRepository = productoRepository;
        this.formaPagoRepository = formaPagoRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioActualService = usuarioActualService;
    }

    public record LineaCotizacion(
            Long id, Long productoId, String codigo, String descripcion,
            BigDecimal cantidad, BigDecimal precioUnitario, BigDecimal descuento, BigDecimal subtotal) {
    }

    public record EventoCotizacion(
            LocalDateTime fecha, String usuario, AccionCotizacion accion,
            EstadoCotizacion estadoAnterior, EstadoCotizacion estadoNuevo, String detalle) {
    }

    public record CotizacionResumen(
            Long id, Integer folio, String numero, EstadoCotizacion estado,
            LocalDate fechaEmision, LocalDate fechaVencimiento,
            String clienteNombre, String clienteRut, String vendedorNombre,
            BigDecimal montoTotal) {
    }

    public record DocumentoRelacionado(String tipoDocumento, Long documentoId, String numero, LocalDateTime fecha) {
    }

    public record ConteoPorEstado(EstadoCotizacion estado, int cantidad, BigDecimal monto) {
    }

    public record DashboardCotizaciones(
            LocalDate desde, LocalDate hasta,
            int cantidad, int pendientes, int aceptadas, int rechazadas, int enviadas,
            BigDecimal montoCotizado, BigDecimal montoAceptado,
            Double tasaConversion,
            List<ConteoPorEstado> porEstado) {
    }

    public record CotizacionCompleta(
            Long id, Integer folio, String numero, EstadoCotizacion estado,
            LocalDate fechaEmision, LocalDate fechaVencimiento, boolean exenta,
            Long clienteId, String clienteNombre, String clienteRazonSocial, String clienteRut,
            String clienteDireccion, String clienteEmail, String clienteTelefono,
            Long vendedorId, String vendedorNombre,
            Long formaPagoId, String formaPagoNombre,
            String condicionesComerciales, String observaciones, String motivo,
            BigDecimal descuento, BigDecimal montoSubtotal, BigDecimal montoDescuento,
            BigDecimal montoNeto, BigDecimal montoIva, BigDecimal montoTotal,
            List<LineaCotizacion> lineas, List<EventoCotizacion> eventos,
            List<DocumentoRelacionado> documentosRelacionados) {
    }

    // --- Escritura -----------------------------------------------------------

    @Transactional
    public CotizacionCompleta crear(CotizacionRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Long usuarioId = usuarioActualService.idUsuarioActual(tenantId);

        Cotizacion cotizacion = nuevaDesde(request, tenantId, usuarioId);
        cotizacion = cotizacionRepository.save(cotizacion);

        registrarEvento(cotizacion, AccionCotizacion.CREADA, null, EstadoCotizacion.BORRADOR, null, usuarioId);
        return detalleDe(cotizacion);
    }

    @Transactional
    public CotizacionCompleta actualizar(Long id, CotizacionRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Cotizacion cotizacion = buscarEntidad(id, tenantId);
        exigirEstado(cotizacion, "editar", EstadoCotizacion.BORRADOR);

        Cliente cliente = validarCliente(request.clienteId(), tenantId);
        validarFechas(request);
        Long formaPagoId = validarFormaPago(request.formaPagoId(), tenantId);

        cotizacion.setClienteId(cliente.getId());
        cotizacion.setFormaPagoId(formaPagoId);
        cotizacion.setFechaEmision(request.fechaEmision());
        cotizacion.setFechaVencimiento(request.fechaVencimiento());
        cotizacion.setExenta(request.exenta());
        cotizacion.setCondicionesComerciales(request.condicionesComerciales());
        cotizacion.setObservaciones(request.observaciones());
        cotizacion.setFechaActualizacion(LocalDateTime.now());

        cotizacion.getDetalle().clear();
        aplicarLineasYMontos(cotizacion, request, tenantId);
        cotizacion = cotizacionRepository.save(cotizacion);

        registrarEvento(cotizacion, AccionCotizacion.EDITADA, EstadoCotizacion.BORRADOR, EstadoCotizacion.BORRADOR,
                null, usuarioActualService.idUsuarioActual(tenantId));
        return detalleDe(cotizacion);
    }

    @Transactional
    public void eliminar(Long id) {
        Long tenantId = TenantContext.getTenantId();
        Cotizacion cotizacion = buscarEntidad(id, tenantId);
        exigirEstado(cotizacion, "eliminar", EstadoCotizacion.BORRADOR);
        // El folio ya consumido no se reutiliza: queda un hueco en la numeración,
        // igual que con un documento anulado.
        cotizacionRepository.delete(cotizacion);
    }

    @Transactional
    public CotizacionCompleta enviar(Long id) {
        return cambiarEstado(id, "enviar", EstadoCotizacion.ENVIADA, AccionCotizacion.ENVIADA, null,
                EstadoCotizacion.BORRADOR);
    }

    @Transactional
    public CotizacionCompleta aceptar(Long id) {
        return cambiarEstado(id, "aceptar", EstadoCotizacion.ACEPTADA, AccionCotizacion.ACEPTADA, null,
                EstadoCotizacion.ENVIADA);
    }

    @Transactional
    public CotizacionCompleta rechazar(Long id, String motivo) {
        return cambiarEstado(id, "rechazar", EstadoCotizacion.RECHAZADA, AccionCotizacion.RECHAZADA, motivo,
                EstadoCotizacion.ENVIADA);
    }

    @Transactional
    public CotizacionCompleta cancelar(Long id, String motivo) {
        return cambiarEstado(id, "cancelar", EstadoCotizacion.CANCELADA, AccionCotizacion.CANCELADA, motivo,
                EstadoCotizacion.BORRADOR, EstadoCotizacion.ENVIADA);
    }

    @Transactional
    public CotizacionCompleta duplicar(Long id) {
        Long tenantId = TenantContext.getTenantId();
        Long usuarioId = usuarioActualService.idUsuarioActual(tenantId);
        Cotizacion original = buscarEntidad(id, tenantId);

        // Se mantiene la ventana de vigencia original (misma cantidad de días),
        // pero contada desde hoy.
        LocalDate hoy = LocalDate.now();
        long diasVigencia = ChronoUnit.DAYS.between(original.getFechaEmision(), original.getFechaVencimiento());

        Cotizacion copia = Cotizacion.builder()
                .tenantId(tenantId)
                .folio(folioService.siguienteFolio(tenantId))
                .clienteId(original.getClienteId())
                .vendedorId(usuarioId)
                .formaPagoId(original.getFormaPagoId())
                .estado(EstadoCotizacion.BORRADOR)
                .exenta(original.isExenta())
                .fechaEmision(hoy)
                .fechaVencimiento(hoy.plusDays(diasVigencia))
                .descuento(original.getDescuento())
                .condicionesComerciales(original.getCondicionesComerciales())
                .observaciones(original.getObservaciones())
                .montoSubtotal(original.getMontoSubtotal())
                .montoDescuento(original.getMontoDescuento())
                .montoNeto(original.getMontoNeto())
                .montoIva(original.getMontoIva())
                .montoTotal(original.getMontoTotal())
                .build();

        List<CotizacionDetalle> lineas = new ArrayList<>();
        for (CotizacionDetalle linea : original.getDetalle()) {
            lineas.add(CotizacionDetalle.builder()
                    .cotizacion(copia)
                    .productoId(linea.getProductoId())
                    .codigo(linea.getCodigo())
                    .descripcion(linea.getDescripcion())
                    .cantidad(linea.getCantidad())
                    .precioUnitario(linea.getPrecioUnitario())
                    .descuento(linea.getDescuento())
                    .subtotal(linea.getSubtotal())
                    .build());
        }
        copia.setDetalle(lineas);
        Cotizacion guardada = cotizacionRepository.save(copia);

        String numeroOriginal = NumeroCotizacion.formatear(original.getFolio());
        String numeroCopia = NumeroCotizacion.formatear(guardada.getFolio());
        registrarEvento(original, AccionCotizacion.DUPLICADA, original.getEstado(), original.getEstado(),
                "Duplicada en " + numeroCopia, usuarioId);
        registrarEvento(guardada, AccionCotizacion.CREADA, null, EstadoCotizacion.BORRADOR,
                "Duplicada desde " + numeroOriginal, usuarioId);

        return detalleDe(guardada);
    }

    // Usado por el job de vencimiento, que corre sin usuario autenticado.
    @Transactional
    public void marcarVencida(Cotizacion cotizacion) {
        EstadoCotizacion anterior = cotizacion.getEstado();
        cotizacion.setEstado(EstadoCotizacion.VENCIDA);
        cotizacionRepository.save(cotizacion);
        registrarEvento(cotizacion, AccionCotizacion.VENCIDA, anterior, EstadoCotizacion.VENCIDA,
                "Venció el " + cotizacion.getFechaVencimiento(), null);
    }

    // --- Lectura -------------------------------------------------------------

    public CotizacionCompleta obtener(Long id) {
        return detalleDe(buscarEntidad(id, TenantContext.getTenantId()));
    }

    public Cotizacion obtenerEntidad(Long id) {
        return buscarEntidad(id, TenantContext.getTenantId());
    }

    public PaginaResponse<CotizacionResumen> buscar(EstadoCotizacion estado, Long clienteId, Long vendedorId,
                                                     LocalDate desde, LocalDate hasta, String q,
                                                     String orden, String direccion, int pagina, int tamano) {
        if (pagina < 0) {
            throw new IllegalArgumentException("La página debe ser 0 o mayor");
        }
        if (tamano < 1) {
            throw new IllegalArgumentException("El tamaño de página debe ser mayor que 0");
        }
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'");
        }
        Long tenantId = TenantContext.getTenantId();

        // Se arma dinámicamente en vez de usar "(:param IS NULL OR ...)" en JPQL:
        // el driver de Postgres no logra inferir el tipo de un parámetro que solo
        // se compara contra IS NULL (ver TransaccionPagoService.buscar).
        Specification<Cotizacion> spec = (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
        if (estado != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("estado"), estado));
        }
        if (clienteId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("clienteId"), clienteId));
        }
        if (vendedorId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("vendedorId"), vendedorId));
        }
        if (desde != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("fechaEmision"), desde));
        }
        if (hasta != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("fechaEmision"), hasta));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(filtroBusqueda(tenantId, q.trim()));
        }

        var pageable = PageRequest.of(pagina, tamano, ordenamiento(orden, direccion));
        var pagina_ = cotizacionRepository.findAll(spec, pageable);

        List<Cotizacion> cotizaciones = pagina_.getContent();
        Map<Long, Cliente> clientes = clientesDe(tenantId, cotizaciones);
        Map<Long, Usuario> vendedores = vendedoresDe(tenantId, cotizaciones);

        List<CotizacionResumen> contenido = cotizaciones.stream()
                .map(c -> resumenDe(c, clientes.get(c.getClienteId()), vendedores.get(c.getVendedorId())))
                .toList();
        return new PaginaResponse<>(contenido, pagina_.getTotalElements());
    }

    public DashboardCotizaciones dashboard(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'");
        }
        Long tenantId = TenantContext.getTenantId();
        List<Cotizacion> cotizaciones = cotizacionRepository
                .findByTenantIdAndFechaEmisionBetweenOrderByFolioAsc(tenantId, desde, hasta);

        Map<EstadoCotizacion, List<Cotizacion>> porEstado = cotizaciones.stream()
                .collect(Collectors.groupingBy(Cotizacion::getEstado));

        // "Enviadas" es el universo comercial del período: todo lo que salió de
        // borrador. Se cuenta por estado actual (una aceptada o rechazada pasó
        // antes por enviada), porque contar solo las que siguen en ENVIADA haría
        // que aceptar una cotización bajara el denominador de la conversión.
        int enviadas = cantidad(porEstado, EstadoCotizacion.ENVIADA)
                + cantidad(porEstado, EstadoCotizacion.ACEPTADA)
                + cantidad(porEstado, EstadoCotizacion.RECHAZADA)
                + cantidad(porEstado, EstadoCotizacion.VENCIDA);
        int aceptadas = cantidad(porEstado, EstadoCotizacion.ACEPTADA);
        Double tasaConversion = enviadas == 0 ? null : (aceptadas * 100.0) / enviadas;

        List<ConteoPorEstado> conteos = java.util.Arrays.stream(EstadoCotizacion.values())
                .map(estado -> new ConteoPorEstado(estado, cantidad(porEstado, estado), monto(porEstado, estado)))
                .toList();

        return new DashboardCotizaciones(
                desde, hasta,
                cotizaciones.size(),
                cantidad(porEstado, EstadoCotizacion.ENVIADA),
                aceptadas,
                cantidad(porEstado, EstadoCotizacion.RECHAZADA),
                enviadas,
                sumarMontos(cotizaciones),
                monto(porEstado, EstadoCotizacion.ACEPTADA),
                tasaConversion,
                conteos);
    }

    private int cantidad(Map<EstadoCotizacion, List<Cotizacion>> porEstado, EstadoCotizacion estado) {
        return porEstado.getOrDefault(estado, List.of()).size();
    }

    private BigDecimal monto(Map<EstadoCotizacion, List<Cotizacion>> porEstado, EstadoCotizacion estado) {
        return sumarMontos(porEstado.getOrDefault(estado, List.of()));
    }

    private BigDecimal sumarMontos(List<Cotizacion> cotizaciones) {
        return cotizaciones.stream().map(Cotizacion::getMontoTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // El texto puede ser el número de la cotización (COT-000123, "123") o el
    // nombre/RUT del cliente. Los clientes se resuelven a ids en una consulta
    // aparte para que el filtro siga corriendo en la base y la paginación no
    // tenga que traer todo a memoria.
    private Specification<Cotizacion> filtroBusqueda(Long tenantId, String q) {
        List<Long> clienteIds = clienteRepository.idsPorBusqueda(tenantId, "%" + q.toLowerCase() + "%");
        Integer folio = folioDeTexto(q);

        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> alternativas = new ArrayList<>();
            if (!clienteIds.isEmpty()) {
                alternativas.add(root.get("clienteId").in(clienteIds));
            }
            if (folio != null) {
                alternativas.add(cb.equal(root.get("folio"), folio));
            }
            return alternativas.isEmpty()
                    ? cb.disjunction()
                    : cb.or(alternativas.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private Integer folioDeTexto(String q) {
        String digitos = q.replaceAll("[^0-9]", "");
        if (digitos.isEmpty() || digitos.length() > 9) {
            return null;
        }
        return Integer.valueOf(digitos);
    }

    private Sort ordenamiento(String orden, String direccion) {
        String campo = CAMPOS_ORDEN.contains(orden) ? orden : "fechaEmision";
        Sort.Direction dir = "asc".equalsIgnoreCase(direccion) ? Sort.Direction.ASC : Sort.Direction.DESC;
        // Desempate por folio: dos cotizaciones del mismo día deben salir siempre
        // en el mismo orden entre páginas.
        return Sort.by(dir, campo).and(Sort.by(dir, "folio"));
    }

    // --- Helpers de escritura ------------------------------------------------

    private Cotizacion nuevaDesde(CotizacionRequest request, Long tenantId, Long usuarioId) {
        Cliente cliente = validarCliente(request.clienteId(), tenantId);
        validarFechas(request);
        Long formaPagoId = validarFormaPago(request.formaPagoId(), tenantId);

        Cotizacion cotizacion = Cotizacion.builder()
                .tenantId(tenantId)
                .folio(folioService.siguienteFolio(tenantId))
                .clienteId(cliente.getId())
                .vendedorId(usuarioId)
                .formaPagoId(formaPagoId)
                .estado(EstadoCotizacion.BORRADOR)
                .exenta(request.exenta())
                .fechaEmision(request.fechaEmision())
                .fechaVencimiento(request.fechaVencimiento())
                .condicionesComerciales(request.condicionesComerciales())
                .observaciones(request.observaciones())
                .build();
        aplicarLineasYMontos(cotizacion, request, tenantId);
        return cotizacion;
    }

    // Calcula líneas y totales. Los precios de línea son netos: el IVA se suma
    // por encima, salvo que la cotización sea exenta. Se delega en
    // CalculadoraMontosVenta (modo FACTURA = "la suma de detalle es neta") para
    // no duplicar la lógica de IVA ni romper el invariante neto + iva == total.
    private void aplicarLineasYMontos(Cotizacion cotizacion, CotizacionRequest request, Long tenantId) {
        List<CotizacionDetalle> lineas = new ArrayList<>();
        BigDecimal subtotalBrutoTotal = BigDecimal.ZERO;
        BigDecimal descuentoLineas = BigDecimal.ZERO;
        BigDecimal sumaDetalle = BigDecimal.ZERO;

        for (CotizacionRequest.Item item : request.items()) {
            Producto producto = productoRepository.findByIdAndTenantIdAndActivoTrue(item.productoId(), tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + item.productoId()));

            if (item.cantidad().signum() <= 0) {
                throw new IllegalArgumentException("La cantidad de " + producto.getNombre() + " debe ser mayor que 0");
            }
            if (item.precioUnitario().signum() < 0) {
                throw new IllegalArgumentException("El precio de " + producto.getNombre() + " no puede ser negativo");
            }

            BigDecimal subtotalBruto = item.precioUnitario().multiply(item.cantidad());
            BigDecimal descuentoLinea = item.descuento() != null ? item.descuento() : BigDecimal.ZERO;
            if (descuentoLinea.signum() < 0) {
                throw new IllegalArgumentException("El descuento de " + producto.getNombre() + " no puede ser negativo");
            }
            if (descuentoLinea.compareTo(subtotalBruto) > 0) {
                throw new IllegalArgumentException(
                        "El descuento de " + producto.getNombre() + " no puede superar el subtotal de la línea");
            }

            BigDecimal subtotal = subtotalBruto.subtract(descuentoLinea);
            subtotalBrutoTotal = subtotalBrutoTotal.add(subtotalBruto);
            descuentoLineas = descuentoLineas.add(descuentoLinea);
            sumaDetalle = sumaDetalle.add(subtotal);

            lineas.add(CotizacionDetalle.builder()
                    .cotizacion(cotizacion)
                    .productoId(producto.getId())
                    .codigo(producto.getSku())
                    .descripcion(producto.getNombre())
                    .cantidad(item.cantidad())
                    .precioUnitario(item.precioUnitario())
                    .descuento(descuentoLinea)
                    .subtotal(subtotal)
                    .build());
        }

        BigDecimal descuentoGlobal = request.descuento() != null ? request.descuento() : BigDecimal.ZERO;
        if (descuentoGlobal.signum() < 0) {
            throw new IllegalArgumentException("El descuento global no puede ser negativo");
        }
        if (descuentoGlobal.compareTo(sumaDetalle) > 0) {
            throw new IllegalArgumentException("El descuento global no puede superar el total de las líneas");
        }

        CalculadoraMontosVenta.Montos montos = CalculadoraMontosVenta.calcular(
                TipoDocumentoVenta.FACTURA, cotizacion.isExenta(), sumaDetalle.subtract(descuentoGlobal));

        cotizacion.getDetalle().addAll(lineas);
        cotizacion.setDescuento(descuentoGlobal);
        cotizacion.setMontoSubtotal(subtotalBrutoTotal);
        cotizacion.setMontoDescuento(descuentoLineas.add(descuentoGlobal));
        cotizacion.setMontoNeto(montos.neto());
        cotizacion.setMontoIva(montos.iva());
        cotizacion.setMontoTotal(montos.total());
    }

    private CotizacionCompleta cambiarEstado(Long id, String accionTexto, EstadoCotizacion destino,
                                              AccionCotizacion accion, String motivo,
                                              EstadoCotizacion... permitidos) {
        Long tenantId = TenantContext.getTenantId();
        Long usuarioId = usuarioActualService.idUsuarioActual(tenantId);
        Cotizacion cotizacion = buscarEntidad(id, tenantId);
        exigirEstado(cotizacion, accionTexto, permitidos);

        EstadoCotizacion anterior = cotizacion.getEstado();
        cotizacion.setEstado(destino);
        cotizacion.setFechaActualizacion(LocalDateTime.now());
        if (motivo != null && !motivo.isBlank()) {
            cotizacion.setMotivo(motivo.trim());
        }
        cotizacion = cotizacionRepository.save(cotizacion);

        registrarEvento(cotizacion, accion, anterior, destino, cotizacion.getMotivo(), usuarioId);
        return detalleDe(cotizacion);
    }

    private void registrarEvento(Cotizacion cotizacion, AccionCotizacion accion, EstadoCotizacion anterior,
                                  EstadoCotizacion nuevo, String detalle, Long usuarioId) {
        eventoRepository.save(CotizacionEvento.builder()
                .tenantId(cotizacion.getTenantId())
                .cotizacionId(cotizacion.getId())
                .usuarioId(usuarioId)
                .accion(accion)
                .estadoAnterior(anterior)
                .estadoNuevo(nuevo)
                .detalle(detalle)
                .build());
    }

    private void exigirEstado(Cotizacion cotizacion, String accion, EstadoCotizacion... permitidos) {
        for (EstadoCotizacion permitido : permitidos) {
            if (cotizacion.getEstado() == permitido) {
                return;
            }
        }
        throw new IllegalArgumentException(
                "No se puede " + accion + " una cotización en estado " + cotizacion.getEstado());
    }

    private Cotizacion buscarEntidad(Long id, Long tenantId) {
        return cotizacionRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Cotización no encontrada: " + id));
    }

    private Cliente validarCliente(Long clienteId, Long tenantId) {
        return clienteRepository.findByIdAndTenantIdAndActivoTrue(clienteId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado: " + clienteId));
    }

    private Long validarFormaPago(Long formaPagoId, Long tenantId) {
        if (formaPagoId == null) {
            return null;
        }
        return formaPagoRepository.findByIdAndTenantIdAndActivoTrue(formaPagoId, tenantId)
                .map(FormaPago::getId)
                .orElseThrow(() -> new IllegalArgumentException("Forma de pago no encontrada: " + formaPagoId));
    }

    private void validarFechas(CotizacionRequest request) {
        if (request.fechaVencimiento().isBefore(request.fechaEmision())) {
            throw new IllegalArgumentException(
                    "La fecha de vencimiento no puede ser anterior a la fecha de emisión");
        }
    }

    // --- Helpers de lectura --------------------------------------------------

    private CotizacionCompleta detalleDe(Cotizacion cotizacion) {
        Long tenantId = cotizacion.getTenantId();
        Cliente cliente = clienteRepository.findById(cotizacion.getClienteId()).orElse(null);
        Usuario vendedor = usuarioRepository.findById(cotizacion.getVendedorId()).orElse(null);
        String formaPagoNombre = cotizacion.getFormaPagoId() == null ? null
                : formaPagoRepository.findById(cotizacion.getFormaPagoId()).map(FormaPago::getNombre).orElse(null);

        List<LineaCotizacion> lineas = cotizacion.getDetalle().stream()
                .map(l -> new LineaCotizacion(l.getId(), l.getProductoId(), l.getCodigo(), l.getDescripcion(),
                        l.getCantidad(), l.getPrecioUnitario(), l.getDescuento(), l.getSubtotal()))
                .toList();

        List<CotizacionEvento> eventos = eventoRepository
                .findByTenantIdAndCotizacionIdOrderByFechaAscIdAsc(tenantId, cotizacion.getId());
        Map<Long, String> nombresUsuario = nombresDeUsuarios(eventos);
        List<EventoCotizacion> historial = eventos.stream()
                .map(e -> new EventoCotizacion(e.getFecha(),
                        e.getUsuarioId() == null ? "Sistema" : nombresUsuario.getOrDefault(e.getUsuarioId(), "—"),
                        e.getAccion(), e.getEstadoAnterior(), e.getEstadoNuevo(), e.getDetalle()))
                .toList();

        return new CotizacionCompleta(
                cotizacion.getId(), cotizacion.getFolio(), NumeroCotizacion.formatear(cotizacion.getFolio()),
                cotizacion.getEstado(), cotizacion.getFechaEmision(), cotizacion.getFechaVencimiento(),
                cotizacion.isExenta(),
                cotizacion.getClienteId(),
                cliente != null ? cliente.getNombre() : "—",
                cliente != null ? cliente.getRazonSocial() : null,
                cliente != null ? cliente.getRut() : null,
                cliente != null ? cliente.getDireccion() : null,
                cliente != null ? cliente.getEmail() : null,
                cliente != null ? cliente.getTelefono() : null,
                cotizacion.getVendedorId(), vendedor != null ? vendedor.getNombre() : "—",
                cotizacion.getFormaPagoId(), formaPagoNombre,
                cotizacion.getCondicionesComerciales(), cotizacion.getObservaciones(), cotizacion.getMotivo(),
                cotizacion.getDescuento(), cotizacion.getMontoSubtotal(), cotizacion.getMontoDescuento(),
                cotizacion.getMontoNeto(), cotizacion.getMontoIva(), cotizacion.getMontoTotal(),
                lineas, historial, documentosDe(tenantId, cotizacion.getId()));
    }

    private List<DocumentoRelacionado> documentosDe(Long tenantId, Long cotizacionId) {
        return documentoRepository.findByTenantIdAndCotizacionIdOrderByFechaAsc(tenantId, cotizacionId).stream()
                .map(d -> new DocumentoRelacionado(d.getTipoDocumento(), d.getDocumentoId(), d.getNumero(),
                        d.getFecha()))
                .toList();
    }

    private CotizacionResumen resumenDe(Cotizacion cotizacion, Cliente cliente, Usuario vendedor) {
        return new CotizacionResumen(
                cotizacion.getId(), cotizacion.getFolio(), NumeroCotizacion.formatear(cotizacion.getFolio()),
                cotizacion.getEstado(), cotizacion.getFechaEmision(), cotizacion.getFechaVencimiento(),
                cliente != null ? cliente.getNombre() : "—",
                cliente != null ? cliente.getRut() : null,
                vendedor != null ? vendedor.getNombre() : "—",
                cotizacion.getMontoTotal());
    }

    private Map<Long, Cliente> clientesDe(Long tenantId, List<Cotizacion> cotizaciones) {
        List<Long> ids = cotizaciones.stream().map(Cotizacion::getClienteId).distinct().toList();
        return ids.isEmpty() ? Map.of()
                : clienteRepository.findByTenantIdAndIdIn(tenantId, ids).stream()
                        .collect(Collectors.toMap(Cliente::getId, Function.identity()));
    }

    private Map<Long, Usuario> vendedoresDe(Long tenantId, List<Cotizacion> cotizaciones) {
        List<Long> ids = cotizaciones.stream().map(Cotizacion::getVendedorId).distinct().toList();
        return ids.isEmpty() ? Map.of()
                : usuarioRepository.findAllById(ids).stream()
                        .filter(u -> u.getTenantId().equals(tenantId))
                        .collect(Collectors.toMap(Usuario::getId, Function.identity()));
    }

    private Map<Long, String> nombresDeUsuarios(List<CotizacionEvento> eventos) {
        List<Long> ids = eventos.stream().map(CotizacionEvento::getUsuarioId).filter(java.util.Objects::nonNull)
                .distinct().toList();
        return ids.isEmpty() ? Map.of()
                : usuarioRepository.findAllById(ids).stream()
                        .collect(Collectors.toMap(Usuario::getId, Usuario::getNombre));
    }
}
