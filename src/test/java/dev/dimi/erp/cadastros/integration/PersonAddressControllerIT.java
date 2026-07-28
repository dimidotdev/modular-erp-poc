package dev.dimi.erp.cadastros.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.dimi.erp.identidade.internal.web.dto.LoginApiResponse;
import dev.dimi.erp.support.AbstractWebIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * IT do CRUD de {@code PersonAddress} em {@code /api/v1/cadastros/persons/{id}/enderecos/**}.
 *
 * <p>Cobre criação, listagem, atualização parcial, troca de padrão (RN-CAD-004), exclusão e
 * validação de CEP.
 */
class PersonAddressControllerIT extends AbstractWebIntegrationTest {

  private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";
  private static final String CPF_VALIDO = "11144477735";

  private String tenantSlug;
  private String accessToken;
  private String personId;

  @BeforeEach
  void provisionAndLogin() throws Exception {
    tenantSlug = "addr-" + System.nanoTime();
    String body =
        """
        {
          "slug": "%s",
          "displayName": "Address IT Co",
          "legalName": "Address IT Limitada",
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
    personId = createPF();
  }

  @Test
  @DisplayName("POST /enderecos cria endereço e GET /{addressId} devolve o registro")
  void criar_e_buscar() throws Exception {
    String addressId = createAddress("01310100", "Av Paulista", "100", true);

    mockMvc
        .perform(
            get("/api/v1/cadastros/persons/" + personId + "/enderecos/" + addressId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cep").value("01310100"))
        .andExpect(jsonPath("$.street").value("Av Paulista"))
        .andExpect(jsonPath("$.defaultAddress").value(true));
  }

  @Test
  @DisplayName("POST /enderecos com CEP em formato mascarado 01310-100 normaliza para 01310100")
  void cepMascarado_normalizado() throws Exception {
    String addressId = createAddress("01310-100", "Av Paulista", "200", false);

    mockMvc
        .perform(
            get("/api/v1/cadastros/persons/" + personId + "/enderecos/" + addressId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cep").value("01310100"));
  }

  @Test
  @DisplayName("POST /enderecos com CEP inválido devolve 400 invalid-cep")
  void cepInvalido_400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/cadastros/persons/" + personId + "/enderecos")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "cep": "123",
                      "street": "rua x",
                      "city": "SP",
                      "state": "SP"
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /enderecos/{a}/padrao troca padrão e desmarca o anterior (RN-CAD-004)")
  void marcarPadrao_desmarcaAnterior() throws Exception {
    String first = createAddress("01310100", "Av Paulista", "100", true);
    String second = createAddress("04094050", "Av Ibirapuera", "200", false);

    mockMvc
        .perform(
            post("/api/v1/cadastros/persons/" + personId + "/enderecos/" + second + "/padrao")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultAddress").value(true));

    MvcResult res =
        mockMvc
            .perform(
                get("/api/v1/cadastros/persons/" + personId + "/enderecos")
                    .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
    for (JsonNode node : json) {
      if (node.get("id").asText().equals(first)) {
        assertThat(node.get("defaultAddress").asBoolean()).isFalse();
      } else if (node.get("id").asText().equals(second)) {
        assertThat(node.get("defaultAddress").asBoolean()).isTrue();
      }
    }
  }

  @Test
  @DisplayName("PATCH /enderecos/{a} aplica alterações parciais (city)")
  void update_parcial() throws Exception {
    String addressId = createAddress("01310100", "Av Paulista", "100", false);

    mockMvc
        .perform(
            patch("/api/v1/cadastros/persons/" + personId + "/enderecos/" + addressId)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"city\":\"Campinas\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.city").value("Campinas"))
        .andExpect(jsonPath("$.street").value("Av Paulista"));
  }

  @Test
  @DisplayName("DELETE /enderecos/{a} devolve 204 e remove o endereço")
  void delete_204() throws Exception {
    String addressId = createAddress("01310100", "Av Paulista", "100", false);

    mockMvc
        .perform(
            delete("/api/v1/cadastros/persons/" + personId + "/enderecos/" + addressId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/v1/cadastros/persons/" + personId + "/enderecos/" + addressId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound());
  }

  // --------------------------------------------------------------------------
  // Helpers
  // --------------------------------------------------------------------------

  private String createAddress(String cep, String street, String number, boolean defaultAddress)
      throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/cadastros/persons/" + personId + "/enderecos")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "cep": "%s",
                          "street": "%s",
                          "number": "%s",
                          "neighborhood": "Bela Vista",
                          "city": "São Paulo",
                          "state": "SP",
                          "country": "BR",
                          "defaultAddress": %s
                        }
                        """
                            .formatted(cep, street, number, defaultAddress)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
  }

  private String createPF() throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/cadastros/persons")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "type": "PF",
                          "legalName": "João Endereço",
                          "cpf": "%s",
                          "modalities": ["CUSTOMER"]
                        }
                        """
                            .formatted(CPF_VALIDO)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
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
