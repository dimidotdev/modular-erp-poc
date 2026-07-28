package dev.dimi.erp.cadastros.internal.web.dto;

import dev.dimi.erp.cadastros.internal.application.CepLookup;

/**
 * Resposta do endpoint {@code GET /api/v1/cadastros/cep/{cep}}. Campos não-CEP são opcionais
 * (ViaCEP pode retornar logradouro em branco para CEPs de unidade / zona rural).
 */
public record CepLookupApiResponse(
    String cep,
    String street,
    String complement,
    String neighborhood,
    String city,
    String state,
    String ibgeCode) {

  public static CepLookupApiResponse from(CepLookup lookup) {
    return new CepLookupApiResponse(
        lookup.cep().value(),
        lookup.street(),
        lookup.complement(),
        lookup.neighborhood(),
        lookup.city(),
        lookup.state(),
        lookup.ibgeCode());
  }
}
