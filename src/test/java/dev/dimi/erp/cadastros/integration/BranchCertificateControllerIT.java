package dev.dimi.erp.cadastros.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.dimi.erp.identidade.internal.web.dto.LoginApiResponse;
import dev.dimi.erp.support.AbstractWebIntegrationTest;
import dev.dimi.erp.support.TestPkcs12Generator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/**
 * IT dos endpoints {@code PUT/DELETE /api/v1/cadastros/branches/{id}/certificate} (slice 3.5,
 * RN-CAD-007/008). Usa o adapter {@code LocalFsCertificateMaterialStore} via configuração de teste
 * — nenhum byte sai para Key Vault.
 */
class BranchCertificateControllerIT extends AbstractWebIntegrationTest {

  private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";
  private static final String CNPJ_DA_FILIAL = "11222333000181";
  private static final String OUTRO_CNPJ = "45283163000167";

  private String tenantSlug;
  private String temporaryPassword;
  private String accessToken;

  @BeforeEach
  void provisionAndLogin() throws Exception {
    tenantSlug = "cert-" + System.nanoTime();
    String body =
        """
        {
          "slug": "%s",
          "displayName": "Cert IT Co",
          "legalName": "Cert IT Limitada",
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
  @DisplayName("PUT /certificate sem step-up devolve 401")
  void semStepUp_401() throws Exception {
    String branchId = idDaMatrizSeed();
    TestPkcs12Generator.Pfx pfx =
        TestPkcs12Generator.generate(
            "Cert IT Co",
            CNPJ_DA_FILIAL,
            Instant.now().minus(1, ChronoUnit.DAYS),
            Instant.now().plus(365, ChronoUnit.DAYS),
            "senha123");
    mockMvc
        .perform(
            multipart(HttpMethod.PUT, "/api/v1/cadastros/branches/" + branchId + "/certificate")
                .file(new MockMultipartFile("pfx", "cert.pfx", "application/x-pkcs12", pfx.bytes()))
                .param("password", pfx.password())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("PUT /certificate sem branch_fiscal devolve 409 missing-fiscal")
  void semFiscal_409() throws Exception {
    String branchId = idDaMatrizSeed();
    TestPkcs12Generator.Pfx pfx =
        TestPkcs12Generator.generate(
            "Cert IT Co",
            CNPJ_DA_FILIAL,
            Instant.now().minus(1, ChronoUnit.DAYS),
            Instant.now().plus(365, ChronoUnit.DAYS),
            "senha123");
    String steppedToken = doStepUp();
    mockMvc
        .perform(
            multipart(HttpMethod.PUT, "/api/v1/cadastros/branches/" + branchId + "/certificate")
                .file(new MockMultipartFile("pfx", "cert.pfx", "application/x-pkcs12", pfx.bytes()))
                .param("password", pfx.password())
                .header("Authorization", "Bearer " + steppedToken))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("cadastros.invalid-branch-state"))
        .andExpect(jsonPath("$.reason").value("missing-fiscal"));
  }

  @Test
  @DisplayName("PUT /certificate com senha errada devolve 422 invalid-pfx-password")
  void senhaErrada_422() throws Exception {
    String branchId = idDaMatrizSeed();
    instalarFiscalNaMatriz(branchId, CNPJ_DA_FILIAL);
    TestPkcs12Generator.Pfx pfx =
        TestPkcs12Generator.generate(
            "Cert IT Co",
            CNPJ_DA_FILIAL,
            Instant.now().minus(1, ChronoUnit.DAYS),
            Instant.now().plus(365, ChronoUnit.DAYS),
            "senha-correta");
    String steppedToken = doStepUp();
    mockMvc
        .perform(
            multipart(HttpMethod.PUT, "/api/v1/cadastros/branches/" + branchId + "/certificate")
                .file(new MockMultipartFile("pfx", "cert.pfx", "application/x-pkcs12", pfx.bytes()))
                .param("password", "senha-errada")
                .header("Authorization", "Bearer " + steppedToken))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("cadastros.invalid-pfx-password"));
  }

  @Test
  @DisplayName("PUT /certificate com CNPJ diferente da filial devolve 422")
  void cnpjMismatch_422() throws Exception {
    String branchId = idDaMatrizSeed();
    instalarFiscalNaMatriz(branchId, CNPJ_DA_FILIAL);
    TestPkcs12Generator.Pfx pfx =
        TestPkcs12Generator.generate(
            "Outra Empresa",
            OUTRO_CNPJ,
            Instant.now().minus(1, ChronoUnit.DAYS),
            Instant.now().plus(365, ChronoUnit.DAYS),
            "senha123");
    String steppedToken = doStepUp();
    mockMvc
        .perform(
            multipart(HttpMethod.PUT, "/api/v1/cadastros/branches/" + branchId + "/certificate")
                .file(new MockMultipartFile("pfx", "cert.pfx", "application/x-pkcs12", pfx.bytes()))
                .param("password", pfx.password())
                .header("Authorization", "Bearer " + steppedToken))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("cadastros.certificate-cnpj-mismatch"))
        .andExpect(jsonPath("$.certificateCnpj").value(OUTRO_CNPJ))
        .andExpect(jsonPath("$.branchCnpj").value(CNPJ_DA_FILIAL));
  }

  @Test
  @DisplayName("PUT /certificate válido instala, GET expõe metadados, DELETE remove")
  void cicloCompleto() throws Exception {
    String branchId = idDaMatrizSeed();
    instalarFiscalNaMatriz(branchId, CNPJ_DA_FILIAL);

    Instant notBefore = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    Instant notAfter = Instant.now().plus(365, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    TestPkcs12Generator.Pfx pfx =
        TestPkcs12Generator.generate("Cert IT Co", CNPJ_DA_FILIAL, notBefore, notAfter, "senha123");

    String steppedToken = doStepUp();
    mockMvc
        .perform(
            multipart(HttpMethod.PUT, "/api/v1/cadastros/branches/" + branchId + "/certificate")
                .file(new MockMultipartFile("pfx", "cert.pfx", "application/x-pkcs12", pfx.bytes()))
                .param("password", pfx.password())
                .header("Authorization", "Bearer " + steppedToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fiscal.certificate.subjectCnpj").value(CNPJ_DA_FILIAL))
        .andExpect(jsonPath("$.fiscal.certificate.thumbprint").isString());

    MvcResult res =
        mockMvc
            .perform(
                get("/api/v1/cadastros/branches/" + branchId)
                    .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode cert =
        objectMapper
            .readTree(res.getResponse().getContentAsString())
            .get("fiscal")
            .get("certificate");
    assertThat(cert.get("subjectCnpj").asText()).isEqualTo(CNPJ_DA_FILIAL);
    assertThat(cert.get("thumbprint").asText()).hasSize(40);

    String steppedToken2 = doStepUp();
    mockMvc
        .perform(
            delete("/api/v1/cadastros/branches/" + branchId + "/certificate")
                .header("Authorization", "Bearer " + steppedToken2))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fiscal.certificate").doesNotExist());
  }

  @Test
  @DisplayName("PUT /certificate vencido devolve 409 expired-certificate")
  void certificadoVencido_409() throws Exception {
    String branchId = idDaMatrizSeed();
    instalarFiscalNaMatriz(branchId, CNPJ_DA_FILIAL);
    TestPkcs12Generator.Pfx pfx =
        TestPkcs12Generator.generate(
            "Cert IT Co",
            CNPJ_DA_FILIAL,
            Instant.now().minus(400, ChronoUnit.DAYS),
            Instant.now().minus(10, ChronoUnit.DAYS),
            "senha123");
    String steppedToken = doStepUp();
    mockMvc
        .perform(
            multipart(HttpMethod.PUT, "/api/v1/cadastros/branches/" + branchId + "/certificate")
                .file(new MockMultipartFile("pfx", "cert.pfx", "application/x-pkcs12", pfx.bytes()))
                .param("password", pfx.password())
                .header("Authorization", "Bearer " + steppedToken))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.reason").value("expired-certificate"));
  }

  // --------------------------------------------------------------------------
  // Helpers
  // --------------------------------------------------------------------------

  private void instalarFiscalNaMatriz(String branchId, String cnpj) throws Exception {
    String steppedToken = doStepUp();
    mockMvc
        .perform(
            put("/api/v1/cadastros/branches/" + branchId + "/fiscal")
                .header("Authorization", "Bearer " + steppedToken)
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
                        .formatted(cnpj)))
        .andExpect(status().isOk());
  }

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
