package br.com.techbr.fiscalanalyzer.item.repository;

import br.com.techbr.fiscalanalyzer.item.model.FiscalItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FiscalItemRepository extends JpaRepository<FiscalItem, Long> {}
