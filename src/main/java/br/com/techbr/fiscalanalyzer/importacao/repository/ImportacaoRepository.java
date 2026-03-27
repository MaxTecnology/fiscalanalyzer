package br.com.techbr.fiscalanalyzer.importacao.repository;

import br.com.techbr.fiscalanalyzer.importacao.model.Importacao;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ImportacaoRepository extends JpaRepository<Importacao, Long> {

    Page<Importacao> findByTenantIdAndEmpresaId(Long tenantId, Long empresaId, Pageable pageable);

    Page<Importacao> findByTenantIdAndEmpresaIdAndStatus(Long tenantId,
                                                         Long empresaId,
                                                         ImportacaoStatus status,
                                                         Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Importacao i where i.id = :id")
    Optional<Importacao> findByIdForUpdate(@Param("id") Long id);
}
