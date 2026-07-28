package dev.dimi.erp.produto.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.dimi.erp.identidade.internal.web.dto.LoginApiResponse;
import dev.dimi.erp.support.AbstractWebIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * IT do CRUD de {@code Product} em {@code /api/v1/produtos/**}: registro com variant/packaging
 * default, listagem, busca, atualização, desativação, e o endpoint de variant/packaging filhos.
 */
class ProductControllerIT extends AbstractWebIntegrationTest {

  private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";

  private String tenantSlug;
  private String accessToken;

  @BeforeEach
  void provisionAndLogin() throws Exception {
    tenantSlug = "prod-" + System.nanoTime();
    String body =
        """
        {
          "slug": "%s",
          "displayName": "Produto IT Co",
          "legalName": "Produto IT Limitada",
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
  @DisplayName("POST /produtos com defaultPackagingSku cria produto + variant + packaging")
  void registrarComPackagingDefault() throws Exception {
    String productId = createProduct("POLO-AZ", "Camiseta polo azul", "POLO-AZ-DEF");

    mockMvc
        .perform(
            get("/api/v1/produtos/" + productId).header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.skuRoot").value("POLO-AZ"))
        .andExpect(jsonPath("$.name").value("Camiseta polo azul"))
        .andExpect(jsonPath("$.classification").value("PRODUTO"))
        .andExpect(jsonPath("$.ncm").value("61091000"))
        .andExpect(jsonPath("$.active").value(true));
  }

  @Test
  @DisplayName("POST /produtos PRODUTO sem NCM devolve 400 ncm-required")
  void semNcm_400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/produtos")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "skuRoot": "NONCM",
                      "name": "Sem NCM",
                      "classification": "PRODUTO"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("produto.invalid-product-data"));
  }

  @Test
  @DisplayName("POST /produtos com skuRoot duplicado devolve 409 duplicate-sku")
  void skuRootDuplicado_409() throws Exception {
    createProduct("DUP-A", "Primeiro", null);

    mockMvc
        .perform(
            post("/api/v1/produtos")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "skuRoot": "DUP-A",
                      "name": "Segundo",
                      "classification": "PRODUTO",
                      "ncm": "61091000"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("produto.duplicate-sku"));
  }

  @Test
  @DisplayName("PATCH /produtos/{id} aplica alterações parciais")
  void update_parcial() throws Exception {
    String productId = createProduct("UP-1", "Original", null);

    mockMvc
        .perform(
            patch("/api/v1/produtos/" + productId)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Atualizado\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Atualizado"))
        .andExpect(jsonPath("$.skuRoot").value("UP-1"));
  }

  @Test
  @DisplayName("POST /produtos/{id}/variants/{variantId}/packagings cria nova SKU")
  void adicionarPackaging() throws Exception {
    String productId = createProduct("VAR-1", "Com variantes", "VAR-1-DEF");
    String variantId = addVariant(productId, "AZ-M", "Azul M", false);

    mockMvc
        .perform(
            post("/api/v1/produtos/" + productId + "/variants/" + variantId + "/packagings")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sku": "VAR-1-AZ-M-CX5",
                      "barcode": "7891234567890",
                      "conversionFactor": 5.0
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.sku").value("VAR-1-AZ-M-CX5"))
        .andExpect(jsonPath("$.barcode").value("7891234567890"))
        .andExpect(jsonPath("$.active").value(true));
  }

  @Test
  @DisplayName("POST nova packaging com barcode duplicado devolve 409 duplicate-barcode")
  void barcodeDuplicado_409() throws Exception {
    String productId = createProduct("BC-1", "Barcode dup", null);
    String variantResp = addVariant(productId, "V1", "V1", true);

    mockMvc
        .perform(
            post("/api/v1/produtos/" + productId + "/variants/" + variantResp + "/packagings")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sku": "BC-1-PRIMEIRO",
                      "barcode": "1111111111111"
                    }
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/produtos/" + productId + "/variants/" + variantResp + "/packagings")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sku": "BC-1-SEGUNDO",
                      "barcode": "1111111111111"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("produto.duplicate-barcode"));
  }

  @Test
  @DisplayName("sem Authorization devolve 401")
  void semAuth_401() throws Exception {
    mockMvc.perform(get("/api/v1/produtos")).andExpect(status().isUnauthorized());
  }

  // --------------------------------------------------------------------------
  // Helpers
  // --------------------------------------------------------------------------

  private String createProduct(String skuRoot, String name, String defaultPackagingSku)
      throws Exception {
    String packagingLine =
        defaultPackagingSku == null
            ? ""
            : ", \"defaultPackagingSku\": \"" + defaultPackagingSku + "\"";
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/produtos")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "skuRoot": "%s",
                          "name": "%s",
                          "classification": "PRODUTO",
                          "ncm": "61091000"%s
                        }
                        """
                            .formatted(skuRoot, name, packagingLine)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
  }

  private String addVariant(String productId, String code, String name, boolean asDefault)
      throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/produtos/" + productId + "/variants")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"code":"%s","name":"%s","asDefault":%s}
                        """
                            .formatted(code, name, asDefault)))
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
