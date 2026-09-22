package cl.slimerp.notasventa;

import cl.slimerp.catalogo.Cliente;
import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.catalogo.FormaPago;
import cl.slimerp.catalogo.FormaPagoRepository;
import cl.slimerp.catalogo.Producto;
import cl.slimerp.catalogo.ProductoRepository;
import cl.slimerp.common.PaginaResponse;
import cl.slimerp.common.UsuarioActualService;
import cl.slimerp.config.TenantContext;
import cl.slimerp.cotizaciones.Cotizacion;
import cl.slimerp.cotizaciones.CotizacionDocumento;
import cl.slimerp.cotizaciones.CotizacionDocumentoRepository;
import cl.slimerp.cotizaciones.CotizacionRepository;
import cl.slimerp.cotizaciones.EstadoCotizacion;
import cl.slimerp.cotizaciones.NumeroCotizacion;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// Nota de Venta: operación comercial confirmada, segundo eslabón después de la
// cotización. No compromete stock ni genera deuda — el despacho (Guía) y la
// facturación (DTE) son módulos futuros que colgarán de nota_venta_documento.
// Cada cambio queda asentado en nota_venta_evento (historial append-only) y el
// estado de entrega se deriva de las cantidades entregadas por línea.
@Service
public class NotaVentaService {

    private static final Set<String> CAMPOS_ORDEN = Set.of("fechaEmision", "folio", "montoTotal");

    private final NotaVentaRepository notaVentaRepository;
    private final NotaVentaEventoRepository eventoRepository;
    private final NotaVentaDocumentoRepository documentoRepository;
    private final NotaVentaEntregaRepository entregaRepository;
    private final NotaVentaFolioService folioService;
    private final ClienteRepository clienteRepository;
    private final ProductoRepository productoRepository;
    private final FormaPagoRepository formaPagoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CotizacionRepository cotizacionRepository;
    private final CotizacionDocumentoRepository cotizacionDocumentoRepository;
    private final UsuarioActualService usuarioActualService;

    public NotaVentaService(NotaVentaRepository notaVentaRepository, NotaVentaEventoRepository eventoRepository,
                            NotaVentaDocumentoRepository documentoRepository, NotaVentaEntregaRepository entregaRepository,
                            NotaVentaFolioService folioService, ClienteRepository clienteRepository,
                            ProductoRepository productoRepository, FormaPagoRepository formaPagoRepository,
                            UsuarioRepository usuarioRepository, CotizacionRepository cotizacionRepository,
                            CotizacionDocumentoRepository cotizacionDocumentoRepository,
                            UsuarioActualService usuarioActualService) {
        this.notaVentaRepository = notaVentaRepository;
        this.eventoRepository = eventoRepository;
        this.documentoRepository = documentoRepository;
        this.entregaRepository = entregaRepository;
        this.folioService = folioService;
        this.clienteRepository = clienteRepository;
        this.productoRepository = productoRepository;
        this.formaPagoRepository = formaPagoRepository;
        this.usuarioRepository = usuarioRepository;
        this.cotizacionRepository = cotizacionRepository;
        this.cotizacionDocumentoRepository = cotizacionDocumentoRepository;
        this.usuarioActualService = usuarioActualService;
    }

    public record LineaNotaVenta(
            Long id, Long productoId, String codigo, String descripcion,
            BigDecimal cantidad, BigDecimal cantidadEntregada,
            BigDecimal precioUnitario, BigDecimal descuento, BigDecimal subtotal) {
    }

    public record EventoNotaVenta(
            LocalDateTime fecha, String usuario, AccionNotaVenta accion,
            EstadoNotaVenta estadoAnterior, EstadoNotaVenta estadoNuevo, String detalle) {
    }

    public record EntregaLineaNotaVenta(Long productoId, String descripcion, BigDecimal cantidad) {
    }

    public record EntregaNotaVenta(
            Long id, LocalDateTime fecha, String usuario, String observacion,
            List<EntregaLineaNotaVenta> lineas) {
    }

    public record NotaVentaResumen(
            Long id, Integer folio, String numero, EstadoNotaVenta estado,
            OrigenNotaVenta origen, LocalDate fechaEmision, LocalDate fechaEntregaEstimada,
            String clienteNombre, String clienteRut, String vendedorNombre,
            BigDecimal montoTotal) {
    }

    public record DocumentoRelacionado(String tipoDocumento, Long documentoId, String numero, LocalDateTime fecha) {
    }

    public record ConteoPorEstado(EstadoNotaVenta estado, int cantidad, BigDecimal monto) {
    }

    public record DashboardNotasVenta(
            LocalDate desde, LocalDate hasta,
            int cantidad, int confirmadas, int enPreparacion, int pendientesEntrega,
            int entregadas, int facturadas, int canceladas,
            BigDecimal montoTotalVendido,
            List<ConteoPorEstado> porEstado) {
    }

