package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.shared.address.Cep;
import java.util.Optional;

/**
 * Porta para lookup de CEP em provedor externo (ViaCEP no v1).
 *
 * <p>Implementação injetada via {@code internal/adapter/ViaCepHttpClient}. Operação opcional
 * (RN-CAD-015): o cliente pode preencher o endereço manualmente; o lookup só ajuda a UX. Falhas do
 * provedor não devem bloquear o cadastro de endereço.
 */
public interface CepLookupClient {

  /**
   * Consulta o CEP no provedor externo.
   *
   * @return {@link Optional#empty()} se o CEP for válido em formato mas não existir no provedor;
   *     {@link CepLookup} preenchido caso contrário.
   * @throws CepLookupUnavailableException se o provedor estiver indisponível, retornar erro
   *     inesperado ou exceder timeout. O caller decide se rejeita ou permite preenchimento manual.
   */
  Optional<CepLookup> lookup(Cep cep);
}
