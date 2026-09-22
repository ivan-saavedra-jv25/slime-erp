package cl.slimerp.notasventa;

import org.springframework.stereotype.Service;

@Service
public class NotaVentaFolioService {

    private final NotaVentaFolioContadorRepository contadorRepository;

    public NotaVentaFolioService(NotaVentaFolioContadorRepository contadorRepository) {
        this.contadorRepository = contadorRepository;
    }

    public int siguienteFolio(Long tenantId) {
        return contadorRepository.siguienteFolio(tenantId);
    }
}