package dev.dimi.erp.fiscal.internal.adapter.acbr;

import dev.dimi.erp.fiscal.ConsumerType;
import dev.dimi.erp.fiscal.InvoiceDraft;
import dev.dimi.erp.fiscal.InvoiceDraft.DraftItem;
import dev.dimi.erp.fiscal.InvoiceDraft.DraftTax;
import dev.dimi.erp.fiscal.NfeEnvironment;
import dev.dimi.erp.fiscal.internal.adapter.acbr.dto.NfePedidoEmissao;
import dev.dimi.erp.fiscal.internal.domain.TaxType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Constrói o payload {@link NfePedidoEmissao} (POST /nfe no SaaS {@code acbr.api.br}) a partir de
 * um {@link InvoiceDraft}.
 *
 * <p>v1 mapping mínimo: cobre o caminho feliz de NFe modelo 55 intraestadual SP→SP com pagamento à
 * vista, sem ST/FCP/IPI/ISSQN, finalidade normal, modalidade de frete por conta do destinatário.
 * Vários campos (cUF, cMunFG, xNome emitente) ainda estão como placeholder — o ajuste fino vem no
 * smoke-test contra a homologação SEFAZ, quando descobrirmos quais rejeições aparecem antes de
 * persistir BranchFiscalConfig completo (slice 8.6).
 *
 * <p>Ativado apenas quando {@code erp-poc.fiscal.provider=acbr-api}.
 */
@Component
@ConditionalOnProperty(prefix = "erp-poc.fiscal", name = "provider", havingValue = "acbr-api")
public class NfeRequestBuilder {

  /** Constrói o payload completo. v1 mapping mínimo, ajuste fino em smoke-test homologação. */
  public NfePedidoEmissao build(InvoiceDraft draft) {
    Map<String, Object> infNFe = new LinkedHashMap<>();
    infNFe.put("ide", buildIde(draft));
    infNFe.put("emit", buildEmit(draft));
    // ConsumerType v1 = {NORMAL, FINAL} — não há ANONYMOUS no enum atual. Omitimos <dest> apenas
    // quando o draft realmente não trouxe documento do destinatário (venda anônima de balcão).
    if (draft.customerCpfCnpj() != null && !draft.customerCpfCnpj().isBlank()) {
      infNFe.put("dest", buildDest(draft));
    }
    infNFe.put("det", buildDet(draft));
    infNFe.put("total", buildTotal(draft));
    infNFe.put("transp", buildTransp());
    infNFe.put("pag", buildPag(draft));

    String ambiente = draft.environment().name().toLowerCase();
    String referencia = draft.invoiceId().value().toString();
    return new NfePedidoEmissao(infNFe, null, ambiente, referencia);
  }

  private Map<String, Object> buildIde(InvoiceDraft draft) {
    Map<String, Object> ide = new LinkedHashMap<>();
    // TODO: derivar cUF/cMunFG do BranchFiscalConfig (slice 8.6); placeholder SP (35 / 3550308).
    ide.put("cUF", 35);
    ide.put("natOp", "Venda");
    ide.put("serie", Integer.parseInt(draft.series()));
    ide.put("nNF", draft.number());
    ide.put("dhEmi", Instant.now().toString());
    ide.put("tpNF", 1);
    ide.put("idDest", 1);
    ide.put("cMunFG", 3550308);
    ide.put("tpImp", 1);
    ide.put("tpEmis", 1);
    ide.put("tpAmb", draft.environment() == NfeEnvironment.HOMOLOGACAO ? 2 : 1);
    ide.put("finNFe", 1);
    ide.put("indFinal", draft.consumerType() == ConsumerType.FINAL ? 1 : 0);
    ide.put("indPres", 1);
    return ide;
  }

  private Map<String, Object> buildEmit(InvoiceDraft draft) {
    Map<String, Object> emit = new LinkedHashMap<>();
    emit.put("CNPJ", onlyDigits(draft.branchCnpj()));
    // TODO: razão social real do BranchFiscalConfig (slice 8.6).
    emit.put("xNome", "Emitente Placeholder");
    emit.put("CRT", 1);
    return emit;
  }

