package cl.slimerp.notasdebito;

import org.springframework.stereotype.Service;

@Service
public class NotaDebitoFolioService {

    private final NotaDebitoFolioContadorRepository contadorRepository;

    public NotaDebitoFolioService(NotaDebitoFolioContadorRepository contadorRepository) {
        this.contadorRepository = contadorRepository;
    }

    public int siguienteFolio(Long tenantId) {
        return contadorRepository.siguienteFolio(tenantId);
    }
}