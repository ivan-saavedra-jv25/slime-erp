package cl.slimerp.admin.alerta;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertaService {

    private final AlertaRepository alertaRepository;

    public AlertaService(AlertaRepository alertaRepository) {
        this.alertaRepository = alertaRepository;
    }

    @Transactional
    public Alerta crear(Long companyId, String severity, String tipo, String titulo, String descripcion) {
        return alertaRepository.save(Alerta.builder()
                .companyId(companyId)
                .severity(severity)
                .tipo(tipo)
                .titulo(titulo)
                .descripcion(descripcion)
                .status("OPEN")
                .build());
    }
}