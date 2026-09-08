package cl.slimerp.inventario;

import cl.slimerp.catalogo.ProductoRepository;
import cl.slimerp.config.TenantContext;
import cl.slimerp.tenant.Usuario;
import cl.slimerp.tenant.UsuarioRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/movimientos")
public class MovimientoInventarioController {

    private final MovimientoInventarioService service;
    private final ProductoRepository productoRepository;
    private final BodegaRepository bodegaRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimientoImportService importService;

    public MovimientoInventarioController(MovimientoInventarioService service,
                                           ProductoRepository productoRepository,
                                           BodegaRepository bodegaRepository,
                                           UsuarioRepository usuarioRepository,
                                           MovimientoImportService importService) {
        this.service = service;
        this.productoRepository = productoRepository;
        this.bodegaRepository = bodegaRepository;
        this.usuarioRepository = usuarioRepository;
        this.importService = importService;
    }

    public record MovimientoHistorialResponse(Long id, String tipo,
                                               String bodegaOrigenNombre, String bodegaDestinoNombre,
                                               String usuarioNombre, String observacion,
                                               String fecha,
                                               List<ItemDetalle> items) {}

    public record ItemDetalle(Long productoId, String productoSku, String productoNombre, java.math.BigDecimal cantidad) {}

    @PostMapping
    @PreAuthorize("hasAuthority('MOVIMIENTOS_EDITAR')")
    public ResponseEntity<?> crear(@Valid @RequestBody MovimientoInventarioService.MovimientoRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Long usuarioId = request.responsableId() != null
                ? validarResponsable(tenantId, request.responsableId())
                : resolveUsuarioId(tenantId);

        MovimientoInventarioHeader header = service.crear(tenantId, usuarioId, request);
        return ResponseEntity.ok(Map.of("id", header.getId(), "mensaje", "Movimiento registrado"));
    }

    // Solo resuelve código/código de barra + cantidad contra el catálogo — no crea
    // movimientos. El tipo, la bodega y la observación los define el formulario en
    // pantalla; el resultado se agrega al detalle de la operación que ya está armando.
    @PostMapping(value = "/importar", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('MOVIMIENTOS_EDITAR')")
    public MovimientoImportService.ImportResultado importar(@RequestParam("archivo") MultipartFile archivo) throws IOException {
        Long tenantId = TenantContext.getTenantId();
        return importService.importar(tenantId, archivo.getInputStream());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MOVIMIENTOS_VER')")
    public List<MovimientoHistorialResponse> historial() {
        Long tenantId = TenantContext.getTenantId();
        List<MovimientoInventarioHeader> headers = service.historial(tenantId);

        return headers.stream().map(h -> armarRespuesta(tenantId, h)).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MOVIMIENTOS_VER')")
    public MovimientoHistorialResponse detalle(@PathVariable Long id) {
        Long tenantId = TenantContext.getTenantId();
        MovimientoInventarioHeader h = service.detalle(tenantId, id);
        return armarRespuesta(tenantId, h);
    }

    private MovimientoHistorialResponse armarRespuesta(Long tenantId, MovimientoInventarioHeader h) {
        List<MovimientoInventario> items = service.itemsDelHeader(tenantId, h.getId());
        List<ItemDetalle> detalle = items.stream().map(m -> {
            var prod = productoRepository.findById(m.getProductoId()).orElse(null);
            return new ItemDetalle(
                    m.getProductoId(),
                    prod != null ? prod.getSku() : null,
                    prod != null ? prod.getNombre() : "N/D",
                    m.getCantidad());
        }).toList();

        return new MovimientoHistorialResponse(
                h.getId(),
                h.getTipo().name(),
                resolveBodegaNombre(h.getBodegaOrigenId()),
                resolveBodegaNombre(h.getBodegaDestinoId()),
                resolveUsuarioNombre(h.getUsuarioId()),
                h.getObservacion(),
                h.getFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                detalle);
    }

    // El "responsable" elegido en pantalla debe pertenecer al mismo tenant, igual que
    // cualquier otro id que llegue del frontend.
    private Long validarResponsable(Long tenantId, Long responsableId) {
        return usuarioRepository.findByIdAndTenantId(responsableId, tenantId)
                .map(Usuario::getId)
                .orElseThrow(() -> new IllegalArgumentException("Responsable no encontrado"));
    }

    private Long resolveUsuarioId(Long tenantId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth != null ? auth.getName() : null;
        if (email == null) throw new IllegalStateException("No hay usuario autenticado");
        return usuarioRepository.findByEmailAndTenantId(email, tenantId)
                .map(Usuario::getId)
                .orElseThrow(() -> new IllegalStateException("Usuario no encontrado: " + email));
    }

    private String resolveBodegaNombre(Long bodegaId) {
        if (bodegaId == null) return "—";
        return bodegaRepository.findById(bodegaId).map(Bodega::getNombre).orElse("N/D");
    }

    private String resolveUsuarioNombre(Long usuarioId) {
        if (usuarioId == null) return "—";
        return usuarioRepository.findById(usuarioId).map(Usuario::getNombre).orElse("N/D");
    }
}
