package dev.dimi.erp.cadastros.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.dimi.erp.identidade.internal.web.dto.LoginApiResponse;
import dev.dimi.erp.support.AbstractWebIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * IT dos endpoints REST de {@code Employee} ({@code /api/v1/cadastros/employees/**}).
 *
 * <p>Cobre contratação na matriz seedada, RN-CAD-010 (único ativo por person+branch), update
 * parcial, terminate com step-up obrigatório, bloqueio de edição pós-terminate, e re-contratação
 * após desligamento.
 */
class EmployeeControllerIT extends AbstractWebIntegrationTest {

  private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";
  private static final String CPF_VALIDO_1 = "11144477735";
  private static final String CPF_VALIDO_2 = "52998224725";

  private String tenantSlug;
  private String temporaryPassword;
  private String accessToken;
  private String matrizId;

  @BeforeEach
  void provisionAndLogin() throws Exception {
    tenantSlug = "emp-" + System.nanoTime();
    String body =
        """
        {
          "slug": "%s",
          "displayName": "Employee IT Co",
          "legalName": "Employee IT Limitada",
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
    matrizId = idDaMatrizSeed();
  }

  @Test
  @DisplayName("POST /employees contrata pessoa na matriz e GET busca pelo id")
  void contratar_eBuscar() throws Exception {
    String personId = registrarPessoaFisica("João Operacional", CPF_VALIDO_1);

    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/cadastros/employees")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "personId": "%s",
                          "branchId": "%s",
                          "hiredAt": "%s",
                          "salary": 3500.00,
                          "notes": "experiência 90 dias"
                        }
                        """
                            .formatted(personId, matrizId, LocalDate.now().minusDays(1))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.personId").value(personId))
            .andExpect(jsonPath("$.branchId").value(matrizId))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.terminatedAt").doesNotExist())
            .andReturn();
    String id = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            get("/api/v1/cadastros/employees/" + id)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.salary").value(3500.00));
  }

  @Test
  @DisplayName("POST /employees na mesma person+branch ativa devolve 409 (RN-CAD-010)")
  void duplicado_409() throws Exception {
    String personId = registrarPessoaFisica("Maria Vendedora", CPF_VALIDO_1);
    contratar(personId, matrizId, LocalDate.now().minusDays(10));

    mockMvc
        .perform(
            post("/api/v1/cadastros/employees")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"personId":"%s","branchId":"%s","hiredAt":"%s"}
                    """
                        .formatted(personId, matrizId, LocalDate.now().minusDays(1))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("cadastros.employee-already-hired"));
  }

  @Test
  @DisplayName("PATCH /employees/{id} atualiza campos parciais")
  void patch_atualizaParcial() throws Exception {
    String personId = registrarPessoaFisica("Carlos Caixa", CPF_VALIDO_1);
    String id = contratar(personId, matrizId, LocalDate.now().minusDays(5));

    mockMvc
        .perform(
            patch("/api/v1/cadastros/employees/" + id)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"salary\":4200.50,\"notes\":\"promovido a sênior\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.salary").value(4200.50))
        .andExpect(jsonPath("$.notes").value("promovido a sênior"));
  }

  @Test
  @DisplayName("POST /employees/{id}/terminate sem step-up devolve 401 com WWW-Authenticate StepUp")
  void terminateSemStepUp_401() throws Exception {
    String personId = registrarPessoaFisica("Pedro Vendedor", CPF_VALIDO_1);
    String id = contratar(personId, matrizId, LocalDate.now().minusDays(30));

    mockMvc
        .perform(
            post("/api/v1/cadastros/employees/" + id + "/terminate")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"terminatedAt":"%s","reason":"pediu demissão"}
                    """
                        .formatted(LocalDate.now())))
        .andExpect(status().isUnauthorized())
        .andExpect(
            header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("StepUp")));
  }

  @Test
  @DisplayName("POST /employees/{id}/terminate com step-up encerra vínculo e libera person+branch")
  void terminate_eRecontratar() throws Exception {
    String personId = registrarPessoaFisica("Ana Operadora", CPF_VALIDO_1);
    String id = contratar(personId, matrizId, LocalDate.now().minusDays(60));

    String steppedToken = doStepUp();
    mockMvc
        .perform(
            post("/api/v1/cadastros/employees/" + id + "/terminate")
                .header("Authorization", "Bearer " + steppedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"terminatedAt":"%s","reason":"reorganização"}
                    """
                        .formatted(LocalDate.now())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.terminatedAt").value(LocalDate.now().toString()));

    String novoId = contratar(personId, matrizId, LocalDate.now());
    assertThat(novoId).as("recontratação após terminate deve criar novo vínculo").isNotEqualTo(id);
  }

  @Test
  @DisplayName("PATCH em funcionário desligado devolve 409")
  void editarDesligado_409() throws Exception {
    String personId = registrarPessoaFisica("Bruno Antigo", CPF_VALIDO_1);
    String id = contratar(personId, matrizId, LocalDate.now().minusDays(90));

    String steppedToken = doStepUp();
    mockMvc
        .perform(
            post("/api/v1/cadastros/employees/" + id + "/terminate")
                .header("Authorization", "Bearer " + steppedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"terminatedAt":"%s","reason":"fim de contrato"}
                    """
                        .formatted(LocalDate.now())))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            patch("/api/v1/cadastros/employees/" + id)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"salary\":1000.00}"))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("POST /employees com personId inexistente devolve 409")
  void personInexistente_409() throws Exception {
    String inexistente = "00000000-0000-7000-8000-000000000000";
    mockMvc
        .perform(
            post("/api/v1/cadastros/employees")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"personId":"%s","branchId":"%s","hiredAt":"%s"}
                    """
                        .formatted(inexistente, matrizId, LocalDate.now())))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("Mesma pessoa pode ser contratada em duas filiais distintas")
  void pessoaEmDuasFiliais() throws Exception {
    String personId = registrarPessoaFisica("Diego Multi", CPF_VALIDO_1);
    String segundaFilial = criarFilialSecundaria();

    contratar(personId, matrizId, LocalDate.now().minusDays(5));
    String idSegunda = contratar(personId, segundaFilial, LocalDate.now().minusDays(2));
    assertThat(idSegunda).isNotBlank();
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

  private String criarFilialSecundaria() throws Exception {
    String steppedToken = doStepUp();
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/cadastros/branches")
                    .header("Authorization", "Bearer " + steppedToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"002\",\"name\":\"Filial Bairro\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
  }

  private String registrarPessoaFisica(String legalName, String cpf) throws Exception {
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

  private String contratar(String personId, String branchId, LocalDate hiredAt) throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/cadastros/employees")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"personId":"%s","branchId":"%s","hiredAt":"%s"}
                        """
                            .formatted(personId, branchId, hiredAt)))
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
