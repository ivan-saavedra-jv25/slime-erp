package cl.slimerp.notasventa;

import cl.slimerp.catalogo.Cliente;
import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.catalogo.FormaPagoRepository;
import cl.slimerp.catalogo.Producto;
import cl.slimerp.catalogo.ProductoRepository;
import cl.slimerp.common.UsuarioActualService;
import cl.slimerp.config.TenantContext;
import cl.slimerp.cotizaciones.Cotizacion;
import cl.slimerp.cotizaciones.CotizacionDetalle;
import cl.slimerp.cotizaciones.CotizacionDocumentoRepository;
import cl.slimerp.cotizaciones.CotizacionRepository;
import cl.slimerp.cotizaciones.EstadoCotizacion;
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotaVentaServiceTest {

    private NotaVentaRepository notaVentaRepository;
    private NotaVentaEventoRepository eventoRepository;
    private NotaVentaDocumentoRepository documentoRepository;
    private NotaVentaEntregaRepository entregaRepository;
    private NotaVentaFolioService folioService;
    private ClienteRepository clienteRepository;
    private ProductoRepository productoRepository;
    private FormaPagoRepository formaPagoRepository;
    private UsuarioRepository usuarioRepository;
    private CotizacionRepository cotizacionRepository;
    private CotizacionDocumentoRepository cotizacionDocumentoRepository;
    private UsuarioActualService usuarioActualService;
    private NotaVentaService service;

    private final Long tenantId = 1L;
    private final Long usuarioId = 7L;
    private final LocalDate hoy = LocalDate.of(2026, 9, 22);

    private final Cliente cliente = Cliente.builder().id(5L).tenantId(1L).nombre("Empresa ABC SpA")
            .razonSocial("Empresa ABC SpA").rut("76.111.222-3").direccion("Av. Siempre Viva 123")
            .email("ventas@abc.cl").telefono("+56911122233").activo(true).build();
    private final Producto producto = Producto.builder().id(10L).tenantId(1L).sku("SKU001").nombre("Producto X")
            .precioVenta(new BigDecimal("1000")).activo(true).build();
    private final Usuario vendedor = Usuario.builder().id(usuarioId).tenantId(1L).nombre("Vendedor Demo").build();

    private final List<NotaVentaEvento> eventosGuardados = new ArrayList<>();

    @BeforeEach
    void setUp() {
        notaVentaRepository = mock(NotaVentaRepository.class);
        eventoRepository = mock(NotaVentaEventoRepository.class);
        documentoRepository = mock(NotaVentaDocumentoRepository.class);
        entregaRepository = mock(NotaVentaEntregaRepository.class);
        folioService = mock(NotaVentaFolioService.class);
        clienteRepository = mock(ClienteRepository.class);
        productoRepository = mock(ProductoRepository.class);
        formaPagoRepository = mock(FormaPagoRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        cotizacionRepository = mock(CotizacionRepository.class);
        cotizacionDocumentoRepository = mock(CotizacionDocumentoRepository.class);
        usuarioActualService = mock(UsuarioActualService.class);
        service = new NotaVentaService(notaVentaRepository, eventoRepository, documentoRepository, entregaRepository,
                folioService, clienteRepository, productoRepository, formaPagoRepository, usuarioRepository,
                cotizacionRepository, cotizacionDocumentoRepository, usuarioActualService);

        TenantContext.setTenantId(tenantId);
        eventosGuardados.clear();

        when(usuarioActualService.idUsuarioActual(tenantId)).thenReturn(usuarioId);
        when(folioService.siguienteFolio(tenantId)).thenReturn(1);
        when(clienteRepository.findByIdAndTenantIdAndActivoTrue(5L, tenantId)).thenReturn(Optional.of(cliente));
        when(clienteRepository.findById(5L)).thenReturn(Optional.of(cliente));
        when(productoRepository.findByIdAndTenantIdAndActivoTrue(10L, tenantId)).thenReturn(Optional.of(producto));
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(vendedor));
        when(usuarioRepository.findAllById(any())).thenReturn(List.of(vendedor));
        when(eventoRepository.findByTenantIdAndNotaVentaIdOrderByFechaAscIdAsc(anyLong(), any()))
                .thenReturn(List.of());
        when(entregaRepository.findByTenantIdAndNotaVentaIdOrderByFechaAscIdAsc(anyLong(), any()))
                .thenReturn(List.of());
        when(documentoRepository.findByTenantIdAndNotaVentaIdOrderByFechaAsc(anyLong(), any()))
                .thenReturn(List.of());
        when(clienteRepository.idsPorBusqueda(anyLong(), any())).thenReturn(List.of());
        when(eventoRepository.save(any(NotaVentaEvento.class))).thenAnswer(inv -> {
            NotaVentaEvento evento = inv.getArgument(0);
            eventosGuardados.add(evento);
            return evento;
        });
        when(notaVentaRepository.save(any(NotaVenta.class))).thenAnswer(inv -> {
            NotaVenta n = inv.getArgument(0);
            if (n.getId() == null) n.setId(100L);
            return n;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private NotaVentaRequest request(BigDecimal precio, BigDecimal cantidad, BigDecimal descuentoLinea,
                                     BigDecimal descuentoGlobal, boolean exenta) {
        return new NotaVentaRequest(5L, null, hoy, hoy.plusDays(5), "Av. Siempre Viva 123", "30 días", "Observación",
                exenta, "CLP", descuentoGlobal,
                List.of(new NotaVentaRequest.Item(10L, cantidad, precio, descuentoLinea)));
    }

    private NotaVentaRequest requestSimple() {
        return request(new BigDecimal("1000"), new BigDecimal("2"), null, null, false);
    }

    private NotaVenta notaEnEstado(EstadoNotaVenta estado) {
        NotaVenta nota = NotaVenta.builder()
                .id(100L).tenantId(tenantId).folio(1).clienteId(5L).vendedorId(usuarioId)
                .estado(estado).fechaEmision(hoy)
                .montoSubtotal(new BigDecimal("2000")).montoNeto(new BigDecimal("2000"))
                .montoIva(new BigDecimal("380")).montoTotal(new BigDecimal("2380"))
                .build();
        nota.setDetalle(new ArrayList<>(List.of(NotaVentaDetalle.builder()
                .id(1L).notaVenta(nota).productoId(10L).codigo("SKU001").descripcion("Producto X")
                .cantidad(new BigDecimal("2")).cantidadEntregada(BigDecimal.ZERO)
                .precioUnitario(new BigDecimal("1000")).descuento(BigDecimal.ZERO)
                .subtotal(new BigDecimal("2000")).build())));
        when(notaVentaRepository.findByIdAndTenantId(100L, tenantId)).thenReturn(Optional.of(nota));
        return nota;
    }

    @Test
    void crearDejaLaNotaEnBorradorConFolioVendedorYMontosCalculados() {
        var creada = service.crear(requestSimple());

        assertEquals(EstadoNotaVenta.BORRADOR, creada.estado());
        assertEquals(1, creada.folio());
        assertEquals("NV-000001", creada.numero());
        assertEquals(usuarioId, creada.vendedorId());
        assertEquals(new BigDecimal("2000"), creada.montoSubtotal());
        assertEquals(BigDecimal.ZERO, creada.montoDescuento());
        assertEquals(new BigDecimal("2000.00"), creada.montoNeto());
        assertEquals(new BigDecimal("380.00"), creada.montoIva());
        assertEquals(new BigDecimal("2380.00"), creada.montoTotal());
    }

    @Test
    void unaNotaExentaNoLlevaIva() {
        var creada = service.crear(request(new BigDecimal("1000"), new BigDecimal("2"), null, null, true));

        assertTrue(creada.exenta());
        assertEquals(new BigDecimal("2000.00"), creada.montoNeto());
        assertEquals(new BigDecimal("0.00"), creada.montoIva());
        assertEquals(new BigDecimal("2000.00"), creada.montoTotal());
    }

    @Test
    void losDescuentosDeLineaYGlobalSeRestanAntesDeCalcularElIva() {
        var creada = service.crear(request(new BigDecimal("1000"), new BigDecimal("2"),
                new BigDecimal("200"), new BigDecimal("300"), false));

        assertEquals(new BigDecimal("2000"), creada.montoSubtotal());
        assertEquals(new BigDecimal("500"), creada.montoDescuento());
        assertEquals(new BigDecimal("1500.00"), creada.montoNeto());
        assertEquals(new BigDecimal("285.00"), creada.montoIva());
        assertEquals(new BigDecimal("1785.00"), creada.montoTotal());
    }

    @Test
    void guardaElCodigoYLaDescripcionDelProductoComoSnapshot() {
        var creada = service.crear(requestSimple());

        assertEquals(1, creada.lineas().size());
        assertEquals("SKU001", creada.lineas().get(0).codigo());
        assertEquals("Producto X", creada.lineas().get(0).descripcion());
        assertEquals(BigDecimal.ZERO, creada.lineas().get(0).cantidadEntregada());
    }

    @Test
    void registraElEventoCreadaAlCrear() {
        service.crear(requestSimple());

        assertEquals(1, eventosGuardados.size());
        assertEquals(AccionNotaVenta.CREADA, eventosGuardados.get(0).getAccion());
        assertEquals(EstadoNotaVenta.BORRADOR, eventosGuardados.get(0).getEstadoNuevo());
        assertEquals(usuarioId, eventosGuardados.get(0).getUsuarioId());
    }

    @Test
    void rechazaCantidadCeroONegativa() {
        var req = request(new BigDecimal("1000"), BigDecimal.ZERO, null, null, false);

        var error = assertThrows(IllegalArgumentException.class, () -> service.crear(req));
        assertTrue(error.getMessage().contains("cantidad"));
    }

    @Test
    void rechazaUnDescuentoDeLineaMayorQueElSubtotalDeLaLinea() {
        var req = request(new BigDecimal("1000"), BigDecimal.ONE, new BigDecimal("1500"), null, false);

        assertThrows(IllegalArgumentException.class, () -> service.crear(req));
    }

    @Test
    void rechazaUnDescuentoGlobalMayorQueElTotalDeLasLineas() {
        var req = request(new BigDecimal("1000"), BigDecimal.ONE, null, new BigDecimal("1500"), false);

        assertThrows(IllegalArgumentException.class, () -> service.crear(req));
    }

    @Test
    void rechazaUnClienteInexistente() {
        when(clienteRepository.findByIdAndTenantIdAndActivoTrue(99L, tenantId)).thenReturn(Optional.empty());
        var req = new NotaVentaRequest(99L, null, hoy, null, null, null, null, false, "CLP", null,
                List.of(new NotaVentaRequest.Item(10L, BigDecimal.ONE, new BigDecimal("1000"), null)));

        assertThrows(IllegalArgumentException.class, () -> service.crear(req));
    }

    @Test
    void rechazaUnProductoInexistente() {
        when(productoRepository.findByIdAndTenantIdAndActivoTrue(99L, tenantId)).thenReturn(Optional.empty());
        var req = new NotaVentaRequest(5L, null, hoy, null, null, null, null, false, "CLP", null,
                List.of(new NotaVentaRequest.Item(99L, BigDecimal.ONE, new BigDecimal("1000"), null)));

        assertThrows(IllegalArgumentException.class, () -> service.crear(req));
    }

    @Test
    void confirmarMueveDeBorradorAConfirmada() {
        notaEnEstado(EstadoNotaVenta.BORRADOR);

        var confirmada = service.confirmar(100L);

        assertEquals(EstadoNotaVenta.CONFIRMADA, confirmada.estado());
        assertEquals(AccionNotaVenta.CONFIRMADA, eventosGuardados.get(0).getAccion());
        assertEquals(EstadoNotaVenta.BORRADOR, eventosGuardados.get(0).getEstadoAnterior());
    }

    @Test
    void noSePuedeConfirmarUnaNotaSinProductos() {
        NotaVenta nota = notaEnEstado(EstadoNotaVenta.BORRADOR);
        nota.setDetalle(new ArrayList<>());

        assertThrows(IllegalArgumentException.class, () -> service.confirmar(100L));
        verify(notaVentaRepository, never()).save(nota);
    }

    @Test
    void noSePuedeConfirmarUnaNotaQueNoEsteEnBorrador() {
        notaEnEstado(EstadoNotaVenta.CONFIRMADA);

        var error = assertThrows(IllegalArgumentException.class, () -> service.confirmar(100L));
        assertTrue(error.getMessage().contains("CONFIRMADA"));
    }

    @Test
    void prepararSoloEsPosibleDesdeConfirmada() {
        notaEnEstado(EstadoNotaVenta.CONFIRMADA);
        assertEquals(EstadoNotaVenta.EN_PREPARACION, service.preparar(100L).estado());

        notaEnEstado(EstadoNotaVenta.BORRADOR);
        assertThrows(IllegalArgumentException.class, () -> service.preparar(100L));
    }

    @Test
    void registrarEntregaParcialDerivaParcialmenteEntregada() {
        notaEnEstado(EstadoNotaVenta.EN_PREPARACION);
        var request = new EntregaRequest("Primer despacho",
                List.of(new EntregaRequest.Linea(10L, BigDecimal.ONE)));

        var resultado = service.registrarEntrega(100L, request);

        assertEquals(EstadoNotaVenta.PARCIALMENTE_ENTREGADA, resultado.estado());
        assertEquals(new BigDecimal("1"), resultado.lineas().get(0).cantidadEntregada());
        assertEquals(AccionNotaVenta.ENTREGA_REGISTRADA, eventosGuardados.get(0).getAccion());
    }

    @Test
    void registrarEntregaCompletaDerivaEntregada() {
        notaEnEstado(EstadoNotaVenta.EN_PREPARACION);
        var request = new EntregaRequest("Despacho total",
                List.of(new EntregaRequest.Linea(10L, new BigDecimal("2"))));

        var resultado = service.registrarEntrega(100L, request);

        assertEquals(EstadoNotaVenta.ENTREGADA, resultado.estado());
        assertEquals(new BigDecimal("2"), resultado.lineas().get(0).cantidadEntregada());
        assertEquals(AccionNotaVenta.ENTREGADA, eventosGuardados.get(0).getAccion());
    }

    @Test
    void registrarEntregaPuedeAcumularAvances() {
        notaEnEstado(EstadoNotaVenta.EN_PREPARACION);
        service.registrarEntrega(100L, new EntregaRequest("Parcial 1",
                List.of(new EntregaRequest.Linea(10L, BigDecimal.ONE))));

        var completo = service.registrarEntrega(100L, new EntregaRequest("Parcial 2",
                List.of(new EntregaRequest.Linea(10L, BigDecimal.ONE))));

        assertEquals(EstadoNotaVenta.ENTREGADA, completo.estado());
        assertEquals(AccionNotaVenta.ENTREGADA, eventosGuardados.get(1).getAccion());
    }

    @Test
    void rechazaUnaEntregaQueSupereLaCantidadSolicitada() {
        notaEnEstado(EstadoNotaVenta.EN_PREPARACION);
        var request = new EntregaRequest("Extralimitada",
                List.of(new EntregaRequest.Linea(10L, new BigDecimal("3"))));

        var error = assertThrows(IllegalArgumentException.class, () -> service.registrarEntrega(100L, request));
        assertTrue(error.getMessage().contains("supera la cantidad"));
    }

    @Test
    void rechazaUnaEntregaDeUnProductoQueNoEstaEnLaNota() {
        notaEnEstado(EstadoNotaVenta.EN_PREPARACION);
        var request = new EntregaRequest("Producto extra",
                List.of(new EntregaRequest.Linea(99L, BigDecimal.ONE)));

        assertThrows(IllegalArgumentException.class, () -> service.registrarEntrega(100L, request));
    }

    @Test
    void noSePuedeRegistrarUnaEntregaEnUnaNotaEntregada() {
        notaEnEstado(EstadoNotaVenta.ENTREGADA);

        assertThrows(IllegalArgumentException.class, () -> service.registrarEntrega(100L,
                new EntregaRequest("X", List.of(new EntregaRequest.Linea(10L, BigDecimal.ONE)))));
    }

    @Test
    void cancelarEsPosibleDesdeBorradorYConfirmadaPeroNoDesdePreparacion() {
        notaEnEstado(EstadoNotaVenta.BORRADOR);
        assertEquals(EstadoNotaVenta.CANCELADA, service.cancelar(100L, null).estado());

        notaEnEstado(EstadoNotaVenta.CONFIRMADA);
        var cancelada = service.cancelar(100L, "Cliente desistió");
        assertEquals(EstadoNotaVenta.CANCELADA, cancelada.estado());
        assertEquals("Cliente desistió", cancelada.motivo());

        notaEnEstado(EstadoNotaVenta.EN_PREPARACION);
        assertThrows(IllegalArgumentException.class, () -> service.cancelar(100L, null));
    }

    @Test
    void soloSePuedeEliminarUnBorrador() {
        notaEnEstado(EstadoNotaVenta.BORRADOR);
        service.eliminar(100L);
        verify(notaVentaRepository).delete(any(NotaVenta.class));

        notaEnEstado(EstadoNotaVenta.CONFIRMADA);
        assertThrows(IllegalArgumentException.class, () -> service.eliminar(100L));
        verify(notaVentaRepository, times(1)).delete(any(NotaVenta.class));
    }

    @Test
    void editarSoloEsPosibleEnBorrador() {
        notaEnEstado(EstadoNotaVenta.CONFIRMADA);

        assertThrows(IllegalArgumentException.class, () -> service.actualizar(100L, requestSimple()));
    }

    @Test
    void editarUnBorradorReemplazaLasLineasYRecalculaLosMontos() {
        notaEnEstado(EstadoNotaVenta.BORRADOR);

        var editada = service.actualizar(100L, request(new BigDecimal("500"), new BigDecimal("4"), null, null, false));

        assertEquals(1, editada.lineas().size());
        assertEquals(new BigDecimal("2000.00"), editada.montoNeto());
        assertEquals(AccionNotaVenta.EDITADA, eventosGuardados.get(0).getAccion());
    }

    @Test
    void duplicarCreaUnBorradorComoVentaDirectaConFolioNuevo() {
        notaEnEstado(EstadoNotaVenta.CONFIRMADA);
        when(folioService.siguienteFolio(tenantId)).thenReturn(2);
        when(notaVentaRepository.save(any(NotaVenta.class))).thenAnswer(inv -> {
            NotaVenta n = inv.getArgument(0);
            if (n.getId() == null) n.setId(101L);
            return n;
        });

        var copia = service.duplicar(100L);

        assertEquals(EstadoNotaVenta.BORRADOR, copia.estado());
        assertEquals(OrigenNotaVenta.VENTA_DIRECTA, copia.origen());
        assertEquals(2, copia.folio());
        assertEquals("NV-000002", copia.numero());
        assertEquals(1, copia.lineas().size());
        assertEquals(2, eventosGuardados.size());
        assertEquals(AccionNotaVenta.DUPLICADA, eventosGuardados.get(0).getAccion());
        assertEquals(AccionNotaVenta.CREADA, eventosGuardados.get(1).getAccion());
        assertTrue(eventosGuardados.get(1).getDetalle().contains("NV-000001"));
    }

    @Test
    void crearDesdeCotizacionSoloEsPosibleDesdeUnaAceptada() {
        Cotizacion cotizacion = cotizacionEnEstado(EstadoCotizacion.ACEPTADA);
        cotizacion.setDetalle(List.of(lineaCotizacion(cotizacion)));

        var creada = service.crearDesdeCotizacion(200L);

        assertEquals(EstadoNotaVenta.BORRADOR, creada.estado());
        assertEquals(OrigenNotaVenta.COTIZACION, creada.origen());
        assertEquals("COT-000001", creada.cotizacionNumero());
        assertEquals(1, creada.lineas().size());
        assertEquals(new BigDecimal("2000.00"), creada.montoNeto());
        verify(cotizacionDocumentoRepository).save(any());
    }

    @Test
    void crearDesdeCotizacionRechazaUnaQueNoEsteAceptada() {
        cotizacionEnEstado(EstadoCotizacion.ENVIADA);

        var error = assertThrows(IllegalArgumentException.class, () -> service.crearDesdeCotizacion(200L));
        assertTrue(error.getMessage().contains("ACEPTADA"));
    }

    @Test
    void crearDesdeCotizacionRechazaUnaYaConvertidaParaNoDuplicarElVinculo() {
        cotizacionEnEstado(EstadoCotizacion.ACEPTADA);
        when(notaVentaRepository.findByTenantIdAndCotizacionId(tenantId, 200L))
                .thenReturn(Optional.of(NotaVenta.builder().id(55L).build()));

        assertThrows(IllegalArgumentException.class, () -> service.crearDesdeCotizacion(200L));
    }

    private Cotizacion cotizacionEnEstado(EstadoCotizacion estado) {
        Cotizacion cotizacion = Cotizacion.builder()
                .id(200L).tenantId(tenantId).folio(1).clienteId(5L).vendedorId(usuarioId)
                .estado(estado).fechaEmision(hoy).fechaVencimiento(hoy.plusDays(30))
                .exenta(false).condicionesComerciales("30 días")
                .build();
        when(cotizacionRepository.findByIdAndTenantId(200L, tenantId)).thenReturn(Optional.of(cotizacion));
        when(cotizacionRepository.findById(200L)).thenReturn(Optional.of(cotizacion));
        return cotizacion;
    }

    private CotizacionDetalle lineaCotizacion(Cotizacion cotizacion) {
        return CotizacionDetalle.builder()
                .id(1L).cotizacion(cotizacion).productoId(10L).codigo("SKU001").descripcion("Producto X")
                .cantidad(new BigDecimal("2")).precioUnitario(new BigDecimal("1000"))
                .descuento(BigDecimal.ZERO).subtotal(new BigDecimal("2000"))
                .build();
    }

    @Test
    void elDetalleTraeLosDocumentosRelacionadosDeLaCadenaComercial() {
        notaEnEstado(EstadoNotaVenta.CONFIRMADA);
        when(documentoRepository.findByTenantIdAndNotaVentaIdOrderByFechaAsc(tenantId, 100L))
                .thenReturn(List.of(NotaVentaDocumento.builder()
                        .id(1L).tenantId(tenantId).notaVentaId(100L)
                        .tipoDocumento("GUIA").documentoId(77L).numero("GUI-000001")
                        .build()));

        var detalle = service.obtener(100L);

        assertEquals(1, detalle.documentosRelacionados().size());
        assertEquals("GUIA", detalle.documentosRelacionados().get(0).tipoDocumento());
        assertEquals("GUI-000001", detalle.documentosRelacionados().get(0).numero());
    }

    @Test
    void elDetalleTraeLasEntregasRegistradas() {
        NotaVenta nota = notaEnEstado(EstadoNotaVenta.PARCIALMENTE_ENTREGADA);
        nota.getDetalle().get(0).setCantidadEntregada(BigDecimal.ONE);
        when(entregaRepository.findByTenantIdAndNotaVentaIdOrderByFechaAscIdAsc(tenantId, 100L))
                .thenReturn(List.of(NotaVentaEntrega.builder()
                        .id(1L).tenantId(tenantId).notaVentaId(100L).observacion("Primer despacho")
                        .usuarioId(usuarioId)
                        .lineas(new ArrayList<>(List.of(NotaVentaEntregaLinea.builder()
                                .id(1L).productoId(10L).cantidad(BigDecimal.ONE).build())))
                        .build()));

        var detalle = service.obtener(100L);

        assertEquals(1, detalle.entregas().size());
        assertEquals("Primer despacho", detalle.entregas().get(0).observacion());
        assertEquals("Producto X", detalle.entregas().get(0).lineas().get(0).descripcion());
    }

    @Test
    void elDashboardResumeCantidadesMontosYEstados() {
        cuandoDashboard(List.of(
                conEstadoYTotal(1L, EstadoNotaVenta.BORRADOR, "1000"),
                conEstadoYTotal(2L, EstadoNotaVenta.CONFIRMADA, "2000"),
                conEstadoYTotal(3L, EstadoNotaVenta.EN_PREPARACION, "3000"),
                conEstadoYTotal(4L, EstadoNotaVenta.PARCIALMENTE_ENTREGADA, "4000"),
                conEstadoYTotal(5L, EstadoNotaVenta.ENTREGADA, "5000"),
                conEstadoYTotal(6L, EstadoNotaVenta.FACTURADA, "6000"),
                conEstadoYTotal(7L, EstadoNotaVenta.CANCELADA, "7000")));

        var dashboard = service.dashboard(hoy, hoy.plusDays(30));

        assertEquals(7, dashboard.cantidad());
        assertEquals(1, dashboard.confirmadas());
        assertEquals(1, dashboard.enPreparacion());
        assertEquals(3, dashboard.pendientesEntrega());
        assertEquals(1, dashboard.entregadas());
        assertEquals(1, dashboard.facturadas());
        assertEquals(1, dashboard.canceladas());
        assertEquals(new BigDecimal("20000"), dashboard.montoTotalVendido());
        assertEquals(EstadoNotaVenta.values().length, dashboard.porEstado().size());
    }

    @Test
    void elDashboardDevuelveTodosLosEstadosParaElGraficoAunqueEstenEnCero() {
        cuandoDashboard(List.of(conEstadoYTotal(1L, EstadoNotaVenta.CONFIRMADA, "2000")));

        var dashboard = service.dashboard(hoy, hoy.plusDays(30));

        var borrador = dashboard.porEstado().stream()
                .filter(c -> c.estado() == EstadoNotaVenta.BORRADOR).findFirst().orElseThrow();
        assertEquals(0, borrador.cantidad());
        assertEquals(BigDecimal.ZERO, borrador.monto());
    }

    @Test
    void unPeriodoSinNotasDevuelveTodoEnCero() {
        cuandoDashboard(List.of());

        var dashboard = service.dashboard(hoy, hoy.plusDays(30));

        assertEquals(0, dashboard.cantidad());
        assertEquals(0, dashboard.pendientesEntrega());
        assertEquals(BigDecimal.ZERO, dashboard.montoTotalVendido());
    }

    @Test
    void elDashboardRechazaUnRangoDeFechasInvertido() {
        assertThrows(IllegalArgumentException.class, () -> service.dashboard(hoy.plusDays(30), hoy));
        verifyNoInteractions(notaVentaRepository);
    }

    @Test
    void laBusquedaRechazaUnRangoDeFechasInvertido() {
        assertThrows(IllegalArgumentException.class,
                () -> service.buscar(null, null, null, hoy.plusDays(30), hoy, null, null, null, null, 0, 10));
    }

    @Test
    void laBusquedaRechazaPaginasNegativasYTamanosInvalidos() {
        assertThrows(IllegalArgumentException.class,
                () -> service.buscar(null, null, null, null, null, null, null, null, null, -1, 10));
        assertThrows(IllegalArgumentException.class,
                () -> service.buscar(null, null, null, null, null, null, null, null, null, 0, 0));
        verifyNoInteractions(notaVentaRepository);
    }

    private void cuandoDashboard(List<NotaVenta> notas) {
        when(notaVentaRepository.findByTenantIdAndFechaEmisionBetweenOrderByFolioAsc(eq(tenantId), any(), any()))
                .thenReturn(notas);
    }

    private NotaVenta conEstadoYTotal(Long id, EstadoNotaVenta estado, String total) {
        return NotaVenta.builder().id(id).tenantId(tenantId).folio(id.intValue()).clienteId(5L)
                .vendedorId(usuarioId).estado(estado).fechaEmision(hoy)
                .montoTotal(new BigDecimal(total)).build();
    }
}