package dev.dimi.erp.cadastros.internal.web;

import dev.dimi.erp.cadastros.internal.application.CepLookupClient;
import dev.dimi.erp.cadastros.internal.application.CepNotFoundException;
import dev.dimi.erp.cadastros.internal.web.dto.CepLookupApiResponse;
import dev.dimi.erp.shared.address.Cep;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lookup de CEP via ViaCEP (RN-CAD-015). Reaproveita {@code pessoa.consultar} — o consumo natural é
 * o formulário de endereço de Person/Branch dentro de cadastros, então quem pode consultar pessoa
 * já pode consultar CEP. Evita criar permission nova só para esse endpoint (e o churn de seed
 * global + tenant backfill que isso implicaria).
 *
 * <p>O endpoint aceita CEP com ou sem máscara: {@code 01310-100} ou {@code 01310100} ambos válidos.
 * Validação delegada ao VO {@link Cep}; CEP em formato inválido devolve 400 via {@code
 * GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/cadastros/cep")
@RequiredArgsConstructor
class CepController {

  private final CepLookupClient cepLookupClient;

  @GetMapping("/{cep}")
  @PreAuthorize("hasAuthority('pessoa.consultar')")
  CepLookupApiResponse lookup(@PathVariable("cep") String rawCep) {
    Cep cep = Cep.of(rawCep);
    return cepLookupClient
        .lookup(cep)
        .map(CepLookupApiResponse::from)
        .orElseThrow(() -> new CepNotFoundException(cep));
  }
}
