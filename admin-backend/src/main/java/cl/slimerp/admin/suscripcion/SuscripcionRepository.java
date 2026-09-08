package cl.slimerp.admin.suscripcion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {

    long countByEstado(String estado);

    long countByEstadoIn(Collection<String> estados);

    long countByEstadoAndFechaVencimientoBetween(String estado, LocalDate desde, LocalDate hasta);

    long countByEstadoAndFechaVencimientoBefore(String estado, LocalDate fecha);

    List<Suscripcion> findByCompanyIdOrderByFechaInicioDesc(Long companyId);

    List<Suscripcion> findByEstadoOrderByFechaVencimientoAsc(String estado);

    @Query("select s.plan.nombre as plan, count(s) as cantidad " +
            "from Suscripcion s where s.estado = :estado group by s.plan.nombre order by s.plan.nombre")
    List<Object[]> contarPorPlan(@Param("estado") String estado);
}