    public record NotaVentaCompleta(
            Long id, Integer folio, String numero, EstadoNotaVenta estado, boolean exenta,
            OrigenNotaVenta origen, Long cotizacionId, String cotizacionNumero,
            String moneda, LocalDate fechaEmision, LocalDate fechaEntregaEstimada,
            Long clienteId, String clienteNombre, String clienteRazonSocial, String clienteRut,
            String clienteDireccion, String clienteEmail, String clienteTelefono,
            Long vendedorId, String vendedorNombre,
            Long formaPagoId, String formaPagoNombre,
            String direccionEntrega, String condicionesVenta, String observaciones, String motivo,
            BigDecimal descuento, BigDecimal montoSubtotal, BigDecimal montoDescuento,
            BigDecimal montoNeto, BigDecimal montoIva, BigDecimal montoTotal,
            List<LineaNotaVenta> lineas, List<EventoNotaVenta> eventos,
            List<EntregaNotaVenta> entregas, List<DocumentoRelacionado> documentosRelacionados) {
    }

    // --- Escritura -----------------------------------------------------------

    @Transactional
    public NotaVentaCompleta crear(NotaVentaRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Long usuarioId = usuarioActualService.idUsuarioActual(tenantId);

        NotaVenta nota = nuevaDesde(request, tenantId, usuarioId, OrigenNotaVenta.VENTA_DIRECTA, null);
        nota = notaVentaRepository.save(nota);

        registrarEvento(nota, AccionNotaVenta.CREADA, null, EstadoNotaVenta.BORRADOR, null, usuarioId);
        return detalleDe(nota);
    }

