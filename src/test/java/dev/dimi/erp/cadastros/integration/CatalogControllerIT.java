package dev.dimi.erp.cadastros.integration;

import static org.assertj.core.api.Assertions.assertThat;
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
 * IT dos endpoints REST de catálogos auxiliares ({@code /api/v1/cadastros/catalogs/{type}/**}).
 *
 * <p>Cobre listing dos seeds aplicados via V4, criação de entradas custom, bloqueio de mutação de
 * entradas {@code system=true} (RN-CAD-013), e 404 para slug desconhecido.
 */
class CatalogControllerIT extends AbstractWebIntegrationTest {

  private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";

  private String tenantSlug;
  private String accessToken;

  @BeforeEach
  void provisionAndLogin() throws Exception {
    tenantSlug = "catalog-" + System.nanoTime();
    String body =
        """
        {
          "slug": "%s",
          "displayName": "Catalog IT Co",
          "legalName": "Catalog IT Limitada",
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
  @DisplayName("GET /catalogs/segments lista os 5 segmentos seedados")
  void listaSegmentosSeed() throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                get("/api/v1/cadastros/catalogs/segments")
                    .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode arr = objectMapper.readTree(res.getResponse().getContentAsString());
    assertThat(arr.isArray()).isTrue();
    assertThat(arr).hasSize(5);
    boolean encontrouVarejo = false;
    for (JsonNode entry : arr) {
      assertThat(entry.get("system").asBoolean()).isTrue();
      assertThat(entry.get("active").asBoolean()).isTrue();
      if ("VAREJO".equals(entry.get("code").asText())) {
        encontrouVarejo = true;
      }
    }
    assertThat(encontrouVarejo).as("VAREJO deve aparecer no seed").isTrue();
  }

  @Test
  @DisplayName("GET /catalogs/{type} com slug desconhecido devolve 404")
  void slugDesconhecido_404() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/cadastros/catalogs/inexistente")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("POST /catalogs/job-titles cria entrada custom com system=false")
  void criarCustom() throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/cadastros/catalogs/job-titles")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"ANALISTA\",\"name\":\"Analista Fiscal\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("ANALISTA"))
            .andExpect(jsonPath("$.name").value("Analista Fiscal"))
            .andExpect(jsonPath("$.system").value(false))
            .andExpect(jsonPath("$.active").value(true))
            .andReturn();
    String id = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            get("/api/v1/cadastros/catalogs/job-titles/" + id)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("ANALISTA"));
  }

  @Test
  @DisplayName("POST /catalogs/{type} com code duplicado devolve 409")
  void codeDuplicado_409() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/cadastros/catalogs/segments")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"VAREJO\",\"name\":\"Outro Varejo\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("PATCH em entrada system devolve 409 (RN-CAD-013)")
  void renomearSystem_409() throws Exception {
    String varejoId = idDoSegmento("VAREJO");
    mockMvc
        .perform(
            patch("/api/v1/cadastros/catalogs/segments/" + varejoId)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Varejão\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("PATCH /desativar em entrada system devolve 409 (RN-CAD-013)")
  void desativarSystem_409() throws Exception {
    String varejoId = idDoSegmento("VAREJO");
    mockMvc
        .perform(
            patch("/api/v1/cadastros/catalogs/segments/" + varejoId + "/desativar")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("PATCH custom rename, deactivate e reactivate funcionam em sequência")
  void cicloCustom() throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/cadastros/catalogs/departments")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"TI\",\"name\":\"Tecnologia\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    String id = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            patch("/api/v1/cadastros/catalogs/departments/" + id)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Tecnologia da Informação\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Tecnologia da Informação"));

    mockMvc
        .perform(
            patch("/api/v1/cadastros/catalogs/departments/" + id + "/desativar")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));

    mockMvc
        .perform(
            patch("/api/v1/cadastros/catalogs/departments/" + id + "/reativar")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true));
  }

  // --------------------------------------------------------------------------
  // Helpers
  // --------------------------------------------------------------------------

  private String idDoSegmento(String code) throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                get("/api/v1/cadastros/catalogs/segments")
                    .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();
    for (JsonNode entry : objectMapper.readTree(res.getResponse().getContentAsString())) {
      if (code.equals(entry.get("code").asText())) {
        return entry.get("id").asText();
      }
    }
    throw new IllegalStateException("segment " + code + " não encontrado");
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
