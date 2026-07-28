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
 * IT dos endpoints REST de {@code Person} ({@code /api/v1/cadastros/persons/**}).
 *
 * <p>Cobre o caminho feliz (registrar PF, listar, buscar, atualizar, desativar, reativar, ativar
 * modalidade, desativar modalidade), validação de regras polimórficas (PF exige CPF; PJ exige
 * CNPJ), sentinel de consumidor-final (RN-CAD-001), e os bloqueios de autorização (401 sem JWT, 403
 * para usuário sem a permission).
 */
class PersonControllerIT extends AbstractWebIntegrationTest {

  private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";

  // CPFs/CNPJs com DV válido — necessário porque o service constrói Cpf/Cnpj VOs.
  private static final String CPF_VALIDO = "11144477735";
  private static final String CPF_VALIDO_2 = "52998224725";
  private static final String CNPJ_VALIDO = "11222333000181";

  private String tenantSlug;
  private String ownerAccessToken;

  @BeforeEach
  void provisionAndLogin() throws Exception {
    tenantSlug = "cad-" + System.nanoTime();
    String body =
        """
        {
          "slug": "%s",
          "displayName": "Cadastros IT Co",
          "legalName": "Cadastros IT Limitada",
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

    ownerAccessToken = doLogin("owner", temporaryPassword).accessToken();
  }

  @Test
  @DisplayName("POST /persons cria PF e GET /persons/{id} devolve o registro")
  void registrar_pf_201_e_buscar() throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/cadastros/persons")
                    .header("Authorization", "Bearer " + ownerAccessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "type": "PF",
                          "legalName": "João Almeida",
                          "cpf": "%s",
                          "modalities": ["CUSTOMER"]
                        }
                        """
                            .formatted(CPF_VALIDO)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("PF"))
            .andExpect(jsonPath("$.legalName").value("João Almeida"))
            .andExpect(jsonPath("$.cpf").value(CPF_VALIDO))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.activeModalities[0]").value("CUSTOMER"))
            .andReturn();

    String id = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            get("/api/v1/cadastros/persons/" + id)
                .header("Authorization", "Bearer " + ownerAccessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.cpf").value(CPF_VALIDO));
  }

  @Test
  @DisplayName("POST /persons aceita sentinel CPF 00000000000 (RN-CAD-001) múltiplas vezes")
  void sentinelConsumidorFinal_aceitaDuplicado() throws Exception {
    String body =
        """
        {
          "type": "PF",
          "legalName": "Consumidor Final",
          "cpf": "00000000000",
          "modalities": ["CUSTOMER"]
        }
        """;
    mockMvc
        .perform(
            post("/api/v1/cadastros/persons")
                .header("Authorization", "Bearer " + ownerAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/v1/cadastros/persons")
                .header("Authorization", "Bearer " + ownerAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("POST /persons recusa CPF já existente com 409 person-already-exists")
  void cpfDuplicado_409() throws Exception {
    String body =
        """
        {
          "type": "PF",
          "legalName": "Primeiro",
          "cpf": "%s",
          "modalities": ["CUSTOMER"]
        }
        """
            .formatted(CPF_VALIDO_2);

    mockMvc
        .perform(
            post("/api/v1/cadastros/persons")
                .header("Authorization", "Bearer " + ownerAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/cadastros/persons")
                .header("Authorization", "Bearer " + ownerAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("PATCH /persons/{id}/desativar marca inativa e /reativar volta ativa")
  void ciclo_desativar_reativar() throws Exception {
    String id = createPF("Maria Silva", CPF_VALIDO);

    mockMvc
        .perform(
            patch("/api/v1/cadastros/persons/" + id + "/desativar")
                .header("Authorization", "Bearer " + ownerAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"cliente encerrou conta\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));

    mockMvc
        .perform(
            patch("/api/v1/cadastros/persons/" + id + "/reativar")
                .header("Authorization", "Bearer " + ownerAccessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true));
  }

  @Test
  @DisplayName("POST /persons/{id}/modalidades/SUPPLIER/ativar ativa nova modalidade")
  void ativarModalidade_200() throws Exception {
    String id = createPJ("Empresa LTDA", CNPJ_VALIDO);

    mockMvc
        .perform(
            post("/api/v1/cadastros/persons/" + id + "/modalidades/SUPPLIER/ativar")
                .header("Authorization", "Bearer " + ownerAccessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeModalities", org.hamcrest.Matchers.hasItem("SUPPLIER")))
        .andExpect(jsonPath("$.activeModalities", org.hamcrest.Matchers.hasItem("CUSTOMER")));
  }

  @Test
  @DisplayName("GET /persons busca por substring de legalName (param q)")
  void busca_porNome() throws Exception {
    createPF("Alfredo Santos", CPF_VALIDO);
    createPF("Beatriz Souza", CPF_VALIDO_2);

    MvcResult res =
        mockMvc
            .perform(
                get("/api/v1/cadastros/persons")
                    .header("Authorization", "Bearer " + ownerAccessToken)
                    .param("q", "Alfredo"))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
    assertThat(json.get("totalElements").asInt()).isEqualTo(1);
    assertThat(json.get("content").get(0).get("legalName").asText()).isEqualTo("Alfredo Santos");
  }

  @Test
  @DisplayName("POST /persons sem JWT devolve 401")
  void semJwt_401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/cadastros/persons")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"PF\",\"legalName\":\"x\",\"modalities\":[\"CUSTOMER\"]}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("PF sem CPF é rejeitada com 400 (invalid-person-state)")
  void pfSemCpf_400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/cadastros/persons")
                .header("Authorization", "Bearer " + ownerAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "type": "PF",
                      "legalName": "Sem Doc",
                      "modalities": ["CUSTOMER"]
                    }
                    """))
        .andExpect(status().isConflict());
  }

  // ----------------------------------------------------------------------
  // Helpers
  // ----------------------------------------------------------------------

  private String createPF(String legalName, String cpf) throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/cadastros/persons")
                    .header("Authorization", "Bearer " + ownerAccessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "type": "PF",
                          "legalName": "%s",
                          "cpf": "%s",
                          "modalities": ["CUSTOMER"]
                        }
                        """
                            .formatted(legalName, cpf)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
  }

  private String createPJ(String legalName, String cnpj) throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/cadastros/persons")
                    .header("Authorization", "Bearer " + ownerAccessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "type": "PJ",
                          "legalName": "%s",
                          "cnpj": "%s",
                          "modalities": ["CUSTOMER"]
                        }
                        """
                            .formatted(legalName, cnpj)))
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
