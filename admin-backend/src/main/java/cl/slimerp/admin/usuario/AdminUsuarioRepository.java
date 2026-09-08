package cl.slimerp.admin.usuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminUsuarioRepository extends JpaRepository<AdminUsuario, Long> {

    Optional<AdminUsuario> findByEmail(String email);

    Optional<AdminUsuario> findByEmailAndActivoTrue(String email);
}