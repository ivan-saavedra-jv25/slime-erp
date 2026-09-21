package cl.slimerp.gastos;

import cl.slimerp.common.PaginaResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/gastos")
public class GastoController {

    private final GastoService gastoService;

    public GastoController(GastoService gastoService) {
        this.gastoService = gastoService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TESORERIA_VER')")
    public PaginaResponse<Gasto> buscar(
            @RequestParam(required = false) Long categoriaGastoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano) {
        return gastoService.buscar(categoriaGastoId, fechaDesde, fechaHasta, q, pagina, tamano);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TESORERIA_VER')")
    public Gasto obtener(@PathVariable Long id) {
        return gastoService.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TESORERIA_EDITAR')")
    public ResponseEntity<Gasto> crear(@Valid @RequestBody GastoRequest request) {
        return ResponseEntity.ok(gastoService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TESORERIA_EDITAR')")
    public Gasto actualizar(@PathVariable Long id, @Valid @RequestBody GastoRequest request) {
        return gastoService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('TESORERIA_EDITAR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        gastoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
