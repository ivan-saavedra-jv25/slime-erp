package cl.slimerp.cotizaciones;

import cl.slimerp.catalogo.Cliente;
import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.catalogo.FormaPagoRepository;
import cl.slimerp.catalogo.Producto;
import cl.slimerp.catalogo.ProductoRepository;
import cl.slimerp.common.UsuarioActualService;
import cl.slimerp.config.TenantContext;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CotizacionServiceTest {

    private CotizacionRepository cotizacionRepository;
    private CotizacionEventoRepository eventoRepository;
    private CotizacionDocumentoRepository documentoRepository;
    private CotizacionFolioService folioService;
    private ClienteRepository clienteRepository;
    private ProductoRepository productoRepository;
    private FormaPagoRepository formaPagoRepository;
    private UsuarioRepository usuarioRepository;
    private UsuarioActualService usuarioActualService;
    private CotizacionService service;

    private final Long tenantId = 1L;
    private final Long usuarioId = 7L;
    private final LocalDate hoy = LocalDate.of(2026, 9, 22);
    private final LocalDate vence = LocalDate.of(2026, 10, 22);

    private final Cliente cliente = Cliente.builder().id(5L).tenantId(1L).nombre("Empresa ABC SpA")
            .rut("76.111.222-3").activo(true).build();
    private final Producto producto = Producto.builder().id(10L).tenantId(1L).sku("SKU001").nombre("Producto X")
            .precioVenta(new BigDecimal("1000")).activo(true).build();
    private final Usuario vendedor = Usuario.builder().id(usuarioId).tenantId(1L).nombre("Vendedor Demo").build();

    private final List<CotizacionEvento> eventosGuardados = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cotizacionRepository = mock(CotizacionRepository.class);
        eventoRepository = mock(CotizacionEventoRepository.class);
        documentoRepository = mock(CotizacionDocumentoRepository.class);
        folioService = mock(CotizacionFolioService.class);
        clienteRepository = mock(ClienteRepository.class);
        productoRepository = mock(ProductoRepository.class);
        formaPagoRepository = mock(FormaPagoRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        usuarioActualService = mock(UsuarioActualService.class);
        service = new CotizacionService(cotizacionRepository, eventoRepository, documentoRepository, folioService,
                clienteRepository, productoRepository, formaPagoRepository, usuarioRepository, usuarioActualService);

        TenantContext.setTenantId(tenantId);
        eventosGuardados.clear();

        when(usuarioActualService.idUsuarioActual(tenantId)).thenReturn(usuarioId);
        when(folioService.siguienteFolio(tenantId)).thenReturn(1);
        when(clienteRepository.findByIdAndTenantIdAndActivoTrue(5L, tenantId)).thenReturn(Optional.of(cliente));
        when(clienteRepository.findById(5L)).thenReturn(Optional.of(cliente));
        when(productoRepository.findByIdAndTenantIdAndActivoTrue(10L, tenantId)).thenReturn(Optional.of(producto));
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(vendedor));
        when(usuarioRepository.findAllById(any())).thenReturn(List.of(vendedor));
        when(eventoRepository.findByTenantIdAndCotizacionIdOrderByFechaAscIdAsc(anyLong(), any()))
                .thenReturn(List.of());
        when(documentoRepository.findByTenantIdAndCotizacionIdOrderByFechaAsc(anyLong(), any()))
                .thenReturn(List.of());
        when(eventoRepository.save(any(CotizacionEvento.class))).thenAnswer(inv -> {
            CotizacionEvento evento = inv.getArgument(0);
            eventosGuardados.add(evento);
            return evento;
        });
        when(cotizacionRepository.save(any(Cotizacion.class))).thenAnswer(inv -> {
            Cotizacion c = inv.getArgument(0);
            if (c.getId() == null) c.setId(100L);
            return c;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private CotizacionRequest request(BigDecimal precio, BigDecimal cantidad, BigDecimal descuentoLinea,
                                       BigDecimal descuentoGlobal, boolean exenta) {
        return new CotizacionRequest(5L, null, hoy, vence, exenta, descuentoGlobal, "30 días", "Observación",
                List.of(new CotizacionRequest.Item(10L, cantidad, precio, descuentoLinea)));
    }

    private CotizacionRequest requestSimple() {
        return request(new BigDecimal("1000"), new BigDecimal("2"), null, null, false);
    }

    private Cotizacion cotizacionEnEstado(EstadoCotizacion estado) {
        Cotizacion cotizacion = Cotizacion.builder()
                .id(100L).tenantId(tenantId).folio(1).clienteId(5L).vendedorId(usuarioId)
                .estado(estado).fechaEmision(hoy).fechaVencimiento(vence)
                .montoNeto(new BigDecimal("2000")).montoIva(new BigDecimal("380"))
                .montoTotal(new BigDecimal("2380"))
                .build();
        when(cotizacionRepository.findByIdAndTenantId(100L, tenantId)).thenReturn(Optional.of(cotizacion));
        return cotizacion;
    }

    @Test
    void crearDejaLaCotizacionEnBorradorConFolioVendedorYMontosCalculados() {
        var creada = service.crear(requestSimple());

        assertEquals(EstadoCotizacion.BORRADOR, creada.estado());
        assertEquals(1, creada.folio());
        assertEquals("COT-000001", creada.numero());
        assertEquals(usuarioId, creada.vendedorId());
        assertEquals(new BigDecimal("2000.00"), creada.montoNeto());
        assertEquals(new BigDecimal("380.00"), creada.montoIva());
        assertEquals(new BigDecimal("2380.00"), creada.montoTotal());
        assertEquals(new BigDecimal("2000"), creada.montoSubtotal());
        assertEquals(BigDecimal.ZERO, creada.montoDescuento());
    }

    @Test
    void unaCotizacionExentaNoLlevaIva() {
        var creada = service.crear(request(new BigDecimal("1000"), new BigDecimal("2"), null, null, true));

        assertEquals(new BigDecimal("2000.00"), creada.montoNeto());
        assertEquals(new BigDecimal("0.00"), creada.montoIva());
        assertEquals(new BigDecimal("2000.00"), creada.montoTotal());
    }

    @Test
    void losDescuentosDeLineaYGlobalSeRestanAntesDeCalcularElIva() {
        // 2 x 1000 = 2000 bruto, -200 de línea = 1800, -300 global = 1500 neto.
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
    }

    @Test
    void registraElEventoCreadaAlCrear() {
        service.crear(requestSimple());

        assertEquals(1, eventosGuardados.size());
        assertEquals(AccionCotizacion.CREADA, eventosGuardados.get(0).getAccion());
        assertEquals(EstadoCotizacion.BORRADOR, eventosGuardados.get(0).getEstadoNuevo());
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
    void rechazaUnVencimientoAnteriorALaEmision() {
        var req = new CotizacionRequest(5L, null, vence, hoy, false, null, null, null,
                List.of(new CotizacionRequest.Item(10L, BigDecimal.ONE, new BigDecimal("1000"), null)));

        assertThrows(IllegalArgumentException.class, () -> service.crear(req));
    }

    @Test
    void rechazaUnClienteInexistente() {
        when(clienteRepository.findByIdAndTenantIdAndActivoTrue(99L, tenantId)).thenReturn(Optional.empty());
        var req = new CotizacionRequest(99L, null, hoy, vence, false, null, null, null,
                List.of(new CotizacionRequest.Item(10L, BigDecimal.ONE, new BigDecimal("1000"), null)));

        assertThrows(IllegalArgumentException.class, () -> service.crear(req));
    }

    @Test
    void enviarMueveDeBorradorAEnviada() {
        cotizacionEnEstado(EstadoCotizacion.BORRADOR);

        var enviada = service.enviar(100L);

        assertEquals(EstadoCotizacion.ENVIADA, enviada.estado());
        assertEquals(AccionCotizacion.ENVIADA, eventosGuardados.get(0).getAccion());
        assertEquals(EstadoCotizacion.BORRADOR, eventosGuardados.get(0).getEstadoAnterior());
    }

    @Test
    void noSePuedeEnviarUnaCotizacionYaAceptada() {
        cotizacionEnEstado(EstadoCotizacion.ACEPTADA);

        var error = assertThrows(IllegalArgumentException.class, () -> service.enviar(100L));
        assertTrue(error.getMessage().contains("ACEPTADA"));
    }

    @Test
    void aceptarSoloEsPosibleDesdeEnviada() {
        cotizacionEnEstado(EstadoCotizacion.ENVIADA);
        assertEquals(EstadoCotizacion.ACEPTADA, service.aceptar(100L).estado());

        cotizacionEnEstado(EstadoCotizacion.BORRADOR);
        assertThrows(IllegalArgumentException.class, () -> service.aceptar(100L));
    }

    @Test
    void rechazarGuardaElMotivo() {
        cotizacionEnEstado(EstadoCotizacion.ENVIADA);

        var rechazada = service.rechazar(100L, "Precio fuera de presupuesto");

        assertEquals(EstadoCotizacion.RECHAZADA, rechazada.estado());
        assertEquals("Precio fuera de presupuesto", rechazada.motivo());
    }

    @Test
    void cancelarEsPosibleDesdeBorradorYEnviadaPeroNoDesdeAceptada() {
        cotizacionEnEstado(EstadoCotizacion.BORRADOR);
        assertEquals(EstadoCotizacion.CANCELADA, service.cancelar(100L, null).estado());

        cotizacionEnEstado(EstadoCotizacion.ENVIADA);
        assertEquals(EstadoCotizacion.CANCELADA, service.cancelar(100L, "Sin respuesta").estado());

        cotizacionEnEstado(EstadoCotizacion.ACEPTADA);
        assertThrows(IllegalArgumentException.class, () -> service.cancelar(100L, null));
    }

    @Test
    void soloSePuedeEliminarUnBorrador() {
        cotizacionEnEstado(EstadoCotizacion.BORRADOR);
        service.eliminar(100L);
        verify(cotizacionRepository).delete(any(Cotizacion.class));

        cotizacionEnEstado(EstadoCotizacion.ENVIADA);
        assertThrows(IllegalArgumentException.class, () -> service.eliminar(100L));
        verify(cotizacionRepository, times(1)).delete(any(Cotizacion.class));
    }

    @Test
    void duplicarCreaUnBorradorConFolioNuevoYEventosEnAmbas() {
        Cotizacion original = cotizacionEnEstado(EstadoCotizacion.RECHAZADA);
        original.setDetalle(new ArrayList<>(List.of(CotizacionDetalle.builder()
                .id(1L).cotizacion(original).productoId(10L).codigo("SKU001").descripcion("Producto X")
                .cantidad(BigDecimal.ONE).precioUnitario(new BigDecimal("1000"))
                .descuento(BigDecimal.ZERO).subtotal(new BigDecimal("1000")).build())));
        when(folioService.siguienteFolio(tenantId)).thenReturn(2);
        when(cotizacionRepository.save(any(Cotizacion.class))).thenAnswer(inv -> {
            Cotizacion c = inv.getArgument(0);
            if (c.getId() == null) c.setId(101L);
            return c;
        });

        var copia = service.duplicar(100L);

        assertEquals(EstadoCotizacion.BORRADOR, copia.estado());
        assertEquals(2, copia.folio());
        assertEquals(1, copia.lineas().size());
        assertEquals(2, eventosGuardados.size());
        assertEquals(AccionCotizacion.DUPLICADA, eventosGuardados.get(0).getAccion());
        assertEquals(AccionCotizacion.CREADA, eventosGuardados.get(1).getAccion());
        assertTrue(eventosGuardados.get(1).getDetalle().contains("COT-000001"));
    }

    @Test
    void marcarVencidaRegistraElEventoSinUsuario() {
        Cotizacion cotizacion = cotizacionEnEstado(EstadoCotizacion.ENVIADA);

        service.marcarVencida(cotizacion);

        assertEquals(EstadoCotizacion.VENCIDA, cotizacion.getEstado());
        assertEquals(AccionCotizacion.VENCIDA, eventosGuardados.get(0).getAccion());
        assertNull(eventosGuardados.get(0).getUsuarioId());
    }

    @Test
    void editarSoloEsPosibleEnBorrador() {
        cotizacionEnEstado(EstadoCotizacion.ENVIADA);

        assertThrows(IllegalArgumentException.class, () -> service.actualizar(100L, requestSimple()));
    }

    @Test
    void editarUnBorradorReemplazaLasLineasYRecalculaLosMontos() {
        Cotizacion cotizacion = cotizacionEnEstado(EstadoCotizacion.BORRADOR);
        cotizacion.setDetalle(new ArrayList<>());

        var editada = service.actualizar(100L, request(new BigDecimal("500"), new BigDecimal("4"), null, null, false));

        assertEquals(1, editada.lineas().size());
        assertEquals(new BigDecimal("2000.00"), editada.montoNeto());
        assertEquals(AccionCotizacion.EDITADA, eventosGuardados.get(0).getAccion());
    }

    private Cotizacion conEstadoYTotal(Long id, EstadoCotizacion estado, String total) {
        return Cotizacion.builder().id(id).tenantId(tenantId).folio(id.intValue()).clienteId(5L)
                .vendedorId(usuarioId).estado(estado).fechaEmision(hoy).fechaVencimiento(vence)
                .montoTotal(new BigDecimal(total)).build();
    }

    @Test
    void elDetalleTraeLosDocumentosRelacionadosDeLaCadenaComercial() {
        cotizacionEnEstado(EstadoCotizacion.ACEPTADA);
        when(documentoRepository.findByTenantIdAndCotizacionIdOrderByFechaAsc(tenantId, 100L))
                .thenReturn(List.of(CotizacionDocumento.builder()
                        .id(1L).tenantId(tenantId).cotizacionId(100L)
                        .tipoDocumento("NOTA_VENTA").documentoId(55L).numero("NV-000010")
                        .fecha(hoy.atStartOfDay()).build()));

        var detalle = service.obtener(100L);

        assertEquals(1, detalle.documentosRelacionados().size());
        assertEquals("NOTA_VENTA", detalle.documentosRelacionados().get(0).tipoDocumento());
        assertEquals("NV-000010", detalle.documentosRelacionados().get(0).numero());
    }

    @Test
    void unaCotizacionSinDocumentosPosterioresDevuelveLaListaVacia() {
        cotizacionEnEstado(EstadoCotizacion.BORRADOR);

        var detalle = service.obtener(100L);

        assertTrue(detalle.documentosRelacionados().isEmpty());
    }

    @Test
    void elDashboardResumeCantidadesMontosYTasaDeConversion() {
        when(cotizacionRepository.findByTenantIdAndFechaEmisionBetweenOrderByFolioAsc(eq(tenantId), any(), any()))
                .thenReturn(List.of(
                        conEstadoYTotal(1L, EstadoCotizacion.BORRADOR, "1000"),
                        conEstadoYTotal(2L, EstadoCotizacion.ENVIADA, "2000"),
                        conEstadoYTotal(3L, EstadoCotizacion.ACEPTADA, "3000"),
                        conEstadoYTotal(4L, EstadoCotizacion.RECHAZADA, "4000"),
                        conEstadoYTotal(5L, EstadoCotizacion.VENCIDA, "5000")));

        var dashboard = service.dashboard(hoy, vence);

        assertEquals(5, dashboard.cantidad());
        assertEquals(1, dashboard.pendientes());
        assertEquals(1, dashboard.aceptadas());
        assertEquals(1, dashboard.rechazadas());
        assertEquals(4, dashboard.enviadas());
        assertEquals(new BigDecimal("15000"), dashboard.montoCotizado());
        assertEquals(new BigDecimal("3000"), dashboard.montoAceptado());
        assertEquals(25.0, dashboard.tasaConversion());
    }

    @Test
    void elDashboardDevuelveTodosLosEstadosParaElGraficoAunqueEstenEnCero() {
        when(cotizacionRepository.findByTenantIdAndFechaEmisionBetweenOrderByFolioAsc(eq(tenantId), any(), any()))
                .thenReturn(List.of(conEstadoYTotal(1L, EstadoCotizacion.ENVIADA, "2000")));

        var dashboard = service.dashboard(hoy, vence);

        assertEquals(EstadoCotizacion.values().length, dashboard.porEstado().size());
        var borrador = dashboard.porEstado().stream()
                .filter(c -> c.estado() == EstadoCotizacion.BORRADOR).findFirst().orElseThrow();
        assertEquals(0, borrador.cantidad());
        assertEquals(BigDecimal.ZERO, borrador.monto());
    }

    @Test
    void laTasaDeConversionEsNulaSiNoHayCotizacionesEnviadas() {
        when(cotizacionRepository.findByTenantIdAndFechaEmisionBetweenOrderByFolioAsc(eq(tenantId), any(), any()))
                .thenReturn(List.of(conEstadoYTotal(1L, EstadoCotizacion.BORRADOR, "1000")));

        var dashboard = service.dashboard(hoy, vence);

        assertNull(dashboard.tasaConversion());
        assertEquals(0, dashboard.enviadas());
    }

    @Test
    void unPeriodoSinCotizacionesDevuelveTodoEnCero() {
        when(cotizacionRepository.findByTenantIdAndFechaEmisionBetweenOrderByFolioAsc(eq(tenantId), any(), any()))
                .thenReturn(List.of());

        var dashboard = service.dashboard(hoy, vence);

        assertEquals(0, dashboard.cantidad());
        assertEquals(BigDecimal.ZERO, dashboard.montoCotizado());
        assertNull(dashboard.tasaConversion());
    }

    @Test
    void elDashboardRechazaUnRangoDeFechasInvertido() {
        assertThrows(IllegalArgumentException.class, () -> service.dashboard(vence, hoy));
        verifyNoInteractions(cotizacionRepository);
    }

    @Test
    void laBusquedaRechazaUnRangoDeFechasInvertido() {
        assertThrows(IllegalArgumentException.class,
                () -> service.buscar(null, null, null, vence, hoy, null, null, null, 0, 10));
    }

    @Test
    void laBusquedaRechazaPaginasNegativasYTamanosInvalidos() {
        assertThrows(IllegalArgumentException.class,
                () -> service.buscar(null, null, null, null, null, null, null, null, -1, 10));
        assertThrows(IllegalArgumentException.class,
                () -> service.buscar(null, null, null, null, null, null, null, null, 0, 0));
        verifyNoInteractions(cotizacionRepository);
    }
}
