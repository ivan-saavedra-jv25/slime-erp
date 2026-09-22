package cl.slimerp.cotizaciones;

import org.springframework.stereotype.Service;

@Service
public class CotizacionFolioService {

    private final CotizacionFolioContadorRepository contadorRepository;

    public CotizacionFolioService(CotizacionFolioContadorRepository contadorRepository) {
        this.contadorRepository = contadorRepository;
    }

    public int siguienteFolio(Long tenantId) {
        return contadorRepository.siguienteFolio(tenantId);
    }
}
