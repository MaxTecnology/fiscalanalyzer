package br.com.techbr.fiscalanalyzer.sped.repository;

import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpedFiscalParticipantRepository extends JpaRepository<SpedFiscalParticipant, Long> {
    void deleteByFileId(Long fileId);
}
