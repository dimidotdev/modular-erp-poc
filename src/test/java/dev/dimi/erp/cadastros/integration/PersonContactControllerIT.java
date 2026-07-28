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
 * IT do CRUD de {@code PersonContact} em {@code /api/v1/cadastros/persons/{id}/contatos/**}.
 *
 * <p>Cobre criação, atualização parcial, troca de contato primário (RN-CAD-004) e exclusão.
 */
class PersonContactControllerIT extends AbstractWebIntegrationTest {

  private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";
  private static final String CPF_VALIDO = "11144477735";

  private String tenantSlug;
  private String accessToken;
  private String personId;

  @BeforeEach
  void provisionAndLogin() throws Exception {
    tenantSlug = "ctc-" + System.nanoTime();
    String body =
        """
        {
          "slug": "%s",
          "displayName": "Contact IT Co",
          "legalName": "Contact IT Limitada",
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
  @DisplayName("POST /contatos cria contato e GET /{contactId} devolve o registro")
  void criar_e_buscar() throws Exception {
    String contactId = createContact("joao@example.com", "Email pessoal", true);

    mockMvc
        .perform(
            get("/api/v1/cadastros/persons/" + personId + "/contatos/" + contactId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.value").value("joao@example.com"))
        .andExpect(jsonPath("$.primaryContact").value(true));
  }

  @Test
  @DisplayName("POST /contatos/{c}/primario troca primário e desmarca o anterior (RN-CAD-004)")
  void marcarPrimario_desmarcaAnterior() throws Exception {
    String first = createContact("joao@example.com", "Email", true);
    String second = createContact("+5511999999999", "Celular", false);

    mockMvc
        .perform(
            post("/api/v1/cadastros/persons/" + personId + "/contatos/" + second + "/primario")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.primaryContact").value(true));

    MvcResult res =
        mockMvc
            .perform(
                get("/api/v1/cadastros/persons/" + personId + "/contatos")
                    .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
    for (JsonNode node : json) {
      if (node.get("id").asText().equals(first)) {
        assertThat(node.get("primaryContact").asBoolean()).isFalse();
      } else if (node.get("id").asText().equals(second)) {
        assertThat(node.get("primaryContact").asBoolean()).isTrue();
      }
    }
  }

  @Test
  @DisplayName("PATCH /contatos/{c} aplica alterações parciais (label)")
  void update_parcial() throws Exception {
    String contactId = createContact("joao@example.com", "Email", false);

    mockMvc
        .perform(
            patch("/api/v1/cadastros/persons/" + personId + "/contatos/" + contactId)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"Email do trabalho\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.label").value("Email do trabalho"))
        .andExpect(jsonPath("$.value").value("joao@example.com"));
  }

  @Test
  @DisplayName("DELETE /contatos/{c} devolve 204 e remove o contato")
  void delete_204() throws Exception {
    String contactId = createContact("joao@example.com", "Email", false);

    mockMvc
        .perform(
            delete("/api/v1/cadastros/persons/" + personId + "/contatos/" + contactId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/v1/cadastros/persons/" + personId + "/contatos/" + contactId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound());
  }

  // --------------------------------------------------------------------------
  // Helpers
  // --------------------------------------------------------------------------

  private String createContact(String value, String label, boolean primary) throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/cadastros/persons/" + personId + "/contatos")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "value": "%s",
                          "label": "%s",
                          "primaryContact": %s
                        }
                        """
                            .formatted(value, label, primary)))
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
                          "legalName": "João Contato",
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
