package cl.slimerp.notasdebito;

import cl.slimerp.catalogo.Cliente;
import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.catalogo.Producto;
import cl.slimerp.catalogo.ProductoRepository;
import cl.slimerp.common.UsuarioActualService;
import cl.slimerp.config.TenantContext;
import cl.slimerp.inventario.Bodega;
import cl.slimerp.inventario.BodegaRepository;
import cl.slimerp.inventario.MovimientoInventario;
import cl.slimerp.inventario.MovimientoInventarioHeader;
import cl.slimerp.inventario.MovimientoInventarioHeaderRepository;
import cl.slimerp.inventario.MovimientoInventarioRepository;
import cl.slimerp.inventario.StockService;
import cl.slimerp.inventario.TipoMovimiento;
import cl.slimerp.notascredito.EstadoNotaCredito;
import cl.slimerp.notascredito.NotaCredito;
import cl.slimerp.notascredito.NotaCreditoDetalle;
import cl.slimerp.notascredito.NotaCreditoRepository;
import cl.slimerp.notasventa.EstadoNotaVenta;
import cl.slimerp.notasventa.NotaVenta;
import cl.slimerp.notasventa.NotaVentaRepository;
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import cl.slimerp.ventas.TipoDocumentoVenta;
import cl.slimerp.ventas.Venta;
import cl.slimerp.ventas.VentaRepository;
import cl.slimerp.cotizaciones.Cotizacion;
import cl.slimerp.cotizaciones.CotizacionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotaDebitoServiceTest {

    private NotaDebitoRepository notaDebitoRepository;
    private NotaDebitoDetalleRepository detalleRepository;
    private NotaDebitoEventoRepository eventoRepository;
    private NotaDebitoMovimientoRepository movimientoRepository;
    private NotaDebitoFolioService folioService;
    private NotaCreditoRepository notaCreditoRepository;
    private ClienteRepository clienteRepository;
    private ProductoRepository productoRepository;
    private UsuarioRepository usuarioRepository;
    private BodegaRepository bodegaRepository;
    private StockService stockService;
    private MovimientoInventarioRepository movimientoInventarioRepository;
    private MovimientoInventarioHeaderRepository movimientoHeaderRepository;
    private UsuarioActualService usuarioActualService;
    private VentaRepository ventaRepository;
    private NotaVentaRepository notaVentaRepository;
    private CotizacionRepository cotizacionRepository;
    private NotaDebitoService service;

    private final Long tenantId = 1L;
    private final Long usuarioId = 7L;
    private final Long bodegaId = 3L;
    private final LocalDate hoy = LocalDate.of(2026, 9, 23);

    private final Cliente cliente = Cliente.builder().id(5L).tenantId(1L).nombre("Empresa ABC SpA")
            .rut("76.111.222-3").activo(true).build();
    private final Producto productoA = Producto.builder().id(10L).tenantId(1L).sku("SKU-A").nombre("Bidón 20L")
            .activo(true).build();
    private final Producto productoB = Producto.builder().id(11L).tenantId(1L).sku("SKU-B").nombre("Tapa rosca")
            .activo(true).build();
    private final Usuario usuario = Usuario.builder().id(usuarioId).tenantId(1L).nombre("Vendedor Demo").build();
    private final Bodega bodega = Bodega.builder().id(bodegaId).tenantId(1L).nombre("Bodega Central").build();

    private final List<NotaDebitoEvento> eventosGuardados = new ArrayList<>();
    private final List<NotaDebitoMovimiento> vinculosGuardados = new ArrayList<>();
    private long siguienteMovimientoId = 500L;

    @BeforeEach
    void setUp() {
        notaDebitoRepository = mock(NotaDebitoRepository.class);
        detalleRepository = mock(NotaDebitoDetalleRepository.class);
        eventoRepository = mock(NotaDebitoEventoRepository.class);
        movimientoRepository = mock(NotaDebitoMovimientoRepository.class);
        folioService = mock(NotaDebitoFolioService.class);
        notaCreditoRepository = mock(NotaCreditoRepository.class);
        clienteRepository = mock(ClienteRepository.class);
        productoRepository = mock(ProductoRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        bodegaRepository = mock(BodegaRepository.class);
        stockService = mock(StockService.class);
        movimientoInventarioRepository = mock(MovimientoInventarioRepository.class);
        movimientoHeaderRepository = mock(MovimientoInventarioHeaderRepository.class);
        usuarioActualService = mock(UsuarioActualService.class);
        ventaRepository = mock(VentaRepository.class);
        notaVentaRepository = mock(NotaVentaRepository.class);
        cotizacionRepository = mock(CotizacionRepository.class);
        service = new NotaDebitoService(notaDebitoRepository, detalleRepository, eventoRepository,
                movimientoRepository, folioService, notaCreditoRepository, clienteRepository, productoRepository,
                usuarioRepository, bodegaRepository, stockService, movimientoInventarioRepository,
                movimientoHeaderRepository, usuarioActualService, ventaRepository, notaVentaRepository,
                cotizacionRepository);

        TenantContext.setTenantId(tenantId);
        eventosGuardados.clear();
        vinculosGuardados.clear();
        siguienteMovimientoId = 500L;

        when(usuarioActualService.idUsuarioActual(tenantId)).thenReturn(usuarioId);
        when(folioService.siguienteFolio(tenantId)).thenReturn(1);
        when(clienteRepository.findById(5L)).thenReturn(Optional.of(cliente));
        when(clienteRepository.findByTenantIdAndIdIn(anyLong(), any())).thenReturn(List.of(cliente));
        when(clienteRepository.idsPorBusqueda(anyLong(), any())).thenReturn(List.of());
        when(productoRepository.findAllById(any())).thenAnswer(inv -> {
            Iterable<Long> ids = inv.getArgument(0);
            List<Producto> encontrados = new ArrayList<>();
            for (Long id : ids) {
                if (id.equals(10L)) encontrados.add(productoA);
                if (id.equals(11L)) encontrados.add(productoB);
            }
            return encontrados;
        });
        when(productoRepository.findById(10L)).thenReturn(Optional.of(productoA));
        when(productoRepository.findById(11L)).thenReturn(Optional.of(productoB));
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findAllById(any())).thenReturn(List.of(usuario));
        when(bodegaRepository.findById(bodegaId)).thenReturn(Optional.of(bodega));
        when(bodegaRepository.findAllById(any())).thenReturn(List.of(bodega));
        when(eventoRepository.findByTenantIdAndNotaDebitoIdOrderByFechaAscIdAsc(anyLong(), any()))
                .thenReturn(List.of());
        when(movimientoRepository.findByTenantIdAndNotaDebitoIdOrderByFechaAscIdAsc(anyLong(), any()))
                .thenReturn(List.of());
        when(movimientoRepository.findByTenantIdAndNotaDebitoIdAndTipo(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(detalleRepository.cantidadesRevertidas(anyLong(), any())).thenReturn(List.of());
        when(detalleRepository.cantidadesRevertidasExcluyendo(anyLong(), any(), any())).thenReturn(List.of());
        when(notaCreditoRepository.findByIdAndTenantId(90L, tenantId)).thenReturn(Optional.of(notaCredito()));

        when(eventoRepository.save(any(NotaDebitoEvento.class))).thenAnswer(inv -> {
            NotaDebitoEvento e = inv.getArgument(0);
            eventosGuardados.add(e);
            return e;
        });
        when(movimientoRepository.save(any(NotaDebitoMovimiento.class))).thenAnswer(inv -> {
            NotaDebitoMovimiento m = inv.getArgument(0);
            vinculosGuardados.add(m);
            return m;
        });
        when(notaDebitoRepository.save(any(NotaDebito.class))).thenAnswer(inv -> {
            NotaDebito nd = inv.getArgument(0);
            if (nd.getId() == null) {
                nd.setId(100L);
            }
            long idLinea = 200L;
            for (NotaDebitoDetalle linea : nd.getDetalle()) {
                if (linea.getId() == null) {
                    linea.setId(idLinea++);
                }
            }
            return nd;
        });
        when(movimientoHeaderRepository.save(any(MovimientoInventarioHeader.class))).thenAnswer(inv -> {
            MovimientoInventarioHeader h = inv.getArgument(0);
            h.setId(900L);
            return h;
        });
        when(stockService.sumar(anyLong(), anyLong(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> MovimientoInventario.builder()
                        .id(siguienteMovimientoId++)
                        .tenantId(inv.getArgument(0))
                        .productoId(inv.getArgument(1))
                        .bodegaId(inv.getArgument(2))
                        .cantidad(((BigDecimal) inv.getArgument(3)).abs())
                        .tipo(inv.getArgument(4))
                        .fecha(LocalDateTime.now())
                        .build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // La nota de crédito emitida que se revierte: 10 bidones a $1.000 y 4 tapas
    // a $200, factura afecta (la venta original fue FACTURA).
    private NotaCredito notaCredito() {
        NotaCredito nc = NotaCredito.builder()
                .id(90L).tenantId(tenantId).clienteId(5L).usuarioId(usuarioId).folio(90)
                .estado(EstadoNotaCredito.EMITIDA).tipoCorreccion(cl.slimerp.notascredito.TipoCorreccion.CORRIGE_DOCUMENTO)
                .fecha(LocalDate.of(2026, 9, 15)).bodegaId(bodegaId).exenta(false).moneda("CLP")
                .ventaId(50L).docAsociadoTipo(TipoDocumentoVenta.FACTURA).docAsociadoFolio(1042)
                .docAsociadoFecha(LocalDate.of(2026, 9, 12)).docAsociadoRazon("Anulación total")
                .montoNeto(new BigDecimal("10800")).montoIva(new BigDecimal("2052"))
                .montoTotal(new BigDecimal("12852"))
                .detalle(new ArrayList<>())
                .build();
        nc.getDetalle().add(lineaNc(nc, 401L, 10L, new BigDecimal("10"), new BigDecimal("1000"), true));
        nc.getDetalle().add(lineaNc(nc, 402L, 11L, new BigDecimal("4"), new BigDecimal("200"), true));
        return nc;
    }

    private NotaCreditoDetalle lineaNc(NotaCredito nc, Long id, Long productoId, BigDecimal cantidad,
                                       BigDecimal precio, boolean recupera) {
        return NotaCreditoDetalle.builder()
                .id(id).notaCredito(nc).productoId(productoId).ventaDetalleId(id)
                .codigo(productoId.equals(10L) ? "SKU-A" : "SKU-B")
                .descripcion(productoId.equals(10L) ? "Bidón 20L" : "Tapa rosca")
                .cantidad(cantidad).precioUnitario(precio)
                .descuento(BigDecimal.ZERO).subtotal(cantidad.multiply(precio))
                .recuperaInventario(recupera)
                .build();
    }

    private NotaDebitoRequest revierteMonto(BigDecimal cantidad, boolean revierteInventario) {
        return new NotaDebitoRequest(90L, TipoReversion.REVIERTE_MONTO, hoy, "Devolución rechazada",
                "La mercadería no se acepta", null, null, null,
                List.of(new NotaDebitoRequest.Item(10L, 401L, cantidad, new BigDecimal("1000"),
                        null, revierteInventario)));
    }

    private void existeNota(NotaDebito nd) {
        when(notaDebitoRepository.findByIdAndTenantId(nd.getId(), tenantId)).thenReturn(Optional.of(nd));
    }

    // --- Documento asociado --------------------------------------------------

    @Test
    void creaTomandoClienteBodegaYSnapshotDeLaNotaDeCreditoAsociada() {
        var nd = service.crear(revierteMonto(new BigDecimal("2"), true));

        assertEquals("ND-000001", nd.numero());
        assertEquals(EstadoNotaDebito.BORRADOR, nd.estado());
        assertEquals(5L, nd.clienteId());
        assertEquals(bodegaId, nd.bodegaId());
        assertEquals(90L, nd.documentoAsociado().notaCreditoId());
        assertEquals("NC-000090", nd.documentoAsociado().numero());
        assertEquals(0, nd.documentoAsociado().montoTotal().compareTo(new BigDecimal("12852")));
        assertEquals("Devolución rechazada", nd.documentoAsociado().razon());
    }

    @Test
    void rechazaUnaNotaDeCreditoAsociadaInexistente() {
        when(notaCreditoRepository.findByIdAndTenantId(999L, tenantId)).thenReturn(Optional.empty());
        var request = new NotaDebitoRequest(999L, TipoReversion.REVIERTE_TEXTO, hoy, "Razón", null, null,
                "Se corrige el giro", null, List.of());

        var error = assertThrows(IllegalArgumentException.class, () -> service.crear(request));
        assertTrue(error.getMessage().contains("Nota de crédito no encontrada"));
    }

    @Test
    void rechazaUnaNotaDeCreditoAsociadaQueNoEstaEmitida() {
        NotaCredito borrador = notaCredito();
        borrador.setEstado(EstadoNotaCredito.BORRADOR);
        when(notaCreditoRepository.findByIdAndTenantId(90L, tenantId)).thenReturn(Optional.of(borrador));

        var error = assertThrows(IllegalArgumentException.class, () -> service.crear(revierteMonto(BigDecimal.ONE, true)));
        assertTrue(error.getMessage().contains("EMITIDAS"), error.getMessage());
    }

    @Test
    void lineasDeLaNotaDeCreditoTraenLaCantidadDisponibleCompletaSinNotasEmitidas() {
        var lineas = service.lineasNotaCredito(90L, null);

        assertEquals(2, lineas.size());
        assertEquals("Bidón 20L", lineas.get(0).descripcion());
        assertEquals(0, lineas.get(0).cantidadRevertida().compareTo(BigDecimal.ZERO));
        assertEquals(0, lineas.get(0).cantidadDisponible().compareTo(new BigDecimal("10")));
    }

    @Test
    void lineasDeLaNotaDeCreditoDescuentanLoYaRevertido() {
        when(detalleRepository.cantidadesRevertidas(tenantId, 90L))
                .thenReturn(List.of(new CantidadRevertida(401L, new BigDecimal("6"))));

        var lineas = service.lineasNotaCredito(90L, null);

        assertEquals(0, lineas.get(0).cantidadDisponible().compareTo(new BigDecimal("4")));
        assertEquals(0, lineas.get(1).cantidadDisponible().compareTo(new BigDecimal("4")));
    }

    @Test
    void lasAsociablesSoloTraenNotasDeCreditoEmitidas() {
        NotaCredito nc = notaCredito();
        when(notaDebitoRepository.findByTenantIdAndNotaCreditoIdInOrderByFolioAsc(tenantId, List.of(90L)))
                .thenReturn(List.of());
        when(notaCreditoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(nc), PageRequest.of(0, 20), 1));

        var asociables = service.notasCreditoAsociables(null, null);

        assertEquals(1, asociables.size());
        assertEquals("NC-000090", asociables.get(0).numero());
        assertEquals(0, asociables.get(0).montoDisponible().compareTo(new BigDecimal("12852")));
        assertFalse(asociables.get(0).tieneNotasDebito());
    }

    // --- Tipo de reversión ---------------------------------------------------

    @Test
    void revierteTextoRechazaLineasDeDetalle() {
        var request = new NotaDebitoRequest(90L, TipoReversion.REVIERTE_TEXTO, hoy, "Razón", null, null,
                "Se corrige el giro", null,
                List.of(new NotaDebitoRequest.Item(10L, 401L, BigDecimal.ONE, new BigDecimal("1000"), null, false)));

        var error = assertThrows(IllegalArgumentException.class, () -> service.crear(request));
        assertTrue(error.getMessage().contains("no puede tener líneas de detalle"));
    }

    @Test
    void revierteTextoExigeElTextoDeLaCorreccion() {
        var request = new NotaDebitoRequest(90L, TipoReversion.REVIERTE_TEXTO, hoy, "Razón", null, null,
                "   ", null, List.of());

        var error = assertThrows(IllegalArgumentException.class, () -> service.crear(request));
        assertTrue(error.getMessage().contains("texto de la corrección"));
    }

    @Test
    void revierteTextoDejaLosMontosEnCero() {
        var request = new NotaDebitoRequest(90L, TipoReversion.REVIERTE_TEXTO, hoy, "Razón", null, null,
                "Se corrige el giro del cliente", null, List.of());

        var nd = service.crear(request);

        assertEquals(0, nd.montoTotal().compareTo(BigDecimal.ZERO));
        assertEquals(0, nd.montoNeto().compareTo(BigDecimal.ZERO));
        assertEquals(0, nd.montoIva().compareTo(BigDecimal.ZERO));
        assertTrue(nd.lineas().isEmpty());
        assertEquals("Se corrige el giro del cliente", nd.textoCorreccion());
    }

    @Test
    void revierteMontoExigeAlMenosUnaLinea() {
        var request = new NotaDebitoRequest(90L, TipoReversion.REVIERTE_MONTO, hoy, "Razón", null, null,
                null, null, List.of());

        var error = assertThrows(IllegalArgumentException.class, () -> service.crear(request));
        assertTrue(error.getMessage().contains("al menos una línea"));
    }

    @Test
    void revierteDocumentoPrecargaTodasLasLineasDeLaNotaDeCredito() {
        var request = new NotaDebitoRequest(90L, TipoReversion.REVIERTE_DOCUMENTO, hoy, "Anulación total",
                "Se revierte la NC completa", null, null, null, List.of());

        var nd = service.crear(request);

        assertEquals(2, nd.lineas().size());
        assertTrue(nd.lineas().stream().allMatch(NotaDebitoService.LineaNotaDebito::revierteInventario));
        assertEquals(ImpactoInventario.TOTAL, nd.impactoInventario());
        assertEquals(0, nd.montoSubtotal().compareTo(new BigDecimal("10800")));
    }

    @Test
    void unaLineaAjenaALaNotaDeCreditoNoPuedeRevertirInventario() {
        var request = new NotaDebitoRequest(90L, TipoReversion.REVIERTE_MONTO, hoy, "Razón", null, null, null, null,
                List.of(new NotaDebitoRequest.Item(10L, null, BigDecimal.ONE, new BigDecimal("1000"), null, true)));

        var error = assertThrows(IllegalArgumentException.class, () -> service.crear(request));
        assertTrue(error.getMessage().contains("no pertenece a la nota de crédito"));
    }

    @Test
    void rechazaUnaLineaQueApuntaAOtraNotaDeCredito() {
        var request = new NotaDebitoRequest(90L, TipoReversion.REVIERTE_MONTO, hoy, "Razón", null, null, null, null,
                List.of(new NotaDebitoRequest.Item(10L, 999L, BigDecimal.ONE, new BigDecimal("1000"), null, false)));

        var error = assertThrows(IllegalArgumentException.class, () -> service.crear(request));
        assertTrue(error.getMessage().contains("no pertenece a la nota de crédito"));
    }

    // --- Montos --------------------------------------------------------------

    @Test
    void sobreUnaFacturaAfectaElIvaSeSumaAlNeto() {
        var nd = service.crear(revierteMonto(new BigDecimal("2"), false));

        assertEquals(0, nd.montoNeto().compareTo(new BigDecimal("2000.00")));
        assertEquals(0, nd.montoIva().compareTo(new BigDecimal("380.00")));
        assertEquals(0, nd.montoTotal().compareTo(new BigDecimal("2380.00")));
    }

    @Test
    void sobreUnaBoletaAfectaElTotalSeDesglosaDesdeElBruto() {
        NotaCredito boleta = notaCredito();
        boleta.setDocAsociadoTipo(TipoDocumentoVenta.BOLETA);
        when(notaCreditoRepository.findByIdAndTenantId(90L, tenantId)).thenReturn(Optional.of(boleta));

        var request = new NotaDebitoRequest(90L, TipoReversion.REVIERTE_MONTO, hoy, "Razón", null, null, null, null,
                List.of(new NotaDebitoRequest.Item(10L, 401L, BigDecimal.ONE, new BigDecimal("1190"), null, false)));
        var nd = service.crear(request);

        assertEquals(0, nd.montoTotal().compareTo(new BigDecimal("1190.00")));
        assertEquals(0, nd.montoNeto().compareTo(new BigDecimal("1000.00")));
        assertEquals(0, nd.montoIva().compareTo(new BigDecimal("190.00")));
    }

    @Test
    void elDescuentoDeLineaNoPuedeSuperarSuSubtotal() {
        var request = new NotaDebitoRequest(90L, TipoReversion.REVIERTE_MONTO, hoy, "Razón", null, null, null, null,
                List.of(new NotaDebitoRequest.Item(10L, 401L, BigDecimal.ONE, new BigDecimal("1000"),
                        new BigDecimal("1500"), false)));

        var error = assertThrows(IllegalArgumentException.class, () -> service.crear(request));
        assertTrue(error.getMessage().contains("no puede superar el subtotal de la línea"));
    }

    // --- Estados -------------------------------------------------------------

    @Test
    void crearRegistraUnEventoDeCreacion() {
        service.crear(revierteMonto(new BigDecimal("2"), true));

        assertEquals(1, eventosGuardados.size());
        assertEquals(AccionNotaDebito.CREADA, eventosGuardados.get(0).getAccion());
        assertNull(eventosGuardados.get(0).getEstadoAnterior());
        assertEquals(EstadoNotaDebito.BORRADOR, eventosGuardados.get(0).getEstadoNuevo());
    }

    @Test
    void noSePuedeEditarUnaNotaEmitida() {
        NotaDebito nd = notaEnEstado(EstadoNotaDebito.EMITIDA, new BigDecimal("2"), true);
        existeNota(nd);

        var error = assertThrows(IllegalArgumentException.class,
                () -> service.actualizar(100L, revierteMonto(new BigDecimal("1"), true)));
        assertTrue(error.getMessage().contains("EMITIDA"));
    }

    @Test
    void noSePuedeCambiarLaNotaDeCreditoAsociadaAlEditar() {
        NotaDebito nd = notaEnEstado(EstadoNotaDebito.BORRADOR, new BigDecimal("2"), true);
        existeNota(nd);
        var otraNota = new NotaDebitoRequest(91L, TipoReversion.REVIERTE_MONTO, hoy, "Razón", null, null,
                null, null, List.of(new NotaDebitoRequest.Item(10L, 401L, BigDecimal.ONE,
                        new BigDecimal("1000"), null, false)));

        var error = assertThrows(IllegalArgumentException.class, () -> service.actualizar(100L, otraNota));
        assertTrue(error.getMessage().contains("nota de crédito asociada"));
    }

    @Test
    void noSePuedeEliminarUnaNotaEmitida() {
        existeNota(notaEnEstado(EstadoNotaDebito.EMITIDA, new BigDecimal("2"), true));

        assertThrows(IllegalArgumentException.class, () -> service.eliminar(100L));
        verify(notaDebitoRepository, never()).delete(any(NotaDebito.class));
    }

    @Test
    void noSePuedeEmitirDosVeces() {
        existeNota(notaEnEstado(EstadoNotaDebito.EMITIDA, new BigDecimal("2"), true));

        var error = assertThrows(IllegalArgumentException.class, () -> service.emitir(100L));
        assertTrue(error.getMessage().contains("emitir"));
    }

    @Test
    void noSePuedeAnularUnBorrador() {
        existeNota(notaEnEstado(EstadoNotaDebito.BORRADOR, new BigDecimal("2"), true));

        var error = assertThrows(IllegalArgumentException.class, () -> service.anular(100L, "motivo"));
        assertTrue(error.getMessage().contains("anular"));
    }

    // --- Inventario ----------------------------------------------------------

    @Test
    void emitirGeneraUnaSalidaDeInventarioPorLineaQueRevierte() {
        existeNota(notaEnEstado(EstadoNotaDebito.BORRADOR, new BigDecimal("2"), true));

        var nd = service.emitir(100L);

        ArgumentCaptor<BigDecimal> cantidad = ArgumentCaptor.forClass(BigDecimal.class);
        // La salida cuelga de una cabecera SALIDA: es lo que la hace visible en el
        // historial de Inventario y le da detalle y exportaciones.
        verify(stockService).sumar(eq(tenantId), eq(10L), eq(bodegaId), cantidad.capture(),
                eq(TipoMovimiento.SALIDA_NOTA_CREDITO), eq(900L), eq(100L));
        assertEquals(0, cantidad.getValue().compareTo(new BigDecimal("-2")));
        assertEquals(EstadoNotaDebito.EMITIDA, nd.estado());
        assertNotNull(nd.fechaEmision());

        assertEquals(1, vinculosGuardados.size());
        assertEquals(TipoMovimientoNotaDebito.REVERSION, vinculosGuardados.get(0).getTipo());
        assertEquals(500L, vinculosGuardados.get(0).getMovimientoInventarioId());
    }

    @Test
    void emitirNoTocaInventarioCuandoLaLineaNoRevierte() {
        existeNota(notaEnEstado(EstadoNotaDebito.BORRADOR, new BigDecimal("2"), false));

        service.emitir(100L);

        verifyNoInteractions(stockService);
        assertTrue(vinculosGuardados.isEmpty());
    }

    @Test
    void emitirUnaReversionDeTextoNoTocaInventario() {
        NotaDebito nd = NotaDebito.builder()
                .id(100L).tenantId(tenantId).folio(1).clienteId(5L).usuarioId(usuarioId)
                .estado(EstadoNotaDebito.BORRADOR).tipoReversion(TipoReversion.REVIERTE_TEXTO)
                .fecha(hoy).bodegaId(bodegaId).notaCreditoId(90L)
                .ncDocAsociadoTipo(TipoDocumentoVenta.FACTURA).ncFolio(90)
                .textoCorreccion("Se corrige el giro")
                .build();
        existeNota(nd);

        service.emitir(100L);

        verifyNoInteractions(stockService);
    }

    @Test
    void noSePuedeRevertirMasDeLoDisponible() {
        // Ya se revirtió la recuperación de 9 de los 10 bidones en otra nota emitida.
        when(detalleRepository.cantidadesRevertidasExcluyendo(tenantId, 90L, 100L))
                .thenReturn(List.of(new CantidadRevertida(401L, new BigDecimal("9"))));
        existeNota(notaEnEstado(EstadoNotaDebito.BORRADOR, new BigDecimal("2"), true));

        var error = assertThrows(IllegalArgumentException.class, () -> service.emitir(100L));
        assertTrue(error.getMessage().contains("Bidón 20L"), error.getMessage());
        assertTrue(error.getMessage().contains("1"), error.getMessage());
        verifyNoInteractions(stockService);
    }

    @Test
    void emitirCreaLaCabeceraDeSalidaQueUsaElModuloDeInventario() {
        existeNota(notaEnEstado(EstadoNotaDebito.BORRADOR, new BigDecimal("2"), true));

        service.emitir(100L);

        ArgumentCaptor<MovimientoInventarioHeader> header =
                ArgumentCaptor.forClass(MovimientoInventarioHeader.class);
        verify(movimientoHeaderRepository).save(header.capture());
        assertEquals(TipoMovimiento.SALIDA, header.getValue().getTipo());
        assertEquals(bodegaId, header.getValue().getBodegaOrigenId());
        assertNull(header.getValue().getBodegaDestinoId());
        assertEquals(usuarioId, header.getValue().getUsuarioId());
        assertTrue(header.getValue().getObservacion().contains("ND-000001"));
    }

    @Test
    void anularRevierteCadaMovimientoDeReversion() {
        NotaDebito nd = notaEnEstado(EstadoNotaDebito.EMITIDA, new BigDecimal("2"), true);
        existeNota(nd);

        NotaDebitoMovimiento reversion = NotaDebitoMovimiento.builder()
                .id(1L).tenantId(tenantId).notaDebitoId(100L).notaDebitoDetalleId(200L)
                .movimientoInventarioId(500L).tipo(TipoMovimientoNotaDebito.REVERSION).build();
        when(movimientoRepository.findByTenantIdAndNotaDebitoIdAndTipo(
                tenantId, 100L, TipoMovimientoNotaDebito.REVERSION)).thenReturn(List.of(reversion));
        when(movimientoInventarioRepository.findById(500L)).thenReturn(Optional.of(MovimientoInventario.builder()
                .id(500L).tenantId(tenantId).productoId(10L).bodegaId(bodegaId)
                .cantidad(new BigDecimal("2")).tipo(TipoMovimiento.SALIDA_NOTA_CREDITO).build()));

        var resultado = service.anular(100L, "Reversión rechazada");

        ArgumentCaptor<BigDecimal> cantidad = ArgumentCaptor.forClass(BigDecimal.class);
        verify(stockService).sumar(eq(tenantId), eq(10L), eq(bodegaId), cantidad.capture(),
                eq(TipoMovimiento.ENTRADA_NOTA_DEBITO), eq(900L), eq(100L));
        assertEquals(0, cantidad.getValue().compareTo(new BigDecimal("2")));
        assertEquals(EstadoNotaDebito.ANULADA, resultado.estado());
        assertNotNull(resultado.fechaAnulacion());

        assertEquals(1, vinculosGuardados.size());
        assertEquals(TipoMovimientoNotaDebito.REVERSA_ANULACION, vinculosGuardados.get(0).getTipo());
        assertEquals("Reversión rechazada", eventosGuardados.get(0).getDetalle());
    }

    @Test
    void anularCreaUnaCabeceraDeEntradaParaLaReversa() {
        NotaDebito nd = notaEnEstado(EstadoNotaDebito.EMITIDA, new BigDecimal("2"), true);
        existeNota(nd);
        when(movimientoRepository.findByTenantIdAndNotaDebitoIdAndTipo(
                tenantId, 100L, TipoMovimientoNotaDebito.REVERSION))
                .thenReturn(List.of(NotaDebitoMovimiento.builder()
                        .id(1L).tenantId(tenantId).notaDebitoId(100L).notaDebitoDetalleId(200L)
                        .movimientoInventarioId(500L).tipo(TipoMovimientoNotaDebito.REVERSION).build()));
        when(movimientoInventarioRepository.findById(500L)).thenReturn(Optional.of(MovimientoInventario.builder()
                .id(500L).tenantId(tenantId).productoId(10L).bodegaId(bodegaId)
                .cantidad(new BigDecimal("2")).tipo(TipoMovimiento.SALIDA_NOTA_CREDITO).build()));

        service.anular(100L, "Reversión rechazada");

        ArgumentCaptor<MovimientoInventarioHeader> header =
                ArgumentCaptor.forClass(MovimientoInventarioHeader.class);
        verify(movimientoHeaderRepository).save(header.capture());
        assertEquals(TipoMovimiento.ENTRADA, header.getValue().getTipo());
        assertEquals(bodegaId, header.getValue().getBodegaDestinoId());
        assertNull(header.getValue().getBodegaOrigenId());
    }

    @Test
    void sinReversionDeInventarioNoSeCreaCabecera() {
        existeNota(notaEnEstado(EstadoNotaDebito.BORRADOR, new BigDecimal("2"), false));

        service.emitir(100L);

        verifyNoInteractions(movimientoHeaderRepository);
    }

    @Test
    void noSePuedeEmitirPorMasDelMontoDisponibleDeLaNotaDeCredito() {
        // Otra ND emitida ya revirtió $12.000 de los $12.852; esta intenta $2.380.
        NotaDebito otra = notaEnEstado(EstadoNotaDebito.EMITIDA, new BigDecimal("10"), true);
        otra.setId(101L);
        otra.setMontoTotal(new BigDecimal("12000"));
        when(notaDebitoRepository.findByTenantIdAndNotaCreditoIdOrderByFolioAsc(tenantId, 90L))
                .thenReturn(List.of(otra));
        NotaDebito borrador = notaEnEstado(EstadoNotaDebito.BORRADOR, new BigDecimal("2"), true);
        borrador.setMontoTotal(new BigDecimal("2380"));
        existeNota(borrador);

        var error = assertThrows(IllegalArgumentException.class, () -> service.emitir(100L));
        assertTrue(error.getMessage().contains("supera el disponible de la nota de crédito"));
        verifyNoInteractions(stockService);
    }

    // --- Listado y dashboard -------------------------------------------------

    @Test
    void elListadoValidaLaPaginacionYElRango() {
        assertThrows(IllegalArgumentException.class,
                () -> service.buscar(null, null, null, null, null, null, null, null, null, -1, 10));
        assertThrows(IllegalArgumentException.class,
                () -> service.buscar(null, null, null, null, null, null, null, null, null, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> service.buscar(null, null, null, null, hoy, hoy.minusDays(1), null, null, null, 0, 10));
    }

    @Test
    void elListadoFuncionaSinParametroDeOrdenamiento() {
        // "sort" es opcional en la API y Set.of(...).contains(null) lanza NPE:
        // sin ordenamiento explícito el listado debe caer al orden por defecto.
        when(notaDebitoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        var pagina = service.buscar(null, null, null, null, null, null, null, null, null, 0, 10);

        assertEquals(0, pagina.total());
    }

    @Test
    void elDashboardSoloCuentaComoMontoLasNotasEmitidas() {
        NotaDebito emitida = notaEnEstado(EstadoNotaDebito.EMITIDA, new BigDecimal("2"), true);
        emitida.setMontoTotal(new BigDecimal("2380"));
        NotaDebito borrador = notaEnEstado(EstadoNotaDebito.BORRADOR, new BigDecimal("1"), false);
        borrador.setMontoTotal(new BigDecimal("1190"));
        when(notaDebitoRepository.findByTenantIdAndFechaBetweenOrderByFolioAsc(tenantId, hoy.minusDays(30), hoy))
                .thenReturn(List.of(emitida, borrador));

        var dashboard = service.dashboard(hoy.minusDays(30), hoy);

        assertEquals(2, dashboard.cantidad());
        assertEquals(1, dashboard.emitidas());
        assertEquals(1, dashboard.borradores());
        assertEquals(0, dashboard.montoTotalEmitido().compareTo(new BigDecimal("2380")));
        assertEquals(1, dashboard.conReversionInventario());
        assertEquals(1, dashboard.notasCreditoRevertidas());
    }

    // --- Helpers -------------------------------------------------------------

    private NotaDebito notaBase(EstadoNotaDebito estado) {
        return NotaDebito.builder()
                .id(100L).tenantId(tenantId).folio(1).clienteId(5L).usuarioId(usuarioId)
                .estado(estado).tipoReversion(TipoReversion.REVIERTE_MONTO)
                .fecha(hoy).bodegaId(bodegaId).notaCreditoId(90L)
                .ncDocAsociadoTipo(TipoDocumentoVenta.FACTURA).ncFolio(90)
                .ncFecha(LocalDate.of(2026, 9, 15)).ncMontoTotal(new BigDecimal("12852"))
                .ncRazon("Devolución rechazada")
                .detalle(new ArrayList<>())
                .build();
    }

    private NotaDebito notaEnEstado(EstadoNotaDebito estado, BigDecimal cantidad, boolean revierte) {
        NotaDebito nd = notaBase(estado);
        nd.getDetalle().add(linea(nd, 200L, 10L, 401L, cantidad, revierte));
        return nd;
    }

    private NotaDebitoDetalle linea(NotaDebito nd, Long id, Long productoId, Long notaCreditoDetalleId,
                                    BigDecimal cantidad, boolean revierte) {
        return NotaDebitoDetalle.builder()
                .id(id).notaDebito(nd).productoId(productoId).notaCreditoDetalleId(notaCreditoDetalleId)
                .codigo("SKU-A").descripcion("Bidón 20L")
                .cantidad(cantidad).precioUnitario(new BigDecimal("1000"))
                .descuento(BigDecimal.ZERO).subtotal(cantidad.multiply(new BigDecimal("1000")))
                .revierteInventario(revierte)
                .build();
    }

    // --- Trazabilidad --------------------------------------------------------

    private Venta venta() {
        return Venta.builder().id(50L).tenantId(tenantId).folio(1042).notaVentaId(60L)
                .fecha(LocalDateTime.of(2026, 9, 12, 10, 30))
                .montoTotal(new BigDecimal("12852.00")).build();
    }

    private NotaVenta notaVenta() {
        return NotaVenta.builder().id(60L).tenantId(tenantId).folio(6).clienteId(5L).cotizacionId(70L)
                .estado(EstadoNotaVenta.CONFIRMADA).fechaEmision(LocalDate.of(2026, 9, 10))
                .montoTotal(new BigDecimal("12852.00")).build();
    }

    private Cotizacion cotizacion() {
        return Cotizacion.builder().id(70L).tenantId(tenantId).folio(6)
                .estado(cl.slimerp.cotizaciones.EstadoCotizacion.ACEPTADA)
                .fechaEmision(LocalDate.of(2026, 9, 8))
                .montoTotal(new BigDecimal("12852.00")).build();
    }

    @Test
    void cadenaDeRecorreCotizacionNotaVentaVentayNotaCredito() {
        NotaDebito nd = notaBase(EstadoNotaDebito.EMITIDA);
        existeNota(nd);
        when(notaCreditoRepository.findByIdAndTenantId(90L, tenantId)).thenReturn(Optional.of(notaCredito()));
        when(ventaRepository.findByIdAndTenantIdAndActivoTrue(50L, tenantId)).thenReturn(Optional.of(venta()));
        when(notaVentaRepository.findByIdAndTenantId(60L, tenantId)).thenReturn(Optional.of(notaVenta()));
        when(cotizacionRepository.findByIdAndTenantId(70L, tenantId)).thenReturn(Optional.of(cotizacion()));

        List<NotaDebitoService.EslabonCadena> cadena = service.cadenaDe(nd.getId());

        assertEquals(4, cadena.size());

        NotaDebitoService.EslabonCadena cotizacion = cadena.get(0);
        assertEquals("COTIZACION", cotizacion.tipo());
        assertEquals(70L, cotizacion.documentoId());
        assertEquals("COT-000006", cotizacion.numero());

        NotaDebitoService.EslabonCadena nv = cadena.get(1);
        assertEquals("NOTA_VENTA", nv.tipo());
        assertEquals(60L, nv.documentoId());
        assertEquals("NV-000006", nv.numero());

        NotaDebitoService.EslabonCadena venta = cadena.get(2);
        assertEquals("VENTA", venta.tipo());
        assertEquals(50L, venta.documentoId());
        assertEquals("FACTURA N.º 1042", venta.numero());
        assertEquals(0, venta.montoTotal().compareTo(new BigDecimal("12852.00")));

        NotaDebitoService.EslabonCadena nc = cadena.get(3);
        assertEquals("NOTA_CREDITO", nc.tipo());
        assertEquals(90L, nc.documentoId());
        assertEquals("NC-000090", nc.numero());
    }

    @Test
    void cadenaDeOmiteLosEslabonesQueNoExisten() {
        NotaDebito nd = notaBase(EstadoNotaDebito.EMITIDA);
        existeNota(nd);
        when(notaCreditoRepository.findByIdAndTenantId(90L, tenantId)).thenReturn(Optional.of(notaCredito()));
        when(ventaRepository.findByIdAndTenantIdAndActivoTrue(50L, tenantId)).thenReturn(Optional.empty());

        List<NotaDebitoService.EslabonCadena> cadena = service.cadenaDe(nd.getId());

        assertEquals(1, cadena.size());
        assertEquals("NOTA_CREDITO", cadena.get(0).tipo());
    }

    @Test
    void cadenaDeDevuelveVaciaSiLaNotaDeCreditoNoExiste() {
        NotaDebito nd = notaBase(EstadoNotaDebito.EMITIDA);
        existeNota(nd);
        when(notaCreditoRepository.findByIdAndTenantId(90L, tenantId)).thenReturn(Optional.empty());

        assertTrue(service.cadenaDe(nd.getId()).isEmpty());
    }
}