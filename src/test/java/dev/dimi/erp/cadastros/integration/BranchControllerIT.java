package dev.dimi.erp.cadastros.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
 * IT dos endpoints REST de {@code Branch} ({@code /api/v1/cadastros/branches/**}).
 *
 * <p>Cobre criação com step-up, promoção de matriz (RN-CAD-014), bloqueio de desativação da matriz,
 * upsert de dados fiscais, e proteção por step-up no fluxo fiscal.
 *
 * <p>O tenant já é provisionado com a matriz seed (V4): {@code code=001, name=Matriz, isMain=true}.
 */
class BranchControllerIT extends AbstractWebIntegrationTest {

  private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";
  private static final String CNPJ_VALIDO_1 = "11222333000181";
  private static final String CNPJ_VALIDO_2 = "45283163000167";

  private String tenantSlug;
  private String temporaryPassword;
  private String accessToken;

  @BeforeEach
  void provisionAndLogin() throws Exception {
    tenantSlug = "branch-" + System.nanoTime();
    String body =
        """
        {
          "slug": "%s",
          "displayName": "Branch IT Co",
          "legalName": "Branch IT Limitada",
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
  @DisplayName("GET /branches devolve a matriz seedada (RN-CAD-014)")
  void listaInicialContemMatriz() throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                get("/api/v1/cadastros/branches").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
    assertThat(json.get("totalElements").asInt()).isGreaterThanOrEqualTo(1);
    boolean temMatriz = false;
    for (JsonNode b : json.get("content")) {
      if (b.get("main").asBoolean() && "001".equals(b.get("code").asText())) {
        temMatriz = true;
      }
    }
    assertThat(temMatriz).as("matriz seedada deve aparecer no listing").isTrue();
  }

  @Test
  @DisplayName("POST /branches sem step-up devolve 401 com WWW-Authenticate StepUp")
  void semStepUp_401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/cadastros/branches")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"002\",\"name\":\"Filial Centro\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(
            header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("StepUp")));
  }

  @Test
  @DisplayName("POST /branches com step-up cria filial nova e GET busca pelo id")
  void criar_eBuscar() throws Exception {
    String steppedToken = doStepUp();
    String id = criarFilial(steppedToken, "002", "Filial Centro", false);

    mockMvc
        .perform(
            get("/api/v1/cadastros/branches/" + id)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("002"))
        .andExpect(jsonPath("$.name").value("Filial Centro"))
        .andExpect(jsonPath("$.main").value(false))
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.fiscal").doesNotExist());
  }

  @Test
  @DisplayName("POST /branches com code duplicado devolve 409")
  void codeDuplicado_409() throws Exception {
    String steppedToken = doStepUp();
    mockMvc
        .perform(
            post("/api/v1/cadastros/branches")
                .header("Authorization", "Bearer " + steppedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"001\",\"name\":\"Outra Matriz\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("POST /branches com isMain=true desmarca a matriz anterior (RN-CAD-014)")
  void promoverMatriz_desmarcaAnterior() throws Exception {
    String steppedToken = doStepUp();
    String novaMatrizId = criarFilial(steppedToken, "002", "Nova Matriz", true);

    mockMvc
        .perform(
            get("/api/v1/cadastros/branches/" + novaMatrizId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.main").value(true));

    MvcResult res =
        mockMvc
            .perform(
                get("/api/v1/cadastros/branches")
                    .header("Authorization", "Bearer " + accessToken)
                    .param("size", "100"))
            .andExpect(status().isOk())
            .andReturn();
    int countMain = 0;
    for (JsonNode b :
        objectMapper.readTree(res.getResponse().getContentAsString()).get("content")) {
      if (b.get("main").asBoolean()) countMain++;
    }
    assertThat(countMain).as("apenas 1 matriz por tenant").isEqualTo(1);
  }

  @Test
  @DisplayName("PATCH /branches/{id} atualiza name e isMain promove desmarcando anterior")
  void patch_atualizaEPromove() throws Exception {
    String steppedToken = doStepUp();
    String id = criarFilial(steppedToken, "003", "Antiga", false);

    mockMvc
        .perform(
            patch("/api/v1/cadastros/branches/" + id)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renomeada\",\"isMain\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Renomeada"))
        .andExpect(jsonPath("$.main").value(true));
  }

  @Test
  @DisplayName("PATCH /branches/{id}/desativar bloqueia matriz com 409")
  void desativarMatriz_409() throws Exception {
    String matrizId = idDaMatrizSeed();
    mockMvc
        .perform(
            patch("/api/v1/cadastros/branches/" + matrizId + "/desativar")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"teste\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("PATCH /branches/{id}/desativar e /reativar ciclam filial não-matriz")
  void ciclo_desativar_reativar() throws Exception {
    String steppedToken = doStepUp();
    String id = criarFilial(steppedToken, "004", "Filial Bairro", false);

    mockMvc
        .perform(
            patch("/api/v1/cadastros/branches/" + id + "/desativar")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"fechamento\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));

    mockMvc
        .perform(
            patch("/api/v1/cadastros/branches/" + id + "/reativar")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true));
  }

  @Test
  @DisplayName("PUT /branches/{id}/fiscal sem step-up devolve 401")
  void fiscalSemStepUp_401() throws Exception {
    String steppedToken = doStepUp();
    String id = criarFilial(steppedToken, "005", "Filial Sul", false);

    mockMvc
        .perform(
            put("/api/v1/cadastros/branches/" + id + "/fiscal")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "cnpj": "%s",
                      "taxRegime": "SIMPLES",
                      "csosnDefault": "102"
                    }
                    """
                        .formatted(CNPJ_VALIDO_1)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("PUT /branches/{id}/fiscal cria fiscal e segundo PUT atualiza campos")
  void fiscal_upsert() throws Exception {
    String steppedToken = doStepUp();
    String id = criarFilial(steppedToken, "006", "Filial Norte", false);

    String steppedToken2 = doStepUp();
    mockMvc
        .perform(
            put("/api/v1/cadastros/branches/" + id + "/fiscal")
                .header("Authorization", "Bearer " + steppedToken2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "cnpj": "%s",
                      "taxRegime": "SIMPLES",
                      "csosnDefault": "102",
                      "nfeEnvironment": "HOMOLOGACAO"
                    }
                    """
                        .formatted(CNPJ_VALIDO_1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fiscal.cnpj").value(CNPJ_VALIDO_1))
        .andExpect(jsonPath("$.fiscal.taxRegime").value("SIMPLES"))
        .andExpect(jsonPath("$.fiscal.nfeEnvironment").value("HOMOLOGACAO"));

    String steppedToken3 = doStepUp();
    mockMvc
        .perform(
            put("/api/v1/cadastros/branches/" + id + "/fiscal")
                .header("Authorization", "Bearer " + steppedToken3)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "taxRegime": "PRESUMIDO",
                      "cstIcmsDefault": "00",
                      "nfeEnvironment": "PRODUCAO"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fiscal.cnpj").value(CNPJ_VALIDO_1))
        .andExpect(jsonPath("$.fiscal.taxRegime").value("PRESUMIDO"))
        .andExpect(jsonPath("$.fiscal.nfeEnvironment").value("PRODUCAO"));
  }

  @Test
  @DisplayName("PUT /branches/{id}/fiscal recusa CNPJ diferente do registrado (imutável)")
  void fiscal_cnpjImutavel_409() throws Exception {
    String steppedToken = doStepUp();
    String id = criarFilial(steppedToken, "007", "Filial Leste", false);

    String s2 = doStepUp();
    mockMvc
        .perform(
            put("/api/v1/cadastros/branches/" + id + "/fiscal")
                .header("Authorization", "Bearer " + s2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"cnpj":"%s","taxRegime":"SIMPLES","csosnDefault":"102"}
                    """
                        .formatted(CNPJ_VALIDO_1)))
        .andExpect(status().isOk());

    String s3 = doStepUp();
    mockMvc
        .perform(
            put("/api/v1/cadastros/branches/" + id + "/fiscal")
                .header("Authorization", "Bearer " + s3)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"cnpj":"%s","taxRegime":"SIMPLES","csosnDefault":"102"}
                    """
                        .formatted(CNPJ_VALIDO_2)))
        .andExpect(status().isConflict());
  }

  // --------------------------------------------------------------------------
  // Helpers
  // --------------------------------------------------------------------------

  private String idDaMatrizSeed() throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                get("/api/v1/cadastros/branches")
                    .header("Authorization", "Bearer " + accessToken)
                    .param("size", "100"))
            .andExpect(status().isOk())
            .andReturn();
    for (JsonNode b :
        objectMapper.readTree(res.getResponse().getContentAsString()).get("content")) {
      if (b.get("main").asBoolean() && "001".equals(b.get("code").asText())) {
        return b.get("id").asText();
      }
    }
    throw new IllegalStateException("matriz seed não encontrada");
  }

  private String criarFilial(String token, String code, String name, boolean isMain)
      throws Exception {
    String body =
        """
        {"code":"%s","name":"%s","isMain":%s}
        """
            .formatted(code, name, isMain);
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/cadastros/branches")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
  }

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
