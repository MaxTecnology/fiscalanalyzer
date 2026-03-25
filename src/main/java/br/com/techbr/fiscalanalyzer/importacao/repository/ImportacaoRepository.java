package br.com.techbr.fiscalanalyzer.importacao.repository;

import br.com.techbr.fiscalanalyzer.importacao.model.Importacao;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportacaoRepository extends JpaRepository<Importacao, Long> {

    Page<Importacao> findByTenantIdAndEmpresaId(Long tenantId, Long empresaId, Pageable pageable);

    Page<Importacao> findByTenantIdAndEmpresaIdAndStatus(Long tenantId,
                                                         Long empresaId,
                                                         ImportacaoStatus status,
                                                         Pageable pageable);
}
