package cl.slimerp.flujocaja;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flujo-caja")
public class FlujoCajaController {

    private final FlujoCajaService flujoCajaService;

    public FlujoCajaController(FlujoCajaService flujoCajaService) {
        this.flujoCajaService = flujoCajaService;
    }

    @GetMapping("/anio")
    @PreAuthorize("hasAuthority('TESORERIA_VER')")
    public ResumenAnio resumenAnio(@RequestParam int anio) {
        return flujoCajaService.resumenAnio(anio);
    }

    @GetMapping("/mes")
    @PreAuthorize("hasAuthority('TESORERIA_VER')")
    public DetalleMes detalleMes(@RequestParam String mes) {
        return flujoCajaService.detalleMes(mes);
    }
}