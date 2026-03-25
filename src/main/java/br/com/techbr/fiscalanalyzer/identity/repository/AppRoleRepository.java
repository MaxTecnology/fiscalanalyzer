package br.com.techbr.fiscalanalyzer.identity.repository;

import br.com.techbr.fiscalanalyzer.identity.model.AppRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppRoleRepository extends JpaRepository<AppRole, Long> {

    Optional<AppRole> findByCodigo(String codigo);
}
