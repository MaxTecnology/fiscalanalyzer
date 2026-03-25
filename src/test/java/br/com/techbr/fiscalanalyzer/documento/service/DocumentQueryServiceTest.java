package br.com.techbr.fiscalanalyzer.documento.service;

import br.com.techbr.fiscalanalyzer.common.exception.UnprocessableEntityException;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocument;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentRepository;
import br.com.techbr.fiscalanalyzer.identity.service.TenantEmpresaValidationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentQueryServiceTest {

    @Mock
    private FiscalDocumentRepository fiscalDocumentRepository;

    @Mock
    private TenantEmpresaValidationService tenantEmpresaValidationService;

    @InjectMocks
    private DocumentQueryService service;

    @Test
    void findByAccessKey_retornaDocumento_quandoTenantEmpresaAtivos() {
        FiscalDocument document = new FiscalDocument();
        document.setModel((short) 55);
        document.setAccessKey("35191111111111111111550010000000011000000010");
        document.setIssueDate(LocalDate.of(2024, 1, 2));
        document.setOperationType("S");
        document.setEmitCnpj("11111111111111");
        document.setDestCnpj("22222222222222");
        document.setTotalAmount(new BigDecimal("123.45"));
        document.setXmlPath("99/42/abc.xml");
        document.setXmlHash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        when(fiscalDocumentRepository.findByTenantIdAndEmpresaIdAndAccessKey(99L, 42L, document.getAccessKey()))
                .thenReturn(Optional.of(document));

        var response = service.findByAccessKey(99L, 42L, document.getAccessKey());

        assertEquals((short) 55, response.model());
        assertEquals(document.getAccessKey(), response.accessKey());
        assertEquals(LocalDate.of(2024, 1, 2), response.issueDate());
        verify(tenantEmpresaValidationService).validateAtivo(99L, 42L);
    }

    @Test
    void findByAccessKey_retorna422_quandoTenantEmpresaInvalidos() {
        doThrow(new UnprocessableEntityException("empresa nao encontrada"))
                .when(tenantEmpresaValidationService).validateAtivo(99L, 42L);

        assertThrows(UnprocessableEntityException.class,
                () -> service.findByAccessKey(99L, 42L, "35191111111111111111550010000000011000000010"));
        verifyNoInteractions(fiscalDocumentRepository);
    }
}
