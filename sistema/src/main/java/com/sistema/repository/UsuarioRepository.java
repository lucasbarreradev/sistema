
package com.sistema.repository;


import com.sistema.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;



@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsername(String username);

    boolean existsByUsername(String username);
    List<Usuario> findAllByTenantIdOrderByUsernameAsc(Long tenantId);
    Optional<Usuario> findByIdAndTenantId(Long id, Long tenantId);
}



