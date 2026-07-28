package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.shared.address.Cep;

/**
 * Resultado de um lookup de CEP. Campos não-CEP são opcionais ({@code null}) — alguns logradouros
 * (zona rural, CEPs de unidade) vêm com {@code street}/{@code neighborhood} em branco no ViaCEP.
 */
public record CepLookup(
    Cep cep,
    String street,
    String complement,
    String neighborhood,
    String city,
    String state,
    String ibgeCode) {}
