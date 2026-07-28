package dev.dimi.erp.fiscal;

import java.time.Instant;

/**
 * Resultado da chamada de {@link FiscalProvider#authorize(InvoiceDraft)}. Discriminada via sealed
 * interface para que o service força lidar com ambos os casos no {@code switch} sem default.
 *
 * <p>Falhas técnicas (timeout/conexão) NÃO entram aqui — o adapter lança {@link
 * FiscalProviderException}. Apenas resultados semânticos da SEFAZ (autorizado ou rejeitado com
 * código) são modelados aqui. Tratamento de contingência (status {@code CONTINGENCY}) entra no
 * slice 8.6.
 */
public sealed interface AuthorizationResult {

  /**
   * SEFAZ autorizou a emissão.
   *
   * @param fiscalKey chave de 44 dígitos (NFe/NFCe) ou identificador único (NFSe)
   * @param protocol protocolo retornado pela SEFAZ
   * @param authorizedAt instante de autorização registrado pela SEFAZ
   */
  record Authorized(String fiscalKey, String protocol, Instant authorizedAt)
      implements AuthorizationResult {}

  /**
   * SEFAZ rejeitou a emissão por validação semântica (CNPJ inválido, CSOSN incompatível, etc).
   *
   * @param code código de rejeição da SEFAZ (ex: {@code "539"})
   * @param reason descrição humana da rejeição
   */
  record Rejected(String code, String reason) implements AuthorizationResult {}
}
