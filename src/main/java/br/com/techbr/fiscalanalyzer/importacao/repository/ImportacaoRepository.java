package br.com.techbr.fiscalanalyzer.importacao.repository;

import br.com.techbr.fiscalanalyzer.importacao.model.Importacao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportacaoRepository extends JpaRepository<Importacao, Long> {}
