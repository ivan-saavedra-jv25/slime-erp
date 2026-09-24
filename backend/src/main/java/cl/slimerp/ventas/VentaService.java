package cl.slimerp.ventas;

import cl.slimerp.catalogo.CategoriaFormaPago;
import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.catalogo.FormaPago;
import cl.slimerp.catalogo.FormaPagoRepository;
import cl.slimerp.catalogo.Producto;
import cl.slimerp.catalogo.ProductoRepository;
import cl.slimerp.config.TenantContext;
import cl.slimerp.inventario.Bodega;
import cl.slimerp.inventario.BodegaRepository;
import cl.slimerp.inventario.StockService;
import cl.slimerp.inventario.TipoMovimiento;
import cl.slimerp.notasventa.EstadoNotaVenta;
import cl.slimerp.notasventa.NotaVenta;
import cl.slimerp.notasventa.NotaVentaDocumento;
import cl.slimerp.notasventa.NotaVentaDocumentoRepository;
import cl.slimerp.notasventa.NotaVentaRepository;
import cl.slimerp.notasventa.NumeroNotaVenta;
import cl.slimerp.tesoreria.CuentaPorCobrarService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class VentaService {

    private final VentaRepository ventaRepository;
    private final ClienteRepository clienteRepository;
    private final ProductoRepository productoRepository;
    private final BodegaRepository bodegaRepository;
    private final FormaPagoRepository formaPagoRepository;
    private final StockService stockService;
    private final CuentaPorCobrarService cuentaPorCobrarService;
    private final FolioVentaService folioVentaService;
    private final NotaVentaRepository notaVentaRepository;
    private final NotaVentaDocumentoRepository notaVentaDocumentoRepository;

    public VentaService(VentaRepository ventaRepository, ClienteRepository clienteRepository,
                         ProductoRepository productoRepository, BodegaRepository bodegaRepository,
                         FormaPagoRepository formaPagoRepository, StockService stockService,
                         CuentaPorCobrarService cuentaPorCobrarService, FolioVentaService folioVentaService,
                         NotaVentaRepository notaVentaRepository,
                         NotaVentaDocumentoRepository notaVentaDocumentoRepository) {
        this.ventaRepository = ventaRepository;
        this.clienteRepository = clienteRepository;
        this.productoRepository = productoRepository;
        this.bodegaRepository = bodegaRepository;
        this.formaPagoRepository = formaPagoRepository;
        this.stockService = stockService;
        this.cuentaPorCobrarService = cuentaPorCobrarService;
        this.folioVentaService = folioVentaService;
        this.notaVentaRepository = notaVentaRepository;
        this.notaVentaDocumentoRepository = notaVentaDocumentoRepository;
    }

    @Transactional
    public Venta crear(VentaRequest request) {
        Long tenantId = TenantContext.getTenantId();

        clienteRepository.findByIdAndTenantIdAndActivoTrue(request.clienteId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado: " + request.clienteId()));

        FormaPago formaPago = formaPagoRepository.findByIdAndTenantIdAndActivoTrue(request.formaPagoId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Forma de pago no encontrada: " + request.formaPagoId()));

        Bodega bodega = request.bodegaId() != null
                ? bodegaRepository.findByIdAndTenantIdAndActivoTrue(request.bodegaId(), tenantId)
                        .orElseThrow(() -> new IllegalArgumentException("Bodega no encontrada: " + request.bodegaId()))
                : stockService.bodegaPrincipal(tenantId);

        NotaVenta notaVenta = request.notaVentaId() != null
                ? validarNotaVenta(request.notaVentaId(), request.clienteId(), tenantId)
                : null;

        validarStock(tenantId, bodega.getId(), request.items());

        boolean exento = request.exento();
        String claveFolio = CodigoSiiVenta.etiqueta(request.tipoDocumento(), exento);
        int folio = folioVentaService.siguienteFolio(tenantId, claveFolio);

        Venta venta = Venta.builder()
                .tenantId(tenantId)
                .clienteId(request.clienteId())
                .formaPagoId(request.formaPagoId())
                .bodegaId(bodega.getId())
                .tipoDocumento(request.tipoDocumento())
                .exento(exento)
                .folio(folio)
                .codigoSii(CodigoSiiVenta.codigo(request.tipoDocumento(), exento))
                .notaVentaId(request.notaVentaId())
                .observacion(request.observacion())
                .build();
        venta = ventaRepository.save(venta);

        List<VentaDetalle> detalle = new ArrayList<>();
        BigDecimal sumaDetalle = BigDecimal.ZERO;

        for (VentaRequest.Item item : request.items()) {
            Producto producto = productoRepository.findByIdAndTenantIdAndActivoTrue(item.productoId(), tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + item.productoId()));

            BigDecimal descuentoItem = item.descuento() != null ? item.descuento() : BigDecimal.ZERO;
            BigDecimal subtotalBruto = item.precioUnitario().multiply(item.cantidad());
            if (descuentoItem.signum() < 0 || descuentoItem.compareTo(subtotalBruto) > 0) {
                throw new IllegalArgumentException(
                        "Descuento inválido para \"" + producto.getNombre() + "\": no puede ser negativo ni superar "
                                + "el subtotal de la línea (" + subtotalBruto + ")");
            }
            BigDecimal subtotal = subtotalBruto.subtract(descuentoItem);
            sumaDetalle = sumaDetalle.add(subtotal);

            detalle.add(VentaDetalle.builder()
                    .venta(venta)
                    .productoId(item.productoId())
                    .cantidad(item.cantidad())
                    .precioUnitario(item.precioUnitario())
                    .descuento(descuentoItem)
                    .subtotal(subtotal)
                    .build());
        }

        BigDecimal descuento = request.descuento() != null ? request.descuento() : BigDecimal.ZERO;
        CalculadoraMontosVenta.Montos montos = CalculadoraMontosVenta.calcular(
                request.tipoDocumento(), exento, sumaDetalle.subtract(descuento));

        venta.setDetalle(detalle);
        venta.setDescuento(descuento);
        venta.setMontoNeto(montos.neto());
        venta.setMontoIva(montos.iva());
        venta.setMontoTotal(montos.total());
        venta = ventaRepository.save(venta);

        for (VentaRequest.Item item : request.items()) {
            stockService.sumar(tenantId, item.productoId(), bodega.getId(),
                    item.cantidad().negate(), TipoMovimiento.SALIDA_VENTA, null, venta.getId());
        }

        if (formaPago.getCategoria() == CategoriaFormaPago.CREDITO && venta.getMontoTotal().signum() > 0) {
            cuentaPorCobrarService.crearParaVenta(venta);
        }

        // Enlace hacia abajo en la cadena: la nota de venta pasa a tener esta
        // venta como documento derivado (trazabilidad bidireccional).
        if (notaVenta != null) {
            notaVentaDocumentoRepository.save(NotaVentaDocumento.builder()
                    .tenantId(tenantId)
                    .notaVentaId(notaVenta.getId())
                    .tipoDocumento("VENTA")
                    .documentoId(venta.getId())
                    .numero(request.tipoDocumento() + " N.º " + venta.getFolio())
                    .build());
        }

        return venta;
    }

    private NotaVenta validarNotaVenta(Long notaVentaId, Long clienteId, Long tenantId) {
        NotaVenta notaVenta = notaVentaRepository.findByIdAndTenantId(notaVentaId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Nota de venta no encontrada: " + notaVentaId));
        if (notaVenta.getEstado() == EstadoNotaVenta.CANCELADA) {
            throw new IllegalArgumentException("No se puede registrar la venta: la nota de venta "
                    + NumeroNotaVenta.formatear(notaVenta.getFolio()) + " está cancelada");
        }
        if (!notaVenta.getClienteId().equals(clienteId)) {
            throw new IllegalArgumentException("La nota de venta " + NumeroNotaVenta.formatear(notaVenta.getFolio())
                    + " es de otro cliente");
        }
        return notaVenta;
    }

    private void validarStock(Long tenantId, Long bodegaId, List<VentaRequest.Item> items) {
        for (VentaRequest.Item item : items) {
            Producto producto = productoRepository.findByIdAndTenantIdAndActivoTrue(item.productoId(), tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + item.productoId()));

            BigDecimal disponible = stockService.stockDisponible(tenantId, item.productoId(), bodegaId);
            if (disponible.compareTo(item.cantidad()) < 0) {
                throw new IllegalArgumentException(
                        "Stock insuficiente de \"" + producto.getNombre() + "\": disponible " + disponible
                                + ", solicitado " + item.cantidad());
            }
        }
    }
}
