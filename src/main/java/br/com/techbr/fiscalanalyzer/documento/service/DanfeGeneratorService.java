package br.com.techbr.fiscalanalyzer.documento.service;

import br.com.techbr.fiscalanalyzer.common.exception.InfraException;
import br.com.techbr.fiscalanalyzer.common.exception.UnprocessableEntityException;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.swconsultoria.impressao.model.Impressao;
import br.com.swconsultoria.impressao.service.ImpressaoService;
import br.com.swconsultoria.impressao.util.ImpressaoUtil;
import net.sf.jasperreports.engine.JRException;
import org.springframework.stereotype.Service;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

import org.xml.sax.SAXException;

@Service
public class DanfeGeneratorService {

    public byte[] generatePdf(String xmlContent, Short model, String nfceUrlConsulta) {
        if (model == null) {
            throw new ValidationException("model ausente para geracao do DANFE");
        }

        Impressao impressao = switch (model) {
            case 55 -> ImpressaoUtil.impressaoPadraoNFe(xmlContent);
            case 65 -> ImpressaoUtil.impressaoPadraoNFCe(xmlContent, nfceUrlConsulta == null ? "" : nfceUrlConsulta);
            default -> throw new ValidationException("model nao suportado para DANFE: " + model);
        };

        try {
            return ImpressaoService.impressaoPdfByte(impressao);
        } catch (SAXException | ParserConfigurationException e) {
            throw new UnprocessableEntityException("XML invalido para geracao do DANFE");
        } catch (JRException | IOException e) {
            throw new InfraException("Falha ao gerar DANFE", e);
        }
    }
}

