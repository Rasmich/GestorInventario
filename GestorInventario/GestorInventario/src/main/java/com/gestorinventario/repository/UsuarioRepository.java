package com.gestorinventario.repository;

import com.gestorinventario.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, Long id);

    // Lectura actual con bloqueo, no COUNT sobre una instantanea antigua de MySQL.
    // Todas las modificaciones adquieren primero estos bloqueos en el mismo orden.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE u.activo = true AND u.rol = com.gestorinventario.model.Rol.ADMIN ORDER BY u.id")
    List<Usuario> findAdministradoresActivosForUpdate();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE u.id = :id")
    Optional<Usuario> findByIdForUpdate(@Param("id") Long id);
}
