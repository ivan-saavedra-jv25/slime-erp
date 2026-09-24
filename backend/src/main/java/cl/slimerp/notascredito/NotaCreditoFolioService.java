package cl.slimerp.notascredito;

import org.springframework.stereotype.Service;

@Service
public class NotaCreditoFolioService {

    private final NotaCreditoFolioContadorRepository contadorRepository;

    public NotaCreditoFolioService(NotaCreditoFolioContadorRepository contadorRepository) {
        this.contadorRepository = contadorRepository;
    }

    public int siguienteFolio(Long tenantId) {
        return contadorRepository.siguienteFolio(tenantId);
    }
}
