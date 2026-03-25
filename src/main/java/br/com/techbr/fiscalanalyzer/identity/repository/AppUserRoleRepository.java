package br.com.techbr.fiscalanalyzer.identity.repository;

import br.com.techbr.fiscalanalyzer.identity.model.AppUserRole;
import br.com.techbr.fiscalanalyzer.identity.model.AppUserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AppUserRoleRepository extends JpaRepository<AppUserRole, AppUserRoleId> {

    @Query("""
            select r.codigo
              from AppUserRole ur
              join AppRole r on r.id = ur.id.appRoleId
             where ur.id.appUserId = :userId
             order by r.codigo
            """)
    List<String> findRoleCodesByUserId(@Param("userId") Long userId);
}
