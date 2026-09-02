package cl.slimerp.ventas;

import cl.slimerp.catalogo.ClienteRepository;
import cl.slimerp.catalogo.FormaPagoRepository;
import cl.slimerp.catalogo.Producto;
import cl.slimerp.catalogo.ProductoRepository;
import cl.slimerp.config.TenantContext;
import cl.slimerp.inventario.Bodega;
import cl.slimerp.inventario.BodegaRepository;
import cl.slimerp.inventario.StockService;
import cl.slimerp.inventario.TipoMovimiento;
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

    public VentaService(VentaRepository ventaRepository, ClienteRepository clienteRepository,
                         ProductoRepository productoRepository, BodegaRepository bodegaRepository,
                         FormaPagoRepository formaPagoRepository, StockService stockService) {
        this.ventaRepository = ventaRepository;
        this.clienteRepository = clienteRepository;
        this.productoRepository = productoRepository;
        this.bodegaRepository = bodegaRepository;
        this.formaPagoRepository = formaPagoRepository;
        this.stockService = stockService;
    }

    @Transactional
    public Venta crear(VentaRequest request) {
        Long tenantId = TenantContext.getTenantId();

        clienteRepository.findByIdAndTenantIdAndActivoTrue(request.clienteId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado: " + request.clienteId()));

        formaPagoRepository.findByIdAndTenantIdAndActivoTrue(request.formaPagoId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Forma de pago no encontrada: " + request.formaPagoId()));

        Bodega bodega = request.bodegaId() != null
                ? bodegaRepository.findByIdAndTenantIdAndActivoTrue(request.bodegaId(), tenantId)
                        .orElseThrow(() -> new IllegalArgumentException("Bodega no encontrada: " + request.bodegaId()))
                : stockService.bodegaPrincipal(tenantId);

        validarStock(tenantId, bodega.getId(), request.items());

        boolean exento = request.exento();

        Venta venta = Venta.builder()
                .tenantId(tenantId)
                .clienteId(request.clienteId())
                .formaPagoId(request.formaPagoId())
                .bodegaId(bodega.getId())
                .tipoDocumento(request.tipoDocumento())
                .exento(exento)
                .observacion(request.observacion())
                .build();
        venta = ventaRepository.save(venta);

        List<VentaDetalle> detalle = new ArrayList<>();
        BigDecimal sumaDetalle = BigDecimal.ZERO;

        for (VentaRequest.Item item : request.items()) {
            productoRepository.findByIdAndTenantIdAndActivoTrue(item.productoId(), tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + item.productoId()));

            BigDecimal subtotal = item.precioUnitario().multiply(item.cantidad());
            sumaDetalle = sumaDetalle.add(subtotal);

            detalle.add(VentaDetalle.builder()
                    .venta(venta)
                    .productoId(item.productoId())
                    .cantidad(item.cantidad())
                    .precioUnitario(item.precioUnitario())
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

        return venta;
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