  private Map<String, Object> buildDest(InvoiceDraft draft) {
    Map<String, Object> dest = new LinkedHashMap<>();
    String doc = onlyDigits(draft.customerCpfCnpj());
    if (doc.length() == 11) {
      dest.put("CPF", doc);
    } else if (doc.length() == 14) {
      dest.put("CNPJ", doc);
    }
    dest.put("xNome", draft.customerName());
    dest.put("indIEDest", 9);
    return dest;
  }

  private List<Map<String, Object>> buildDet(InvoiceDraft draft) {
    List<Map<String, Object>> det = new ArrayList<>();
    int i = 0;
    for (DraftItem item : draft.items()) {
      i++;
      Map<String, Object> line = new LinkedHashMap<>();
      line.put("nItem", i);

      Map<String, Object> prod = new LinkedHashMap<>();
      prod.put("cProd", item.itemCode());
      prod.put("xProd", item.description());
      prod.put("NCM", item.ncm());
      prod.put("CFOP", item.cfop());
      prod.put("uCom", item.unitCommercial());
      prod.put("qCom", item.quantity());
      prod.put("vUnCom", item.unitValue());
      prod.put("vProd", item.grossValue());
      prod.put("uTrib", item.unitFiscal());
      prod.put("qTrib", item.quantity());
      prod.put("vUnTrib", item.unitValue());
      prod.put("indTot", 1);
      line.put("prod", prod);

      line.put("imposto", buildImposto(item.taxes()));
      det.add(line);
    }
    return det;
  }

  /**
   * Agrupa as {@link DraftTax} por {@link TaxType} e monta o sub-mapa de {@code <imposto>} com nós
   * ICMS/PIS/COFINS. v1 mapping mínimo, ajuste fino em smoke-test homologação.
   */
  private Map<String, Object> buildImposto(List<DraftTax> taxes) {
    Map<String, Object> imposto = new LinkedHashMap<>();
    if (taxes == null) {
      return imposto;
    }
    for (DraftTax t : taxes) {
      if (t.tax() == TaxType.ICMS) {
        imposto.put("ICMS", buildIcms(t));
      } else if (t.tax() == TaxType.PIS) {
        imposto.put("PIS", Map.of("PISAliq", buildPisCofinsAliq(t)));
      } else if (t.tax() == TaxType.COFINS) {
        imposto.put("COFINS", Map.of("COFINSAliq", buildPisCofinsAliq(t)));
      }
    }
    return imposto;
  }

  private Map<String, Object> buildIcms(DraftTax t) {
    String code = t.cstCsosn() == null ? "" : t.cstCsosn();
    // CSOSN tem 3 dígitos (Simples Nacional). CST de ICMS clássico ocupa "000", "020", "040",
    // "060".
    // v1: usa "ICMS00" para CST 00 (tributada integralmente) e "ICMSSN102" como genérico Simples.
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("orig", 0);
    body.put("CSOSN", code);
    body.put("CST", code);
    body.put("vBC", t.base());
    body.put("pICMS", t.rate());
    body.put("vICMS", t.value());

    String tag = code.startsWith("00") || code.equals("000") ? "ICMS00" : "ICMSSN102";
    return Map.of(tag, body);
  }

  private Map<String, Object> buildPisCofinsAliq(DraftTax t) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("CST", t.cstCsosn());
    body.put("vBC", t.base());
    body.put("pPIS", t.rate());
    body.put("vPIS", t.value());
    return body;
  }

  private Map<String, Object> buildTotal(InvoiceDraft draft) {
    Map<String, Object> icmsTot = new LinkedHashMap<>();
    icmsTot.put("vBC", 0);
    icmsTot.put("vICMS", 0);
    icmsTot.put("vProd", draft.totalProducts());
    icmsTot.put("vNF", draft.total());
    icmsTot.put("vDesc", draft.totalDiscount());
    return Map.of("ICMSTot", icmsTot);
  }

  private Map<String, Object> buildTransp() {
    Map<String, Object> transp = new LinkedHashMap<>();
    transp.put("modFrete", 9);
    return transp;
  }

  private Map<String, Object> buildPag(InvoiceDraft draft) {
    Map<String, Object> detPag = new LinkedHashMap<>();
    detPag.put("indPag", 0);
    detPag.put("tPag", "99");
    detPag.put("vPag", draft.total());
    return Map.of("detPag", List.of(detPag));
  }

  private String onlyDigits(String s) {
    return s == null ? "" : s.replaceAll("[^0-9]", "");
  }
}
