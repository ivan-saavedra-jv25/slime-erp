package cl.slimerp.notascredito;

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
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import cl.slimerp.ventas.TipoDocumentoVenta;
import cl.slimerp.ventas.Venta;
import cl.slimerp.ventas.VentaDetalle;
import cl.slimerp.ventas.VentaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
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

class NotaCreditoServiceTest {

    private NotaCreditoRepository notaCreditoRepository;
    private NotaCreditoDetalleRepository detalleRepository;
    private NotaCreditoEventoRepository eventoRepository;
    private NotaCreditoMovimientoRepository movimientoRepository;
    private NotaCreditoFolioService folioService;
    private VentaRepository ventaRepository;
    private ClienteRepository clienteRepository;
    private ProductoRepository productoRepository;
    private UsuarioRepository usuarioRepository;
    private BodegaRepository bodegaRepository;
    private StockService stockService;
    private MovimientoInventarioRepository movimientoInventarioRepository;
    private MovimientoInventarioHeaderRepository movimientoHeaderRepository;
    private UsuarioActualService usuarioActualService;
    private NotaCreditoService service;

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

    private final List<NotaCreditoEvento> eventosGuardados = new ArrayList<>();
    private final List<NotaCreditoMovimiento> vinculosGuardados = new ArrayList<>();
    private long siguienteMovimientoId = 500L;

