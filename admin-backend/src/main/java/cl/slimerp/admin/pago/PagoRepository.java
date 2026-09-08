package cl.slimerp.admin.pago;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    long countByEstado(String estado);

    long countByEstadoAndCreadoEnBefore(String estado, LocalDateTime fecha);
}