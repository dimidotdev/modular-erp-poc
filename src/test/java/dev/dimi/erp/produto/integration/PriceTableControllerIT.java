package dev.dimi.erp.produto.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * IT do fluxo HTTP de tabelas de preço: criação, batch upsert de items e lookup autoritativo com
 * regra de preferência filial > global (RN-PRD-004).
 */
class PriceTableControllerIT extends AbstractWebIntegrationTest {

  private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";

  private String tenantSlug;
  private String accessToken;
  private String branchId;
  private String packagingId;

  @BeforeEach
  void provisionAndSetup() throws Exception {
    tenantSlug = "price-" + System.nanoTime();
    String body =
        """
        {
          "slug": "%s",
          "displayName": "Price IT Co",
          "legalName": "Price IT Limitada",
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

    MvcResult branches =
        mockMvc
            .perform(
                get("/api/v1/cadastros/branches").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode page = objectMapper.readTree(branches.getResponse().getContentAsString());
    branchId = page.get("content").get(0).get("id").asText();
    packagingId = createPackaging("PRICE-A");
  }

  @Test
  @DisplayName("POST /price-tables cria tabela global; PUT items insere; GET quote devolve preço")
  void ciclo_tabela_global() throws Exception {
    String tableId = createPriceTable("Tabela Geral", null, "2026-01-01", null);

    upsertItems(tableId, packagingId, "99.90", "0.0500", true);

    mockMvc
        .perform(
            get("/api/v1/produtos/prices/quote")
                .param("packagingId", packagingId)
                .param("branchId", branchId)
                .param("date", "2026-06-01")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.priceTableId").value(tableId))
        .andExpect(jsonPath("$.salePrice").value(99.90))
        .andExpect(jsonPath("$.discountMax").value(0.05));
  }

  @Test
  @DisplayName("tabela específica de filial vence sobre tabela global (RN-PRD-004)")
  void prefer_filial_sobre_global() throws Exception {
    String globalId = createPriceTable("Global", null, "2026-01-01", null);
    upsertItems(globalId, packagingId, "100.00", "0.0000", true);

    String filialId = createPriceTable("Filial", branchId, "2026-01-01", null);
    upsertItems(filialId, packagingId, "85.00", "0.1000", true);

    mockMvc
        .perform(
            get("/api/v1/produtos/prices/quote")
                .param("packagingId", packagingId)
                .param("branchId", branchId)
                .param("date", "2026-06-01")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.priceTableId").value(filialId))
        .andExpect(jsonPath("$.salePrice").value(85.00));
  }

  @Test
  @DisplayName("GET /prices/quote sem tabela vigente devolve 400 no-price")
  void quote_sem_tabela_400() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/produtos/prices/quote")
                .param("packagingId", packagingId)
                .param("branchId", branchId)
                .param("date", "2026-06-01")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("produto.invalid-product-data"));
  }

  @Test
  @DisplayName("PUT items com replaceAll=true substitui o conjunto inteiro")
  void replaceAll_substitui_tudo() throws Exception {
    String tableId = createPriceTable("Geral", null, "2026-01-01", null);
    String pack2 = createPackaging("PRICE-B");

    upsertItems(tableId, packagingId, "10.00", null, true);
    upsertItems(tableId, pack2, "20.00", null, true); // replaceAll=true descarta o item anterior

    MvcResult list =
        mockMvc
            .perform(
                get("/api/v1/produtos/price-tables/" + tableId + "/items")
                    .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode arr = objectMapper.readTree(list.getResponse().getContentAsString());
    org.assertj.core.api.Assertions.assertThat(arr.size()).isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(arr.get(0).get("packagingId").asText())
        .isEqualTo(pack2);
  }

  @Test
  @DisplayName("GET /price-tables/{id} de tabela inexistente devolve 404")
  void tabela_inexistente_404() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/produtos/price-tables/" + java.util.UUID.randomUUID())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("produto.price-table-not-found"));
  }

  // --------------------------------------------------------------------------
  // Helpers
  // --------------------------------------------------------------------------

  private String createPriceTable(String name, String branchId, String validFrom, String validUntil)
      throws Exception {
    String branchLine = branchId == null ? "" : ", \"branchId\":\"" + branchId + "\"";
    String untilLine = validUntil == null ? "" : ", \"validUntil\":\"" + validUntil + "\"";
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/produtos/price-tables")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"%s","validFrom":"%s"%s%s}
                        """
                            .formatted(name, validFrom, branchLine, untilLine)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
  }

  private void upsertItems(
      String tableId, String packagingId, String salePrice, String discountMax, boolean replaceAll)
      throws Exception {
    String discountLine = discountMax == null ? "" : ", \"discountMax\":" + discountMax;
    mockMvc
        .perform(
            put("/api/v1/produtos/price-tables/" + tableId + "/items")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "items":[{"packagingId":"%s","salePrice":%s%s}],
                      "replaceAll":%s
                    }
                    """
                        .formatted(packagingId, salePrice, discountLine, replaceAll)))
        .andExpect(status().isOk());
  }

  private String createPackaging(String skuRoot) throws Exception {
    MvcResult prod =
        mockMvc
            .perform(
                post("/api/v1/produtos")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "skuRoot":"%s",
                          "name":"Produto %s",
                          "classification":"PRODUTO",
                          "ncm":"61091000"
                        }
                        """
                            .formatted(skuRoot, skuRoot)))
            .andExpect(status().isCreated())
            .andReturn();
    String productId =
        objectMapper.readTree(prod.getResponse().getContentAsString()).get("id").asText();

    MvcResult variantRes =
        mockMvc
            .perform(
                post("/api/v1/produtos/" + productId + "/variants")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"MAIN\",\"name\":\"Main\",\"asDefault\":false}"))
            .andExpect(status().isCreated())
            .andReturn();
    String variantId =
        objectMapper.readTree(variantRes.getResponse().getContentAsString()).get("id").asText();

    MvcResult pkgRes =
        mockMvc
            .perform(
                post("/api/v1/produtos/" + productId + "/variants/" + variantId + "/packagings")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"sku":"%s-PKG","asDefault":false,"conversionFactor":1.0}
                        """
                            .formatted(skuRoot)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(pkgRes.getResponse().getContentAsString()).get("id").asText();
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