    @BeforeEach
    void setUp() {
        notaCreditoRepository = mock(NotaCreditoRepository.class);
        detalleRepository = mock(NotaCreditoDetalleRepository.class);
        eventoRepository = mock(NotaCreditoEventoRepository.class);
        movimientoRepository = mock(NotaCreditoMovimientoRepository.class);
        folioService = mock(NotaCreditoFolioService.class);
        ventaRepository = mock(VentaRepository.class);
        clienteRepository = mock(ClienteRepository.class);
        productoRepository = mock(ProductoRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        bodegaRepository = mock(BodegaRepository.class);
        stockService = mock(StockService.class);
        movimientoInventarioRepository = mock(MovimientoInventarioRepository.class);
        movimientoHeaderRepository = mock(MovimientoInventarioHeaderRepository.class);
        usuarioActualService = mock(UsuarioActualService.class);
        service = new NotaCreditoService(notaCreditoRepository, detalleRepository, eventoRepository,
                movimientoRepository, folioService, ventaRepository, clienteRepository, productoRepository,
                usuarioRepository, bodegaRepository, stockService, movimientoInventarioRepository,
                movimientoHeaderRepository, usuarioActualService);

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
        when(eventoRepository.findByTenantIdAndNotaCreditoIdOrderByFechaAscIdAsc(anyLong(), any()))
                .thenReturn(List.of());
        when(movimientoRepository.findByTenantIdAndNotaCreditoIdOrderByFechaAscIdAsc(anyLong(), any()))
                .thenReturn(List.of());
        when(movimientoRepository.findByTenantIdAndNotaCreditoIdAndTipo(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(detalleRepository.cantidadesRecuperadas(anyLong(), any())).thenReturn(List.of());
        when(detalleRepository.cantidadesRecuperadasExcluyendo(anyLong(), any(), any())).thenReturn(List.of());
        when(ventaRepository.findByIdAndTenantIdAndActivoTrue(50L, tenantId)).thenReturn(Optional.of(venta()));

        when(eventoRepository.save(any(NotaCreditoEvento.class))).thenAnswer(inv -> {
            NotaCreditoEvento e = inv.getArgument(0);
            eventosGuardados.add(e);
            return e;
        });
        when(movimientoRepository.save(any(NotaCreditoMovimiento.class))).thenAnswer(inv -> {
            NotaCreditoMovimiento m = inv.getArgument(0);
            vinculosGuardados.add(m);
            return m;
        });
        when(notaCreditoRepository.save(any(NotaCredito.class))).thenAnswer(inv -> {
            NotaCredito nc = inv.getArgument(0);
            if (nc.getId() == null) {
                nc.setId(100L);
            }
            long idLinea = 200L;
            for (NotaCreditoDetalle linea : nc.getDetalle()) {
                if (linea.getId() == null) {
                    linea.setId(idLinea++);
                }
            }
            return nc;
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

    // La venta original: 10 bidones a $1.000 y 4 tapas a $200, factura afecta.
    private Venta venta() {
        Venta v = Venta.builder()
                .id(50L).tenantId(tenantId).clienteId(5L).bodegaId(bodegaId).formaPagoId(1L)
                .tipoDocumento(TipoDocumentoVenta.FACTURA).exento(false).folio(1042)
                .fecha(LocalDateTime.of(2026, 9, 12, 10, 0))
                .montoNeto(new BigDecimal("10800")).montoIva(new BigDecimal("2052"))
                .montoTotal(new BigDecimal("12852"))
                .activo(true)
                .build();
        VentaDetalle d1 = VentaDetalle.builder().id(301L).venta(v).productoId(10L)
                .cantidad(new BigDecimal("10")).precioUnitario(new BigDecimal("1000"))
                .descuento(BigDecimal.ZERO).subtotal(new BigDecimal("10000")).build();
        VentaDetalle d2 = VentaDetalle.builder().id(302L).venta(v).productoId(11L)
                .cantidad(new BigDecimal("4")).precioUnitario(new BigDecimal("200"))
                .descuento(BigDecimal.ZERO).subtotal(new BigDecimal("800")).build();
        v.setDetalle(new ArrayList<>(List.of(d1, d2)));
        return v;
    }

    private NotaCreditoRequest corrigeMonto(BigDecimal cantidad, boolean recuperaInventario) {
        return new NotaCreditoRequest(50L, TipoCorreccion.CORRIGE_MONTO, hoy, "Devolución parcial",
                "Mercadería en mal estado", null, null, null,
                List.of(new NotaCreditoRequest.Item(10L, 301L, cantidad, new BigDecimal("1000"),
                        null, recuperaInventario)));
    }

    private void existeNota(NotaCredito nc) {
        when(notaCreditoRepository.findByIdAndTenantId(nc.getId(), tenantId)).thenReturn(Optional.of(nc));
    }

    // --- Documento asociado --------------------------------------------------

    @Test
    void creaTomandoClienteBodegaYSnapshotDelDocumentoAsociado() {
        var nc = service.crear(corrigeMonto(new BigDecimal("2"), true));

        assertEquals("NC-000001", nc.numero());
        assertEquals(EstadoNotaCredito.BORRADOR, nc.estado());
        assertEquals(5L, nc.clienteId());
        assertEquals(bodegaId, nc.bodegaId());
        assertEquals(50L, nc.documentoAsociado().ventaId());
        assertEquals(TipoDocumentoVenta.FACTURA, nc.documentoAsociado().tipo());
        assertEquals(1042, nc.documentoAsociado().folio());
        assertEquals(LocalDate.of(2026, 9, 12), nc.documentoAsociado().fecha());
        assertEquals("Devolución parcial", nc.documentoAsociado().razon());
    }

    @Test
    void rechazaUnDocumentoAsociadoInexistente() {
        when(ventaRepository.findByIdAndTenantIdAndActivoTrue(999L, tenantId)).thenReturn(Optional.empty());
        var request = new NotaCreditoRequest(999L, TipoCorreccion.CORRIGE_TEXTO, hoy, "Razón", null, null,
                "Se corrige el giro", null, List.of());

        var error = assertThrows(IllegalArgumentException.class, () -> service.crear(request));
        assertTrue(error.getMessage().contains("Documento asociado no encontrado"));
    }

    @Test
    void lineasDelDocumentoTraenTodaLaCantidadDisponibleCuandoNoHayNotasEmitidas() {
        var lineas = service.lineasDocumento(50L, null);

        assertEquals(2, lineas.size());
        assertEquals("Bidón 20L", lineas.get(0).descripcion());
        assertEquals(0, lineas.get(0).cantidadRecuperada().compareTo(BigDecimal.ZERO));
        assertEquals(0, lineas.get(0).cantidadDisponible().compareTo(new BigDecimal("10")));
    }

    @Test
    void lineasDelDocumentoDescuentanLoYaRecuperado() {
        when(detalleRepository.cantidadesRecuperadas(tenantId, 50L))
                .thenReturn(List.of(new CantidadRecuperada(301L, new BigDecimal("4"))));

        var lineas = service.lineasDocumento(50L, null);

        assertEquals(0, lineas.get(0).cantidadDisponible().compareTo(new BigDecimal("6")));
        assertEquals(0, lineas.get(1).cantidadDisponible().compareTo(new BigDecimal("4")));
    }

    // --- Tipo de corrección --------------------------------------------------

    @Test
    void corrigeTextoRechazaLineasDeDetalle() {
        var request = new NotaCreditoRequest(50L, TipoCorreccion.CORRIGE_TEXTO, hoy, "Razón", null, null,
                "Se corrige el giro", null,
                List.of(new NotaCreditoRequest.Item(10L, 301L, BigDecimal.ONE, new BigDecimal("1000"), null, false)));

        var error = assertThrows(IllegalArgumentException.class, () -> service.crear(request));
        assertTrue(error.getMessage().contains("no puede tener líneas de detalle"));
    }

    @Test
    void corrigeTextoExigeElTextoDeLaCorreccion() {
        var request = new NotaCreditoRequest(50L, TipoCorreccion.CORRIGE_TEXTO, hoy, "Razón", null, null,
                "   ", null, List.of());

        var error = assertThrows(IllegalArgumentException.class, () -> service.crear(request));
        assertTrue(error.getMessage().contains("texto de la corrección"));
    }

    @Test
    void corrigeTextoDejaLosMontosEnCero() {
        var request = new NotaCreditoRequest(50L, TipoCorreccion.CORRIGE_TEXTO, hoy, "Razón", null, null,
                "Se corrige el giro del cliente", null, List.of());

        var nc = service.crear(request);

        assertEquals(0, nc.montoTotal().compareTo(BigDecimal.ZERO));
        assertEquals(0, nc.montoNeto().compareTo(BigDecimal.ZERO));
        assertEquals(0, nc.montoIva().compareTo(BigDecimal.ZERO));
        assertTrue(nc.lineas().isEmpty());
        assertEquals("Se corrige el giro del cliente", nc.textoCorreccion());
    }

    @Test
    void corrigeMontoExigeAlMenosUnaLinea() {
        var request = new NotaCreditoRequest(50L, TipoCorreccion.CORRIGE_MONTO, hoy, "Razón", null, null,
                null, null, List.of());

        var error = assertThrows(IllegalArgumentException.class, () -> service.crear(request));
        assertTrue(error.getMessage().contains("al menos una línea"));
    }

    @Test
    void corrigeDocumentoPrecargaTodasLasLineasDelOriginalRecuperandoInventario() {
        var request = new NotaCreditoRequest(50L, TipoCorreccion.CORRIGE_DOCUMENTO, hoy, "Anulación total",
                "Documento emitido por error", null, null, null, List.of());

        var nc = service.crear(request);

        assertEquals(2, nc.lineas().size());
        assertTrue(nc.lineas().stream().allMatch(NotaCreditoService.LineaNotaCredito::recuperaInventario));
        assertEquals(RecuperacionInventario.TOTAL, nc.recuperacionInventario());
        assertEquals(0, nc.montoSubtotal().compareTo(new BigDecimal("10800")));
    }

    @Test
    void unaLineaAjenaAlDocumentoOriginalNoPuedeRecuperarInventario() {
        var request = new NotaCreditoRequest(50L, TipoCorreccion.CORRIGE_MONTO, hoy, "Razón", null, null, null, null,
                List.of(new NotaCreditoRequest.Item(10L, null, BigDecimal.ONE, new BigDecimal("1000"), null, true)));

        var error = assertThrows(IllegalArgumentException.class, () -> service.crear(request));
        assertTrue(error.getMessage().contains("no pertenece al documento original"));
    }

    @Test
    void rechazaUnaLineaQueApuntaAOtroDocumento() {
        var request = new NotaCreditoRequest(50L, TipoCorreccion.CORRIGE_MONTO, hoy, "Razón", null, null, null, null,
                List.of(new NotaCreditoRequest.Item(10L, 999L, BigDecimal.ONE, new BigDecimal("1000"), null, false)));

        var error = assertThrows(IllegalArgumentException.class, () -> service.crear(request));
        assertTrue(error.getMessage().contains("no pertenece al documento asociado"));
    }

    // --- Montos --------------------------------------------------------------

    @Test
    void sobreUnaFacturaAfectaElIvaSeSumaAlNeto() {
        var nc = service.crear(corrigeMonto(new BigDecimal("2"), false));

        assertEquals(0, nc.montoNeto().compareTo(new BigDecimal("2000.00")));
        assertEquals(0, nc.montoIva().compareTo(new BigDecimal("380.00")));
        assertEquals(0, nc.montoTotal().compareTo(new BigDecimal("2380.00")));
    }

    @Test
    void sobreUnaBoletaAfectaElTotalSeDesglosaDesdeElBruto() {
        Venta boleta = venta();
        boleta.setTipoDocumento(TipoDocumentoVenta.BOLETA);
        when(ventaRepository.findByIdAndTenantIdAndActivoTrue(50L, tenantId)).thenReturn(Optional.of(boleta));

        var request = new NotaCreditoRequest(50L, TipoCorreccion.CORRIGE_MONTO, hoy, "Razón", null, null, null, null,
                List.of(new NotaCreditoRequest.Item(10L, 301L, BigDecimal.ONE, new BigDecimal("1190"), null, false)));
        var nc = service.crear(request);

        assertEquals(0, nc.montoTotal().compareTo(new BigDecimal("1190.00")));
        assertEquals(0, nc.montoNeto().compareTo(new BigDecimal("1000.00")));
        assertEquals(0, nc.montoIva().compareTo(new BigDecimal("190.00")));
    }

    @Test
    void elDescuentoDeLineaNoPuedeSuperarSuSubtotal() {
        var request = new NotaCreditoRequest(50L, TipoCorreccion.CORRIGE_MONTO, hoy, "Razón", null, null, null, null,
                List.of(new NotaCreditoRequest.Item(10L, 301L, BigDecimal.ONE, new BigDecimal("1000"),
                        new BigDecimal("1500"), false)));

        var error = assertThrows(IllegalArgumentException.class, () -> service.crear(request));
        assertTrue(error.getMessage().contains("no puede superar el subtotal de la línea"));
    }

    // --- Estados -------------------------------------------------------------

    @Test
    void crearRegistraUnEventoDeCreacion() {
        service.crear(corrigeMonto(new BigDecimal("2"), true));

        assertEquals(1, eventosGuardados.size());
        assertEquals(AccionNotaCredito.CREADA, eventosGuardados.get(0).getAccion());
        assertNull(eventosGuardados.get(0).getEstadoAnterior());
        assertEquals(EstadoNotaCredito.BORRADOR, eventosGuardados.get(0).getEstadoNuevo());
    }

    @Test
    void noSePuedeEditarUnaNotaEmitida() {
        NotaCredito nc = notaEnEstado(EstadoNotaCredito.EMITIDA, new BigDecimal("2"), true);
        existeNota(nc);

        var error = assertThrows(IllegalArgumentException.class,
                () -> service.actualizar(100L, corrigeMonto(new BigDecimal("1"), true)));
        assertTrue(error.getMessage().contains("EMITIDA"));
    }

    @Test
    void noSePuedeCambiarElDocumentoAsociadoAlEditar() {
        NotaCredito nc = notaEnEstado(EstadoNotaCredito.BORRADOR, new BigDecimal("2"), true);
        existeNota(nc);
        var otroDocumento = new NotaCreditoRequest(51L, TipoCorreccion.CORRIGE_MONTO, hoy, "Razón", null, null,
                null, null, List.of(new NotaCreditoRequest.Item(10L, 301L, BigDecimal.ONE,
                        new BigDecimal("1000"), null, false)));

        var error = assertThrows(IllegalArgumentException.class, () -> service.actualizar(100L, otroDocumento));
        assertTrue(error.getMessage().contains("documento asociado"));
    }

    @Test
    void noSePuedeEliminarUnaNotaEmitida() {
        existeNota(notaEnEstado(EstadoNotaCredito.EMITIDA, new BigDecimal("2"), true));

        assertThrows(IllegalArgumentException.class, () -> service.eliminar(100L));
        verify(notaCreditoRepository, never()).delete(any(NotaCredito.class));
    }

    @Test
    void noSePuedeEmitirDosVeces() {
        existeNota(notaEnEstado(EstadoNotaCredito.EMITIDA, new BigDecimal("2"), true));

        var error = assertThrows(IllegalArgumentException.class, () -> service.emitir(100L));
        assertTrue(error.getMessage().contains("emitir"));
    }

    @Test
    void noSePuedeAnularUnBorrador() {
        existeNota(notaEnEstado(EstadoNotaCredito.BORRADOR, new BigDecimal("2"), true));

        var error = assertThrows(IllegalArgumentException.class, () -> service.anular(100L, "motivo"));
        assertTrue(error.getMessage().contains("anular"));
    }

    // --- Inventario ----------------------------------------------------------

    @Test
    void emitirGeneraUnaEntradaDeInventarioPorLineaQueRecupera() {
        existeNota(notaEnEstado(EstadoNotaCredito.BORRADOR, new BigDecimal("2"), true));

        var nc = service.emitir(100L);

        ArgumentCaptor<BigDecimal> cantidad = ArgumentCaptor.forClass(BigDecimal.class);
        // El movimiento cuelga de una cabecera: es lo que lo hace visible en el
        // historial de Inventario y le da detalle y exportaciones.
        verify(stockService).sumar(eq(tenantId), eq(10L), eq(bodegaId), cantidad.capture(),
                eq(TipoMovimiento.ENTRADA_NOTA_CREDITO), eq(900L), eq(100L));
        assertEquals(0, cantidad.getValue().compareTo(new BigDecimal("2")));
        assertEquals(EstadoNotaCredito.EMITIDA, nc.estado());
        assertNotNull(nc.fechaEmision());

        assertEquals(1, vinculosGuardados.size());
        assertEquals(TipoMovimientoNotaCredito.RECUPERACION, vinculosGuardados.get(0).getTipo());
        assertEquals(500L, vinculosGuardados.get(0).getMovimientoInventarioId());
    }

    @Test
    void emitirNoTocaInventarioCuandoLaLineaNoRecupera() {
        existeNota(notaEnEstado(EstadoNotaCredito.BORRADOR, new BigDecimal("2"), false));

        service.emitir(100L);

        verifyNoInteractions(stockService);
        assertTrue(vinculosGuardados.isEmpty());
    }

    @Test
    void emitirUnaCorreccionDeTextoNoTocaInventario() {
        NotaCredito nc = NotaCredito.builder()
                .id(100L).tenantId(tenantId).folio(1).clienteId(5L).usuarioId(usuarioId)
                .estado(EstadoNotaCredito.BORRADOR).tipoCorreccion(TipoCorreccion.CORRIGE_TEXTO)
                .fecha(hoy).bodegaId(bodegaId).ventaId(50L)
                .docAsociadoTipo(TipoDocumentoVenta.FACTURA).docAsociadoFolio(1042)
                .textoCorreccion("Se corrige el giro")
                .build();
        existeNota(nc);

        service.emitir(100L);

        verifyNoInteractions(stockService);
    }

    @Test
    void noSePuedeRecuperarMasDeLoDisponible() {
        // Ya se recuperaron 9 de los 10 bidones en otra nota emitida.
        when(detalleRepository.cantidadesRecuperadasExcluyendo(tenantId, 50L, 100L))
                .thenReturn(List.of(new CantidadRecuperada(301L, new BigDecimal("9"))));
        existeNota(notaEnEstado(EstadoNotaCredito.BORRADOR, new BigDecimal("2"), true));

        var error = assertThrows(IllegalArgumentException.class, () -> service.emitir(100L));
        assertTrue(error.getMessage().contains("Bidón 20L"), error.getMessage());
        assertTrue(error.getMessage().contains("1"), error.getMessage());
        verifyNoInteractions(stockService);
    }

    @Test
    void variasLineasSobreLaMismaLineaOriginalSeAcumulanParaElControl() {
        // 6 + 6 sobre una línea de 10: por separado cada una pasa, juntas no.
        NotaCredito nc = notaBase(EstadoNotaCredito.BORRADOR);
        nc.getDetalle().add(linea(nc, 200L, 10L, 301L, new BigDecimal("6"), true));
        nc.getDetalle().add(linea(nc, 201L, 10L, 301L, new BigDecimal("6"), true));
        existeNota(nc);

        var error = assertThrows(IllegalArgumentException.class, () -> service.emitir(100L));
        assertTrue(error.getMessage().contains("Bidón 20L"), error.getMessage());
        verifyNoInteractions(stockService);
    }

    @Test
    void anularRevierteCadaMovimientoDeRecuperacion() {
        NotaCredito nc = notaEnEstado(EstadoNotaCredito.EMITIDA, new BigDecimal("2"), true);
        existeNota(nc);

        NotaCreditoMovimiento recuperacion = NotaCreditoMovimiento.builder()
                .id(1L).tenantId(tenantId).notaCreditoId(100L).notaCreditoDetalleId(200L)
                .movimientoInventarioId(500L).tipo(TipoMovimientoNotaCredito.RECUPERACION).build();
        when(movimientoRepository.findByTenantIdAndNotaCreditoIdAndTipo(
                tenantId, 100L, TipoMovimientoNotaCredito.RECUPERACION)).thenReturn(List.of(recuperacion));
        when(movimientoInventarioRepository.findById(500L)).thenReturn(Optional.of(MovimientoInventario.builder()
                .id(500L).tenantId(tenantId).productoId(10L).bodegaId(bodegaId)
                .cantidad(new BigDecimal("2")).tipo(TipoMovimiento.ENTRADA_NOTA_CREDITO).build()));

        var resultado = service.anular(100L, "Devolución rechazada");

        ArgumentCaptor<BigDecimal> cantidad = ArgumentCaptor.forClass(BigDecimal.class);
        verify(stockService).sumar(eq(tenantId), eq(10L), eq(bodegaId), cantidad.capture(),
                eq(TipoMovimiento.SALIDA_ANULA_NOTA_CREDITO), eq(900L), eq(100L));
        assertEquals(0, cantidad.getValue().compareTo(new BigDecimal("-2")));
        assertEquals(EstadoNotaCredito.ANULADA, resultado.estado());
        assertNotNull(resultado.fechaAnulacion());

        assertEquals(1, vinculosGuardados.size());
        assertEquals(TipoMovimientoNotaCredito.REVERSA_ANULACION, vinculosGuardados.get(0).getTipo());
        assertEquals("Devolución rechazada", eventosGuardados.get(0).getDetalle());
    }


    @Test
    void emitirCreaLaCabeceraDeMovimientoQueUsaElModuloDeInventario() {
        existeNota(notaEnEstado(EstadoNotaCredito.BORRADOR, new BigDecimal("2"), true));

        service.emitir(100L);

        ArgumentCaptor<MovimientoInventarioHeader> header =
                ArgumentCaptor.forClass(MovimientoInventarioHeader.class);
        verify(movimientoHeaderRepository).save(header.capture());
        assertEquals(TipoMovimiento.ENTRADA, header.getValue().getTipo());
        assertEquals(bodegaId, header.getValue().getBodegaDestinoId());
        assertNull(header.getValue().getBodegaOrigenId());
        assertEquals(usuarioId, header.getValue().getUsuarioId());
        assertTrue(header.getValue().getObservacion().contains("NC-000001"));
    }

    @Test
    void anularCreaUnaCabeceraDeSalidaParaLaReversa() {
        NotaCredito nc = notaEnEstado(EstadoNotaCredito.EMITIDA, new BigDecimal("2"), true);
        existeNota(nc);
        when(movimientoRepository.findByTenantIdAndNotaCreditoIdAndTipo(
                tenantId, 100L, TipoMovimientoNotaCredito.RECUPERACION))
                .thenReturn(List.of(NotaCreditoMovimiento.builder()
                        .id(1L).tenantId(tenantId).notaCreditoId(100L).notaCreditoDetalleId(200L)
                        .movimientoInventarioId(500L).tipo(TipoMovimientoNotaCredito.RECUPERACION).build()));
        when(movimientoInventarioRepository.findById(500L)).thenReturn(Optional.of(MovimientoInventario.builder()
                .id(500L).tenantId(tenantId).productoId(10L).bodegaId(bodegaId)
                .cantidad(new BigDecimal("2")).tipo(TipoMovimiento.ENTRADA_NOTA_CREDITO).build()));

        service.anular(100L, "Devolución rechazada");

        ArgumentCaptor<MovimientoInventarioHeader> header =
                ArgumentCaptor.forClass(MovimientoInventarioHeader.class);
        verify(movimientoHeaderRepository).save(header.capture());
        assertEquals(TipoMovimiento.SALIDA, header.getValue().getTipo());
        assertEquals(bodegaId, header.getValue().getBodegaOrigenId());
        assertNull(header.getValue().getBodegaDestinoId());
    }

    @Test
    void sinRecuperacionDeInventarioNoSeCreaCabecera() {
        existeNota(notaEnEstado(EstadoNotaCredito.BORRADOR, new BigDecimal("2"), false));

        service.emitir(100L);

        verifyNoInteractions(movimientoHeaderRepository);
    }
    // --- Listado y dashboard -------------------------------------------------

    @Test
    void elListadoValidaLaPaginacionYElRango() {
        assertThrows(IllegalArgumentException.class,
                () -> service.buscar(null, null, null, null, null, null, null, null, null, null, -1, 10));
        assertThrows(IllegalArgumentException.class,
                () -> service.buscar(null, null, null, null, null, null, null, null, null, null, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> service.buscar(null, null, null, null, null, hoy, hoy.minusDays(1), null, null, null, 0, 10));
    }

    @Test
    void elListadoFuncionaSinParametroDeOrdenamiento() {
        // "sort" es opcional en la API y Set.of(...).contains(null) lanza NPE:
        // sin ordenamiento explícito el listado debe caer al orden por defecto.
        when(notaCreditoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        var pagina = service.buscar(null, null, null, null, null, null, null, null, null, null, 0, 10);

        assertEquals(0, pagina.total());
    }

    @Test
    void elDashboardSoloCuentaComoMontoLasNotasEmitidas() {
        NotaCredito emitida = notaEnEstado(EstadoNotaCredito.EMITIDA, new BigDecimal("2"), true);
        emitida.setMontoTotal(new BigDecimal("2380"));
        NotaCredito borrador = notaEnEstado(EstadoNotaCredito.BORRADOR, new BigDecimal("1"), false);
        borrador.setMontoTotal(new BigDecimal("1190"));
        when(notaCreditoRepository.findByTenantIdAndFechaBetweenOrderByFolioAsc(tenantId, hoy.minusDays(30), hoy))
                .thenReturn(List.of(emitida, borrador));

        var dashboard = service.dashboard(hoy.minusDays(30), hoy);

        assertEquals(2, dashboard.cantidad());
        assertEquals(1, dashboard.emitidas());
        assertEquals(1, dashboard.borradores());
        assertEquals(0, dashboard.montoTotalEmitido().compareTo(new BigDecimal("2380")));
        assertEquals(1, dashboard.conRecuperacionInventario());
        assertEquals(1, dashboard.documentosCorregidos());
    }

    // --- Helpers -------------------------------------------------------------

    private NotaCredito notaBase(EstadoNotaCredito estado) {
        return NotaCredito.builder()
                .id(100L).tenantId(tenantId).folio(1).clienteId(5L).usuarioId(usuarioId)
                .estado(estado).tipoCorreccion(TipoCorreccion.CORRIGE_MONTO)
                .fecha(hoy).bodegaId(bodegaId).ventaId(50L)
                .docAsociadoTipo(TipoDocumentoVenta.FACTURA).docAsociadoFolio(1042)
                .docAsociadoFecha(LocalDate.of(2026, 9, 12)).docAsociadoRazon("Devolución parcial")
                .detalle(new ArrayList<>())
                .build();
    }

    private NotaCredito notaEnEstado(EstadoNotaCredito estado, BigDecimal cantidad, boolean recupera) {
        NotaCredito nc = notaBase(estado);
        nc.getDetalle().add(linea(nc, 200L, 10L, 301L, cantidad, recupera));
        return nc;
    }

    private NotaCreditoDetalle linea(NotaCredito nc, Long id, Long productoId, Long ventaDetalleId,
                                     BigDecimal cantidad, boolean recupera) {
        return NotaCreditoDetalle.builder()
                .id(id).notaCredito(nc).productoId(productoId).ventaDetalleId(ventaDetalleId)
                .codigo("SKU-A").descripcion("Bidón 20L")
                .cantidad(cantidad).precioUnitario(new BigDecimal("1000"))
                .descuento(BigDecimal.ZERO).subtotal(cantidad.multiply(new BigDecimal("1000")))
                .recuperaInventario(recupera)
                .build();
    }
}
