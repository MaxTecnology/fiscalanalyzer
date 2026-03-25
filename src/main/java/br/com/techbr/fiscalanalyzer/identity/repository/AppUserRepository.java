package br.com.techbr.fiscalanalyzer.identity.repository;

import br.com.techbr.fiscalanalyzer.identity.model.AppUser;
import br.com.techbr.fiscalanalyzer.identity.model.AppUserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    List<AppUser> findAllByOrderByIdAsc();

    List<AppUser> findByStatusOrderByIdAsc(AppUserStatus status);
}
