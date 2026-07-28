package dev.dimi.erp.cadastros.integration;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.dimi.erp.cadastros.internal.application.CepLookup;
import dev.dimi.erp.cadastros.internal.application.CepLookupClient;
import dev.dimi.erp.cadastros.internal.application.CepLookupUnavailableException;
import dev.dimi.erp.identidade.internal.web.dto.LoginApiResponse;
import dev.dimi.erp.shared.address.Cep;
import dev.dimi.erp.support.AbstractWebIntegrationTest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

/**
 * IT do endpoint público de CEP. Substitui o {@link CepLookupClient} real (ViaCEP HTTP) por mock
 * para evitar dependência de rede no CI e poder forçar respostas específicas.
 */
class CepControllerIT extends AbstractWebIntegrationTest {

  private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";

  @MockitoBean private CepLookupClient cepLookupClient;

  private String tenantSlug;
  private String accessToken;

  @BeforeEach
  void provisionAndLogin() throws Exception {
    tenantSlug = "cep-" + System.nanoTime();
    String body =
        """
        {
          "slug": "%s",
          "displayName": "CEP IT Co",
          "legalName": "CEP IT Limitada",
          "cnpj": "%s",
          "plan": "free",
          "ownerUsername": "owner",
          "ownerEmail": "owner@example.com",
          "ownerFullName": "Owner User"
        }
        """
            .formatted(tenantSlug, randomCnpj());

    MvcResult prov =
        mockMvc
            .perform(
                post("/api/v1/platform/tenants")
                    .header("X-Bootstrap-Token", BOOTSTRAP_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    String temporaryPassword =
        objectMapper
            .readTree(prov.getResponse().getContentAsString())
            .get("temporaryPassword")
            .asText();
    accessToken = doLogin("owner", temporaryPassword).accessToken();
  }

  @Test
  @DisplayName("GET /cep/{cep} autenticado com lookup ok devolve 200 + payload")
  void lookup_ok() throws Exception {
    given(cepLookupClient.lookup(argThat(c -> c.value().equals("01310100"))))
        .willReturn(
            Optional.of(
                new CepLookup(
                    new Cep("01310100"),
                    "Avenida Paulista",
                    null,
                    "Bela Vista",
                    "São Paulo",
                    "SP",
                    "3550308")));

    mockMvc
        .perform(
            get("/api/v1/cadastros/cep/01310-100").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cep").value("01310100"))
        .andExpect(jsonPath("$.street").value("Avenida Paulista"))
        .andExpect(jsonPath("$.city").value("São Paulo"))
        .andExpect(jsonPath("$.state").value("SP"))
        .andExpect(jsonPath("$.ibgeCode").value("3550308"));
  }

  @Test
  @DisplayName("CEP que o provedor não conhece devolve 404")
  void lookup_notFound() throws Exception {
    given(cepLookupClient.lookup(argThat(c -> c.value().equals("99999999"))))
        .willReturn(Optional.empty());

    mockMvc
        .perform(
            get("/api/v1/cadastros/cep/99999999").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("cadastros.cep-not-found"));
  }

  @Test
  @DisplayName("provedor indisponível devolve 502")
  void lookup_unavailable() throws Exception {
    Cep cep = new Cep("01310100");
    given(cepLookupClient.lookup(argThat(c -> c.value().equals("01310100"))))
        .willThrow(new CepLookupUnavailableException(cep, "HTTP 503", null));

    mockMvc
        .perform(
            get("/api/v1/cadastros/cep/01310100").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("cadastros.cep-lookup-unavailable"));
  }

  @Test
  @DisplayName("CEP em formato inválido devolve 400")
  void lookup_invalidFormat() throws Exception {
    mockMvc
        .perform(get("/api/v1/cadastros/cep/abc").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("address.invalid-cep"));
  }

  @Test
  @DisplayName("sem Authorization devolve 401")
  void lookup_unauthenticated() throws Exception {
    mockMvc.perform(get("/api/v1/cadastros/cep/01310100")).andExpect(status().isUnauthorized());
  }

  private LoginApiResponse doLogin(String identifier, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"tenantSlug":"%s","identifier":"%s","password":"%s"}
                        """
                            .formatted(tenantSlug, identifier, password)))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readValue(
        result.getResponse().getContentAsString(), LoginApiResponse.class);
  }
}
