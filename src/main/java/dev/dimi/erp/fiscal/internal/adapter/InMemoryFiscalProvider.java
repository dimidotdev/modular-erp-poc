package dev.dimi.erp.fiscal.internal.adapter;

import dev.dimi.erp.fiscal.AuthorizationResult;
import dev.dimi.erp.fiscal.FiscalProvider;
import dev.dimi.erp.fiscal.InvoiceDraft;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adapter determinístico de {@link FiscalProvider} usado em testes e ambiente de desenvolvimento
 * local. Autoriza tudo gerando uma chave fiscal sintética de 44 dígitos.
 *
 * <p>Default ativo via {@code erp-poc.fiscal.provider=in-memory} (configurado nos profiles {@code
 * test} e {@code dev} de {@code application-*.yml}). Em produção a propriedade vira {@code acbr} e
 * este bean some — não é registrado.
 *
 * <p>Testes que precisem simular rejeição configuram {@code erp-poc.fiscal.in-memory.force-reject}
 * (não implementado neste slice; entra em ITs futuros via {@code @TestPropertySource}).
 */
@Component
@ConditionalOnProperty(prefix = "erp-poc.fiscal", name = "provider", havingValue = "in-memory")
public class InMemoryFiscalProvider implements FiscalProvider {

  @Override
  public AuthorizationResult authorize(InvoiceDraft draft) {
    String fiscalKey = syntheticKey(draft);
    String protocol = "INMEM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 15);
    return new AuthorizationResult.Authorized(fiscalKey, protocol, Instant.now());
  }

  /**
   * Gera chave fiscal sintética de 44 dígitos. Não passa pela validação real SEFAZ (mod 11 etc) —
   * existe só para popular {@code invoice.fiscal_key} em testes. Adapter real do ACBr produzirá
   * chave válida.
   */
  private String syntheticKey(InvoiceDraft draft) {
    String prefix = "INMEM";
    String cnpj = draft.branchCnpj() == null ? "00000000000000" : draft.branchCnpj();
    String number = String.format("%09d", draft.number());
    String invoiceShort = draft.invoiceId().value().toString().replace("-", "").substring(0, 16);
    String full = prefix + cnpj + number + invoiceShort;
    return full.substring(0, 44);
  }
}
