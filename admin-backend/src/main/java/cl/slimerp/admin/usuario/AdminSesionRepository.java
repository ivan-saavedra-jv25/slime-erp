package cl.slimerp.admin.usuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminSesionRepository extends JpaRepository<AdminSesion, Long> {

    List<AdminSesion> findByAdminUsuarioIdAndRevocadaEnIsNull(Long adminUsuarioId);

    Optional<AdminSesion> findByTokenHash(String tokenHash);

    long countByAdminUsuarioIdAndRevocadaEnIsNull(Long adminUsuarioId);
}