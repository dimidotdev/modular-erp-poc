package dev.dimi.erp.cadastros.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.dimi.erp.identidade.internal.web.dto.LoginApiResponse;
import dev.dimi.erp.support.AbstractWebIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * IT do export consolidado de dados pessoais (RN-CAD-016 / LGPD Art. 18 V): {@code GET
 * /persons/{id}/personal-data-export} exige permission {@code lgpd.consultar} + step-up e agrega
 * Person + modalidades + endereços + contatos + vínculos como funcionário.
 */
class PersonalDataExportIT extends AbstractWebIntegrationTest {

  private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";
  private static final String CPF_VALIDO = "11144477735";

  private String tenantSlug;
  private String temporaryPassword;
  private String accessToken;

  @BeforeEach
  void provisionAndLogin() throws Exception {
    tenantSlug = "export-" + System.nanoTime();
    String body =
        """
        {
          "slug": "%s",
          "displayName": "Export IT Co",
          "legalName": "Export IT Limitada",
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
    temporaryPassword =
        objectMapper
            .readTree(prov.getResponse().getContentAsString())
            .get("temporaryPassword")
            .asText();
    accessToken = doLogin("owner", temporaryPassword).accessToken();
  }

  @Test
  @DisplayName("GET sem step-up devolve 401 com WWW-Authenticate StepUp")
  void semStepUp_401() throws Exception {
    String id = createPF();

    mockMvc
        .perform(
            get("/api/v1/cadastros/persons/" + id + "/personal-data-export")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isUnauthorized())
        .andExpect(
            header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("StepUp")));
  }

  @Test
  @DisplayName("GET com step-up devolve payload agregado (person + addresses + contacts)")
  void comStepUp_agregaTudo() throws Exception {
    String id = createPF();
    createAddress(id, "01310100");
    createContact(id, "joao@example.com");
    String steppedToken = doStepUp();

    mockMvc
        .perform(
            get("/api/v1/cadastros/persons/" + id + "/personal-data-export")
                .header("Authorization", "Bearer " + steppedToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.person.id").value(id))
        .andExpect(jsonPath("$.person.legalName").value("João Para Exportar"))
        .andExpect(jsonPath("$.person.cpf").value(CPF_VALIDO))
        .andExpect(jsonPath("$.person.activeModalities[0]").value("CUSTOMER"))
        .andExpect(jsonPath("$.addresses[0].cep").value("01310100"))
        .andExpect(jsonPath("$.addresses[0].street").value("Av Paulista"))
        .andExpect(jsonPath("$.contacts[0].value").value("joao@example.com"))
        .andExpect(jsonPath("$.employments").isArray())
        .andExpect(jsonPath("$.exportedBy").exists())
        .andExpect(jsonPath("$.exportedAt").exists());
  }

  @Test
  @DisplayName("GET para pessoa sem dados relacionados devolve listas vazias")
  void semDados_listasVazias() throws Exception {
    String id = createPF();
    String steppedToken = doStepUp();

    mockMvc
        .perform(
            get("/api/v1/cadastros/persons/" + id + "/personal-data-export")
                .header("Authorization", "Bearer " + steppedToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.addresses").isEmpty())
        .andExpect(jsonPath("$.contacts").isEmpty())
        .andExpect(jsonPath("$.employments").isEmpty());
  }

  @Test
  @DisplayName("GET para pessoa inexistente devolve 404")
  void inexistente_404() throws Exception {
    String steppedToken = doStepUp();

    mockMvc
        .perform(
            get("/api/v1/cadastros/persons/" + UUID.randomUUID() + "/personal-data-export")
                .header("Authorization", "Bearer " + steppedToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("cadastros.person-not-found"));
  }

  // --------------------------------------------------------------------------
  // Helpers
  // --------------------------------------------------------------------------

  private String doStepUp() throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/auth/step-up")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"password\":\"" + temporaryPassword + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper
        .readTree(res.getResponse().getContentAsString())
        .get("accessToken")
        .asText();
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
                          "legalName": "João Para Exportar",
                          "cpf": "%s",
                          "modalities": ["CUSTOMER"]
                        }
                        """
                            .formatted(CPF_VALIDO)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
  }

  private void createAddress(String personId, String cep) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/cadastros/persons/" + personId + "/enderecos")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "cep": "%s",
                      "street": "Av Paulista",
                      "number": "1000",
                      "neighborhood": "Bela Vista",
                      "city": "São Paulo",
                      "state": "SP",
                      "country": "BR",
                      "defaultAddress": true
                    }
                    """
                        .formatted(cep)))
        .andExpect(status().isCreated());
  }

  private void createContact(String personId, String value) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/cadastros/persons/" + personId + "/contatos")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "value": "%s",
                      "label": "Pessoal",
                      "primaryContact": true
                    }
                    """
                        .formatted(value)))
        .andExpect(status().isCreated());
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