    // Crea la nota de venta a partir de una cotización ACEPTADA. Copia cliente,
    // vendedor, líneas, precios, descuentos, condiciones y observaciones, y deja
    // el vínculo en cotizacion_documento (tipo NOTA_VENTA) para la trazabilidad
    // de la cadena. No modifica la cotización original.
    @Transactional
    public NotaVentaCompleta crearDesdeCotizacion(Long cotizacionId) {
        Long tenantId = TenantContext.getTenantId();
        Long usuarioId = usuarioActualService.idUsuarioActual(tenantId);

        Cotizacion cotizacion = cotizacionRepository.findByIdAndTenantId(cotizacionId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Cotización no encontrada: " + cotizacionId));
        if (cotizacion.getEstado() != EstadoCotizacion.ACEPTADA) {
            throw new IllegalArgumentException(
                    "Solo se puede crear una nota de venta desde una cotización ACEPTADA (estado actual: "
                            + cotizacion.getEstado() + ")");
        }
        boolean yaExiste = notaVentaRepository.findByTenantIdAndCotizacionId(tenantId, cotizacionId).isPresent();
        if (yaExiste) {
            throw new IllegalArgumentException(
                    "La cotización " + NumeroCotizacion.formatear(cotizacion.getFolio())
                            + " ya tiene una nota de venta");
        }

        NotaVenta nota = NotaVenta.builder()
                .tenantId(tenantId)
                .folio(folioService.siguienteFolio(tenantId))
                .clienteId(cotizacion.getClienteId())
                .vendedorId(usuarioId)
                .formaPagoId(cotizacion.getFormaPagoId())
                .origen(OrigenNotaVenta.COTIZACION)
                .cotizacionId(cotizacionId)
                .estado(EstadoNotaVenta.BORRADOR)
                .exenta(cotizacion.isExenta())
                .moneda("CLP")
                .fechaEmision(LocalDate.now())
                .condicionesVenta(cotizacion.getCondicionesComerciales())
                .observaciones(cotizacion.getObservaciones())
                .build();

        List<NotaVentaDetalle> lineas = new ArrayList<>();
        BigDecimal subtotalBrutoTotal = BigDecimal.ZERO;
        BigDecimal descuentoLineas = BigDecimal.ZERO;
        BigDecimal sumaDetalle = BigDecimal.ZERO;
        for (var linea : cotizacion.getDetalle()) {
            BigDecimal subtotalBruto = linea.getPrecioUnitario().multiply(linea.getCantidad());
            BigDecimal descuentoLinea = linea.getDescuento() != null ? linea.getDescuento() : BigDecimal.ZERO;
            BigDecimal sub = subtotalBruto.subtract(descuentoLinea);
            subtotalBrutoTotal = subtotalBrutoTotal.add(subtotalBruto);
            descuentoLineas = descuentoLineas.add(descuentoLinea);
            sumaDetalle = sumaDetalle.add(sub);

            lineas.add(NotaVentaDetalle.builder()
                    .notaVenta(nota)
                    .productoId(linea.getProductoId())
                    .codigo(linea.getCodigo())
                    .descripcion(linea.getDescripcion())
                    .cantidad(linea.getCantidad())
                    .cantidadEntregada(BigDecimal.ZERO)
                    .precioUnitario(linea.getPrecioUnitario())
                    .descuento(descuentoLinea)
                    .subtotal(sub)
                    .build());
        }
        BigDecimal descuentoGlobal = cotizacion.getDescuento() != null ? cotizacion.getDescuento() : BigDecimal.ZERO;
        aplicarMontos(nota, cotizacion.isExenta(), subtotalBrutoTotal, descuentoLineas, descuentoGlobal, sumaDetalle);
        nota.setDetalle(lineas);

        nota = notaVentaRepository.save(nota);
        registrarEvento(nota, AccionNotaVenta.CREADA, null, EstadoNotaVenta.BORRADOR,
                "Creada desde " + NumeroCotizacion.formatear(cotizacion.getFolio()), usuarioId);

        // Vínculo en la cadena: la cotización pasa a tener este documento derivado.
        cotizacionDocumentoRepository.save(CotizacionDocumento.builder()
                .tenantId(tenantId)
                .cotizacionId(cotizacion.getId())
                .tipoDocumento("NOTA_VENTA")
                .documentoId(nota.getId())
                .numero(NumeroNotaVenta.formatear(nota.getFolio()))
                .build());

        return detalleDe(nota);
    }

    @Transactional
    public NotaVentaCompleta actualizar(Long id, NotaVentaRequest request) {
        Long tenantId = TenantContext.getTenantId();
        NotaVenta nota = buscarEntidad(id, tenantId);
        exigirEstado(nota, "editar", EstadoNotaVenta.BORRADOR);

        Cliente cliente = validarCliente(request.clienteId(), tenantId);
        Long formaPagoId = validarFormaPago(request.formaPagoId(), tenantId);

        nota.setClienteId(cliente.getId());
        nota.setFormaPagoId(formaPagoId);
        nota.setExenta(request.exenta());
        nota.setMoneda(monedaDe(request));
        nota.setFechaEmision(request.fechaEmision());
        nota.setFechaEntregaEstimada(request.fechaEntregaEstimada());
        nota.setDireccionEntrega(request.direccionEntrega());
        nota.setCondicionesVenta(request.condicionesVenta());
        nota.setObservaciones(request.observaciones());
        nota.setFechaActualizacion(LocalDateTime.now());

        nota.getDetalle().clear();
        aplicarLineasYMontos(nota, request, tenantId);
        nota = notaVentaRepository.save(nota);

        registrarEvento(nota, AccionNotaVenta.EDITADA, EstadoNotaVenta.BORRADOR, EstadoNotaVenta.BORRADOR,
                null, usuarioActualService.idUsuarioActual(tenantId));
        return detalleDe(nota);
    }

    @Transactional
    public void eliminar(Long id) {
        Long tenantId = TenantContext.getTenantId();
        NotaVenta nota = buscarEntidad(id, tenantId);
        exigirEstado(nota, "eliminar", EstadoNotaVenta.BORRADOR);
        // El folio ya consumido no se reutiliza: queda un hueco en la numeración,
        // igual que con un documento anulado.
        notaVentaRepository.delete(nota);
    }

    @Transactional
    public NotaVentaCompleta confirmar(Long id) {
        Long tenantId = TenantContext.getTenantId();
        Long usuarioId = usuarioActualService.idUsuarioActual(tenantId);
        NotaVenta nota = buscarEntidad(id, tenantId);
        exigirEstado(nota, "confirmar", EstadoNotaVenta.BORRADOR);

        if (nota.getDetalle().isEmpty()) {
            throw new IllegalArgumentException("No se puede confirmar una nota de venta sin productos");
        }

        EstadoNotaVenta anterior = nota.getEstado();
        nota.setEstado(EstadoNotaVenta.CONFIRMADA);
        nota.setFechaActualizacion(LocalDateTime.now());
        nota = notaVentaRepository.save(nota);

        registrarEvento(nota, AccionNotaVenta.CONFIRMADA, anterior, EstadoNotaVenta.CONFIRMADA, null, usuarioId);
        return detalleDe(nota);
    }

    @Transactional
    public NotaVentaCompleta preparar(Long id) {
        Long tenantId = TenantContext.getTenantId();
        Long usuarioId = usuarioActualService.idUsuarioActual(tenantId);
        NotaVenta nota = buscarEntidad(id, tenantId);
        exigirEstado(nota, "enviar a preparación", EstadoNotaVenta.CONFIRMADA);

        EstadoNotaVenta anterior = nota.getEstado();
        nota.setEstado(EstadoNotaVenta.EN_PREPARACION);
        nota.setFechaActualizacion(LocalDateTime.now());
        nota = notaVentaRepository.save(nota);

        registrarEvento(nota, AccionNotaVenta.PREPARADA, anterior, EstadoNotaVenta.EN_PREPARACION, null, usuarioId);
        return detalleDe(nota);
    }

    // Registra un avance de entrega parcial o total. La suma de las filas de la
    // entrega incrementa cantidad_entregada del detalle y el estado se deriva
    // automáticamente por cantidades (0 entregado -> confirmada/preparación,
    // parte -> parcialmente entregada, todo -> entregada).
    @Transactional
    public NotaVentaCompleta registrarEntrega(Long id, EntregaRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Long usuarioId = usuarioActualService.idUsuarioActual(tenantId);
        NotaVenta nota = buscarEntidad(id, tenantId);
        exigirEstado(nota, "registrar una entrega", EstadoNotaVenta.CONFIRMADA,
                EstadoNotaVenta.EN_PREPARACION, EstadoNotaVenta.PARCIALMENTE_ENTREGADA);

        Map<Long, NotaVentaDetalle> lineasPorProducto = nota.getDetalle().stream()
                .collect(Collectors.toMap(NotaVentaDetalle::getProductoId, Function.identity()));

        NotaVentaEntrega entrega = NotaVentaEntrega.builder()
                .tenantId(tenantId)
                .notaVentaId(nota.getId())
                .observacion(request.observacion())
                .usuarioId(usuarioId)
                .build();
        List<NotaVentaEntregaLinea> entregaLineas = new ArrayList<>();

        for (EntregaRequest.Linea req : request.lineas()) {
            NotaVentaDetalle detalle = lineasPorProducto.get(req.productoId());
            if (detalle == null) {
                throw new IllegalArgumentException("El producto no está en la nota de venta: " + req.productoId());
            }
            if (req.cantidad().signum() <= 0) {
                throw new IllegalArgumentException("La cantidad entregada debe ser mayor que 0");
            }
            BigDecimal nuevaEntregada = detalle.getCantidadEntregada().add(req.cantidad());
            if (nuevaEntregada.compareTo(detalle.getCantidad()) > 0) {
                throw new IllegalArgumentException(
                        "La entrega supera la cantidad solicitada de " + detalle.getDescripcion()
                                + "(solicitada: " + detalle.getCantidad().stripTrailingZeros().toPlainString()
                                + ", ya entregada: " + detalle.getCantidadEntregada().stripTrailingZeros().toPlainString() + ")");
            }
            detalle.setCantidadEntregada(nuevaEntregada);
            entregaLineas.add(NotaVentaEntregaLinea.builder()
                    .entrega(entrega)
                    .productoId(req.productoId())
                    .cantidad(req.cantidad())
                    .build());
        }
        entrega.setLineas(entregaLineas);
        entregaRepository.save(entrega);

        EstadoNotaVenta anterior = nota.getEstado();
        EstadoNotaVenta derivado = derivarEstadoEntrega(nota.getDetalle());
        nota.setEstado(derivado);
        nota.setFechaActualizacion(LocalDateTime.now());
        nota = notaVentaRepository.save(nota);

        AccionNotaVenta accion = derivado == EstadoNotaVenta.ENTREGADA
                ? AccionNotaVenta.ENTREGADA : AccionNotaVenta.ENTREGA_REGISTRADA;
        registrarEvento(nota, accion, anterior, derivado, request.observacion(), usuarioId);
        return detalleDe(nota);
    }

    @Transactional
    public NotaVentaCompleta cancelar(Long id, String motivo) {
        Long tenantId = TenantContext.getTenantId();
        Long usuarioId = usuarioActualService.idUsuarioActual(tenantId);
        NotaVenta nota = buscarEntidad(id, tenantId);
        exigirEstado(nota, "cancelar", EstadoNotaVenta.BORRADOR, EstadoNotaVenta.CONFIRMADA);

        EstadoNotaVenta anterior = nota.getEstado();
        nota.setEstado(EstadoNotaVenta.CANCELADA);
        nota.setMotivo(motivo);
        nota.setFechaActualizacion(LocalDateTime.now());
        nota = notaVentaRepository.save(nota);

        registrarEvento(nota, AccionNotaVenta.CANCELADA, anterior, EstadoNotaVenta.CANCELADA, motivo, usuarioId);
        return detalleDe(nota);
    }

    @Transactional
    public NotaVentaCompleta duplicar(Long id) {
        Long tenantId = TenantContext.getTenantId();
        Long usuarioId = usuarioActualService.idUsuarioActual(tenantId);
        NotaVenta original = buscarEntidad(id, tenantId);
        exigirEstado(original, "duplicar", EstadoNotaVenta.BORRADOR, EstadoNotaVenta.CONFIRMADA);

        // La copia se crea como venta directa: reengancharla a la cotización
        // original duplicaría el vínculo de trazabilidad de la cadena.
        NotaVenta copia = NotaVenta.builder()
                .tenantId(tenantId)
                .folio(folioService.siguienteFolio(tenantId))
                .clienteId(original.getClienteId())
                .vendedorId(usuarioId)
                .formaPagoId(original.getFormaPagoId())
                .origen(OrigenNotaVenta.VENTA_DIRECTA)
                .estado(EstadoNotaVenta.BORRADOR)
                .exenta(original.isExenta())
                .moneda(original.getMoneda())
                .fechaEmision(LocalDate.now())
                .direccionEntrega(original.getDireccionEntrega())
                .condicionesVenta(original.getCondicionesVenta())
                .observaciones(original.getObservaciones())
                .descuento(original.getDescuento())
                .montoSubtotal(original.getMontoSubtotal())
                .montoDescuento(original.getMontoDescuento())
                .montoNeto(original.getMontoNeto())
                .montoIva(original.getMontoIva())
                .montoTotal(original.getMontoTotal())
                .build();

        List<NotaVentaDetalle> lineas = new ArrayList<>();
        for (NotaVentaDetalle linea : original.getDetalle()) {
            lineas.add(NotaVentaDetalle.builder()
                    .notaVenta(copia)
                    .productoId(linea.getProductoId())
                    .codigo(linea.getCodigo())
                    .descripcion(linea.getDescripcion())
                    .cantidad(linea.getCantidad())
                    .cantidadEntregada(BigDecimal.ZERO)
                    .precioUnitario(linea.getPrecioUnitario())
                    .descuento(linea.getDescuento())
                    .subtotal(linea.getSubtotal())
                    .build());
        }
        copia.setDetalle(lineas);
        NotaVenta guardada = notaVentaRepository.save(copia);

        String numeroOriginal = NumeroNotaVenta.formatear(original.getFolio());
        String numeroCopia = NumeroNotaVenta.formatear(guardada.getFolio());
        registrarEvento(original, AccionNotaVenta.DUPLICADA, original.getEstado(), original.getEstado(),
                "Duplicada en " + numeroCopia, usuarioId);
        registrarEvento(guardada, AccionNotaVenta.CREADA, null, EstadoNotaVenta.BORRADOR,
                "Duplicada desde " + numeroOriginal, usuarioId);

        return detalleDe(guardada);
    }

    // --- Lectura -------------------------------------------------------------

    public NotaVentaCompleta obtener(Long id) {
        return detalleDe(buscarEntidad(id, TenantContext.getTenantId()));
    }

    public NotaVenta obtenerEntidad(Long id) {
        return buscarEntidad(id, TenantContext.getTenantId());
    }

    public PaginaResponse<NotaVentaResumen> buscar(EstadoNotaVenta estado, Long clienteId, Long vendedorId,
                                                    LocalDate desde, LocalDate hasta, OrigenNotaVenta origen,
                                                    String q, String orden, String direccion, int pagina, int tamano) {
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
        // se compara contra IS NULL (ver CotizacionService.buscar).
        Specification<NotaVenta> spec = (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
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
        if (origen != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("origen"), origen));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(filtroBusqueda(tenantId, q.trim()));
        }

        var pageable = PageRequest.of(pagina, tamano, ordenamiento(orden, direccion));
        var pagina_ = notaVentaRepository.findAll(spec, pageable);

        List<NotaVenta> notas = pagina_.getContent();
        Map<Long, Cliente> clientes = clientesDe(tenantId, notas);
        Map<Long, Usuario> vendedores = vendedoresDe(tenantId, notas);

        List<NotaVentaResumen> contenido = notas.stream()
                .map(n -> resumenDe(n, clientes.get(n.getClienteId()), vendedores.get(n.getVendedorId())))
                .toList();
        return new PaginaResponse<>(contenido, pagina_.getTotalElements());
    }

