package br.com.techbr.fiscalanalyzer.documento.service;

import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.documento.dto.FiscalDocumentResponse;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocument;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentQueryService {

    private final FiscalDocumentRepository fiscalDocumentRepository;

    public DocumentQueryService(FiscalDocumentRepository fiscalDocumentRepository) {
        this.fiscalDocumentRepository = fiscalDocumentRepository;
    }

    @Transactional(readOnly = true)
    public FiscalDocumentResponse findByAccessKey(Long tenantId, Long empresaId, String accessKey) {
        if (tenantId == null || empresaId == null) {
            throw new ValidationException("tenantId e empresaId sao obrigatorios");
        }
        if (accessKey == null || accessKey.length() != 44) {
            throw new ValidationException("accessKey invalido");
        }
        FiscalDocument doc = fiscalDocumentRepository
                .findByTenantIdAndEmpresaIdAndAccessKey(tenantId, empresaId, accessKey)
                .orElseThrow(() -> new ValidationException("Documento nao encontrado"));

        return new FiscalDocumentResponse(
                doc.getModel(),
                doc.getAccessKey(),
                doc.getIssueDate(),
                doc.getOperationType(),
                doc.getEmitCnpj(),
                doc.getDestCnpj(),
                doc.getTotalAmount(),
                doc.getImportacao() != null ? doc.getImportacao().getId() : null,
                doc.getXmlPath(),
                doc.getXmlHash()
        );
    }
}
