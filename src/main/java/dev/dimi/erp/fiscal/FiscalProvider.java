package dev.dimi.erp.fiscal;

/**
 * Porta hexagonal de integração com o provedor fiscal externo. Há dois adapters:
 *
 * <ul>
 *   <li>{@code InMemoryFiscalProvider}: determinístico, usado em desenvolvimento e testes.
 *   <li>{@code AcbrFiscalProvider}: cliente HTTP para o provedor ACBr.
 * </ul>
 *
 * <p>Esta porta expõe apenas {@link #authorize(InvoiceDraft)}. Outras operações fiscais usam
 * contratos específicos conforme {@code docs/domain/13-fiscal.md}.
 */
public interface FiscalProvider {

  /**
   * Submete o draft à SEFAZ via o adapter ativo.
   *
   * @return {@link AuthorizationResult.Authorized} ou {@link AuthorizationResult.Rejected}
   * @throws FiscalProviderException em falhas técnicas (timeout, conexão, payload inválido). O
   *     caller decide se aciona contingência ou propaga a falha técnica.
   */
  AuthorizationResult authorize(InvoiceDraft draft);
}