    public DashboardNotasVenta dashboard(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'");
        }
        Long tenantId = TenantContext.getTenantId();
        List<NotaVenta> notas = notaVentaRepository
                .findByTenantIdAndFechaEmisionBetweenOrderByFolioAsc(tenantId, desde, hasta);

        Map<EstadoNotaVenta, List<NotaVenta>> porEstado = notas.stream()
                .collect(Collectors.groupingBy(NotaVenta::getEstado));

        // "Pendientes de entrega" son las notas que siguen bajo la regla de
        // despacho: confirmadas, en preparación o parcialmente entregadas.
        int pendientesEntrega = cantidad(porEstado, EstadoNotaVenta.CONFIRMADA)
                + cantidad(porEstado, EstadoNotaVenta.EN_PREPARACION)
                + cantidad(porEstado, EstadoNotaVenta.PARCIALMENTE_ENTREGADA);

        // "Monto total vendido": lo que representa venta efectiva, sin borradores
        // ni canceladas.
        BigDecimal montoVendido = BigDecimal.ZERO;
        for (var estado : List.of(EstadoNotaVenta.CONFIRMADA, EstadoNotaVenta.EN_PREPARACION,
                EstadoNotaVenta.PARCIALMENTE_ENTREGADA, EstadoNotaVenta.ENTREGADA, EstadoNotaVenta.FACTURADA)) {
            montoVendido = montoVendido.add(monto(porEstado, estado));
        }

