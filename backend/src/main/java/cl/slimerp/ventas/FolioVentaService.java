package cl.slimerp.ventas;

import org.springframework.stereotype.Service;

@Service
public class FolioVentaService {

    private final FolioVentaContadorRepository folioVentaContadorRepository;

    public FolioVentaService(FolioVentaContadorRepository folioVentaContadorRepository) {
        this.folioVentaContadorRepository = folioVentaContadorRepository;
    }

    // Folio correlativo por tenant + tipo de documento (clave = etiqueta de
    // CodigoSiiVenta), empezando en 1 la primera vez que se emite ese tipo.
    public int siguienteFolio(Long tenantId, String clave) {
        return folioVentaContadorRepository.siguienteFolio(tenantId, clave);
    }
}
