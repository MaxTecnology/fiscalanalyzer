package br.com.techbr.fiscalanalyzer.importacao.service;

import br.com.techbr.fiscalanalyzer.importacao.dto.ImportacaoDetailResponse;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportItemStatus;
import br.com.techbr.fiscalanalyzer.importacao.model.Importacao;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoStatus;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportItemRepository;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportacaoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class ImportacaoReadServiceTest {

    @Test
    void getDetail_retornaContadores() {
        ImportacaoRepository importacaoRepository = Mockito.mock(ImportacaoRepository.class);
        ImportItemRepository importItemRepository = Mockito.mock(ImportItemRepository.class);
        ImportacaoReadService service = new ImportacaoReadService(importacaoRepository, importItemRepository);

        Importacao imp = new Importacao();
        ReflectionTestUtils.setField(imp, "id", 1L);
        imp.setStatus(ImportacaoStatus.EXTRAIDO);
        imp.setArquivoNome("a.zip");
        imp.setArquivoHash("hash");
        ReflectionTestUtils.setField(imp, "createdAt", Instant.now());
        ReflectionTestUtils.setField(imp, "updatedAt", Instant.now());

        when(importacaoRepository.findById(1L)).thenReturn(Optional.of(imp));
        when(importItemRepository.countByImportacaoId(1L)).thenReturn(5L);
        when(importItemRepository.countByStatus(1L)).thenReturn(List.of(
                new StatusCount(ImportItemStatus.PARSEADO, 3),
                new StatusCount(ImportItemStatus.FALHA_PARSE, 2)
        ));

        ImportacaoDetailResponse response = service.getDetail(1L);
        assertEquals(5L, response.totalItems());
        assertEquals(2, response.statusCounts().size());
    }

    private record StatusCount(ImportItemStatus status, long count)
            implements ImportItemRepository.StatusCountProjection {
        @Override public ImportItemStatus getStatus() { return status; }
        @Override public long getCount() { return count; }
    }
}
