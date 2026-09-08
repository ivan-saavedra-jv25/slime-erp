package cl.slimerp.admin.plan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    List<Plan> findByEstadoOrderById(String estado);
}