        List<ConteoPorEstado> conteos = java.util.Arrays.stream(EstadoNotaVenta.values())
                .map(estado -> new ConteoPorEstado(estado, cantidad(porEstado, estado), monto(porEstado, estado)))
                .toList();

        return new DashboardNotasVenta(
                desde, hasta,
                notas.size(),
                cantidad(porEstado, EstadoNotaVenta.CONFIRMADA),
                cantidad(porEstado, EstadoNotaVenta.EN_PREPARACION),
                pendientesEntrega,
                cantidad(porEstado, EstadoNotaVenta.ENTREGADA),
                cantidad(porEstado, EstadoNotaVenta.FACTURADA),
                cantidad(porEstado, EstadoNotaVenta.CANCELADA),
                montoVendido,
                conteos);
    }

    private int cantidad(Map<EstadoNotaVenta, List<NotaVenta>> porEstado, EstadoNotaVenta estado) {
        return porEstado.getOrDefault(estado, List.of()).size();
    }

    private BigDecimal monto(Map<EstadoNotaVenta, List<NotaVenta>> porEstado, EstadoNotaVenta estado) {
        return porEstado.getOrDefault(estado, List.of()).stream()
                .map(NotaVenta::getMontoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // El texto puede ser el número de la nota de venta (NV-000045, "45") o el
    // nombre/RUT del cliente. Los clientes se resuelven a ids en una consulta
    // aparte para que el filtro siga corriendo en la base y la paginación no
    // tenga que traer todo a memoria.
    private Specification<NotaVenta> filtroBusqueda(Long tenantId, String q) {
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
        // Desempate por folio: dos notas del mismo día deben salir siempre en el
        // mismo orden entre páginas.
        return Sort.by(dir, campo).and(Sort.by(dir, "folio"));
    }

    // --- Helpers de escritura ------------------------------------------------

    private NotaVenta nuevaDesde(NotaVentaRequest request, Long tenantId, Long usuarioId,
                                  OrigenNotaVenta origen, Long cotizacionId) {
        Cliente cliente = validarCliente(request.clienteId(), tenantId);
        Long formaPagoId = validarFormaPago(request.formaPagoId(), tenantId);

        NotaVenta nota = NotaVenta.builder()
                .tenantId(tenantId)
                .folio(folioService.siguienteFolio(tenantId))
                .clienteId(cliente.getId())
                .vendedorId(usuarioId)
                .formaPagoId(formaPagoId)
                .origen(origen)
                .cotizacionId(cotizacionId)
                .estado(EstadoNotaVenta.BORRADOR)
                .exenta(request.exenta())
                .moneda(monedaDe(request))
                .fechaEmision(request.fechaEmision())
                .fechaEntregaEstimada(request.fechaEntregaEstimada())
                .direccionEntrega(request.direccionEntrega())
                .condicionesVenta(request.condicionesVenta())
                .observaciones(request.observaciones())
                .build();
        aplicarLineasYMontos(nota, request, tenantId);
        return nota;
    }

    // Calcula líneas y totales. Los precios de línea son netos: el IVA se suma
    // por encima, salvo que la nota sea exenta. Se delega en
    // CalculadoraMontosVenta (modo FACTURA = "la suma de detalle es neta") para
    // no duplicar la lógica de IVA ni romper el invariante neto + iva == total.
    private void aplicarLineasYMontos(NotaVenta nota, NotaVentaRequest request, Long tenantId) {
        List<NotaVentaDetalle> lineas = new ArrayList<>();
        BigDecimal subtotalBrutoTotal = BigDecimal.ZERO;
        BigDecimal descuentoLineas = BigDecimal.ZERO;
        BigDecimal sumaDetalle = BigDecimal.ZERO;

        for (NotaVentaRequest.Item item : request.items()) {
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

            lineas.add(NotaVentaDetalle.builder()
                    .notaVenta(nota)
                    .productoId(producto.getId())
                    .codigo(producto.getSku())
                    .descripcion(producto.getNombre())
                    .cantidad(item.cantidad())
                    .cantidadEntregada(BigDecimal.ZERO)
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

        nota.getDetalle().addAll(lineas);
        aplicarMontos(nota, request.exenta(), subtotalBrutoTotal, descuentoLineas, descuentoGlobal, sumaDetalle);
    }

    private void aplicarMontos(NotaVenta nota, boolean exenta, BigDecimal subtotalBrutoTotal,
                               BigDecimal descuentoLineas, BigDecimal descuentoGlobal, BigDecimal sumaDetalle) {
        CalculadoraMontosVenta.Montos montos = CalculadoraMontosVenta.calcular(
                TipoDocumentoVenta.FACTURA, exenta, sumaDetalle.subtract(descuentoGlobal));
        nota.setDescuento(descuentoGlobal);
        nota.setMontoSubtotal(subtotalBrutoTotal);
        nota.setMontoDescuento(descuentoLineas.add(descuentoGlobal));
        nota.setMontoNeto(montos.neto());
        nota.setMontoIva(montos.iva());
        nota.setMontoTotal(montos.total());
    }

    private String monedaDe(NotaVentaRequest request) {
        return request.moneda() != null && !request.moneda().isBlank() ? request.moneda().trim().toUpperCase() : "CLP";
    }

    private void registrarEvento(NotaVenta nota, AccionNotaVenta accion, EstadoNotaVenta anterior,
                                  EstadoNotaVenta nuevo, String detalle, Long usuarioId) {
        eventoRepository.save(NotaVentaEvento.builder()
                .tenantId(nota.getTenantId())
                .notaVentaId(nota.getId())
                .usuarioId(usuarioId)
                .accion(accion)
                .estadoAnterior(anterior)
                .estadoNuevo(nuevo)
                .detalle(detalle)
                .build());
    }

    private void exigirEstado(NotaVenta nota, String accion, EstadoNotaVenta... permitidos) {
        for (EstadoNotaVenta permitido : permitidos) {
            if (nota.getEstado() == permitido) {
                return;
            }
        }
        throw new IllegalArgumentException(
                "No se puede " + accion + " una nota de venta en estado " + nota.getEstado());
    }

    private NotaVenta buscarEntidad(Long id, Long tenantId) {
        return notaVentaRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Nota de venta no encontrada: " + id));
    }

    // Deriva el estado de entrega comparando lo solicitado con lo entregado.
    private EstadoNotaVenta derivarEstadoEntrega(List<NotaVentaDetalle> lineas) {
        boolean todoEntregado = !lineas.isEmpty() && lineas.stream()
                .allMatch(l -> l.getCantidadEntregada().compareTo(l.getCantidad()) >= 0);
        if (todoEntregado) {
            return EstadoNotaVenta.ENTREGADA;
        }
        boolean parcial = lineas.stream().anyMatch(l -> l.getCantidadEntregada().signum() > 0);
        return parcial ? EstadoNotaVenta.PARCIALMENTE_ENTREGADA : EstadoNotaVenta.EN_PREPARACION;
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

    // --- Helpers de lectura --------------------------------------------------

    private NotaVentaCompleta detalleDe(NotaVenta nota) {
        Long tenantId = nota.getTenantId();
        Cliente cliente = clienteRepository.findById(nota.getClienteId()).orElse(null);
        Usuario vendedor = usuarioRepository.findById(nota.getVendedorId()).orElse(null);
        String formaPagoNombre = nota.getFormaPagoId() == null ? null
                : formaPagoRepository.findById(nota.getFormaPagoId()).map(FormaPago::getNombre).orElse(null);

        String cotizacionNumero = null;
        if (nota.getCotizacionId() != null) {
            cotizacionNumero = cotizacionRepository.findById(nota.getCotizacionId())
                    .map(c -> NumeroCotizacion.formatear(c.getFolio()))
                    .orElse(null);
        }

        List<LineaNotaVenta> lineas = nota.getDetalle().stream()
                .map(l -> new LineaNotaVenta(l.getId(), l.getProductoId(), l.getCodigo(), l.getDescripcion(),
                        l.getCantidad(), l.getCantidadEntregada(),
                        l.getPrecioUnitario(), l.getDescuento(), l.getSubtotal()))
                .toList();

        List<NotaVentaEvento> eventos = eventoRepository
                .findByTenantIdAndNotaVentaIdOrderByFechaAscIdAsc(tenantId, nota.getId());
        Map<Long, String> nombresUsuario = nombresDeUsuarios(eventos);
        List<EventoNotaVenta> historial = eventos.stream()
                .map(e -> new EventoNotaVenta(e.getFecha(),
                        e.getUsuarioId() == null ? "Sistema" : nombresUsuario.getOrDefault(e.getUsuarioId(), "—"),
                        e.getAccion(), e.getEstadoAnterior(), e.getEstadoNuevo(), e.getDetalle()))
                .toList();

        List<EntregaNotaVenta> entregas = entregasDe(tenantId, nota, vendedor);
        List<DocumentoRelacionado> documentos = documentosDe(tenantId, nota.getId());

        return new NotaVentaCompleta(
                nota.getId(), nota.getFolio(), NumeroNotaVenta.formatear(nota.getFolio()),
                nota.getEstado(), nota.isExenta(),
                nota.getOrigen(), nota.getCotizacionId(), cotizacionNumero,
                nota.getMoneda(), nota.getFechaEmision(), nota.getFechaEntregaEstimada(),
                nota.getClienteId(),
                cliente != null ? cliente.getNombre() : "—",
                cliente != null ? cliente.getRazonSocial() : null,
                cliente != null ? cliente.getRut() : null,
                cliente != null ? cliente.getDireccion() : null,
                cliente != null ? cliente.getEmail() : null,
                cliente != null ? cliente.getTelefono() : null,
                nota.getVendedorId(), vendedor != null ? vendedor.getNombre() : "—",
                nota.getFormaPagoId(), formaPagoNombre,
                nota.getDireccionEntrega(), nota.getCondicionesVenta(), nota.getObservaciones(), nota.getMotivo(),
                nota.getDescuento(), nota.getMontoSubtotal(), nota.getMontoDescuento(),
                nota.getMontoNeto(), nota.getMontoIva(), nota.getMontoTotal(),
                lineas, historial, entregas, documentos);
    }

    private List<EntregaNotaVenta> entregasDe(Long tenantId, NotaVenta nota, Usuario vendedor) {
        List<NotaVentaEntrega> entregas = entregaRepository
                .findByTenantIdAndNotaVentaIdOrderByFechaAscIdAsc(tenantId, nota.getId());
        if (entregas.isEmpty()) {
            return List.of();
        }
        Map<Long, String> descripciones = nota.getDetalle().stream()
                .collect(Collectors.toMap(NotaVentaDetalle::getProductoId, NotaVentaDetalle::getDescripcion));
        Set<Long> usuarioIds = entregas.stream().map(NotaVentaEntrega::getUsuarioId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> nombres = usuarioIds.isEmpty() ? Map.of()
                : usuarioRepository.findAllById(usuarioIds).stream()
                        .collect(Collectors.toMap(Usuario::getId, Usuario::getNombre));

        return entregas.stream()
                .map(e -> new EntregaNotaVenta(
                        e.getId(), e.getFecha(),
                        e.getUsuarioId() == null ? "Sistema" : nombres.getOrDefault(e.getUsuarioId(), "—"),
                        e.getObservacion(),
                        e.getLineas().stream()
                                .map(l -> new EntregaLineaNotaVenta(l.getProductoId(),
                                        descripciones.getOrDefault(l.getProductoId(), "—"), l.getCantidad()))
                                .toList()))
                .toList();
    }

    private List<DocumentoRelacionado> documentosDe(Long tenantId, Long notaId) {
        return documentoRepository.findByTenantIdAndNotaVentaIdOrderByFechaAsc(tenantId, notaId).stream()
                .map(d -> new DocumentoRelacionado(d.getTipoDocumento(), d.getDocumentoId(), d.getNumero(),
                        d.getFecha()))
                .toList();
    }

    private NotaVentaResumen resumenDe(NotaVenta nota, Cliente cliente, Usuario vendedor) {
        return new NotaVentaResumen(
                nota.getId(), nota.getFolio(), NumeroNotaVenta.formatear(nota.getFolio()),
                nota.getEstado(), nota.getOrigen(), nota.getFechaEmision(), nota.getFechaEntregaEstimada(),
                cliente != null ? cliente.getNombre() : "—",
                cliente != null ? cliente.getRut() : null,
                vendedor != null ? vendedor.getNombre() : "—",
                nota.getMontoTotal());
    }

    private Map<Long, Cliente> clientesDe(Long tenantId, List<NotaVenta> notas) {
        List<Long> ids = notas.stream().map(NotaVenta::getClienteId).distinct().toList();
        return ids.isEmpty() ? Map.of()
                : clienteRepository.findByTenantIdAndIdIn(tenantId, ids).stream()
                        .collect(Collectors.toMap(Cliente::getId, Function.identity()));
    }

    private Map<Long, Usuario> vendedoresDe(Long tenantId, List<NotaVenta> notas) {
        List<Long> ids = notas.stream().map(NotaVenta::getVendedorId).distinct().toList();
        return ids.isEmpty() ? Map.of()
                : usuarioRepository.findAllById(ids).stream()
                        .filter(u -> u.getTenantId().equals(tenantId))
                        .collect(Collectors.toMap(Usuario::getId, Function.identity()));
    }

    private Map<Long, String> nombresDeUsuarios(List<NotaVentaEvento> eventos) {
        List<Long> ids = eventos.stream().map(NotaVentaEvento::getUsuarioId).filter(java.util.Objects::nonNull)
                .distinct().toList();
        return ids.isEmpty() ? Map.of()
                : usuarioRepository.findAllById(ids).stream()
                        .collect(Collectors.toMap(Usuario::getId, Usuario::getNombre));
    }
}