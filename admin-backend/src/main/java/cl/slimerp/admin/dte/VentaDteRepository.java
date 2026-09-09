package cl.slimerp.admin.dte;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Consultas de solo lectura sobre los documentos de venta del ERP. */
public interface VentaDteRepository extends JpaRepository<VentaDte, Long>,
        JpaSpecificationExecutor<VentaDte> {
}