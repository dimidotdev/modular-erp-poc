package dev.dimi.erp.fiscal.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.fiscal.AuthorizationResult;
import dev.dimi.erp.fiscal.ConsumerType;
import dev.dimi.erp.fiscal.FiscalModel;
import dev.dimi.erp.fiscal.FiscalProvider;
import dev.dimi.erp.fiscal.FiscalProviderException;
import dev.dimi.erp.fiscal.InvoiceDraft;
import dev.dimi.erp.fiscal.InvoiceId;
import dev.dimi.erp.fiscal.InvoiceOperation;
import dev.dimi.erp.fiscal.NfeEnvironment;
import dev.dimi.erp.fiscal.internal.domain.TaxType;
import dev.dimi.erp.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * IT do slice 8.3b: exercita o {@code AcbrApiFiscalProvider} HTTP contra um WireMock simulando o
 * SaaS oficial {@code acbr.api.br}. Cobre o caminho feliz (autorização), rejeição semântica
 * (motivo.codigo 539), falha técnica (5xx) e o retry específico de 401 que invalida o cache de
 * token e tenta uma vez novamente.
 *
 * <p>Estende {@link AbstractIntegrationTest} pra herdar o contexto Spring completo (Postgres via
 * Testcontainers + Flyway). O IT em si só exercita HTTP, mas o contexto do app só sobe limpo com
 * DataSource real — sem isso, repositories JPA falham no boot e nada chega à camada do {@link
 * FiscalProvider}. Em máquinas sem Docker o teste é skipado via {@code @EnabledIfDockerAvailable}.
 */
@TestPropertySource(properties = "erp-poc.fiscal.provider=acbr-api")
class AcbrApiFiscalProviderIT extends AbstractIntegrationTest {

  // @DynamicPropertySource é avaliado no bind das @ConfigurationProperties, antes de qualquer
  // @BeforeAll. O server precisa estar de pé nesse momento, daí a inicialização estática.
  private static final WireMockServer wireMock =
      new WireMockServer(WireMockConfiguration.options().dynamicPort());

  static {
    wireMock.start();
    Runtime.getRuntime().addShutdownHook(new Thread(wireMock::stop));
  }

  @Autowired private FiscalProvider provider;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("erp-poc.fiscal.acbr-api.base-url", () -> "http://localhost:" + wireMock.port());
    registry.add(
        "erp-poc.fiscal.acbr-api.auth.token-url",
        () -> "http://localhost:" + wireMock.port() + "/auth/token");
    registry.add("erp-poc.fiscal.acbr-api.auth.client-id", () -> "test-client");
    registry.add("erp-poc.fiscal.acbr-api.auth.client-secret", () -> "test-secret");
    registry.add("erp-poc.fiscal.acbr-api.timeouts.connect-ms", () -> "2000");
    registry.add("erp-poc.fiscal.acbr-api.timeouts.read-ms", () -> "5000");
  }

  @BeforeEach
  void resetStubs() {
    wireMock.resetAll();
    // Stub default do token endpoint — cada teste sobrescreve quando precisa de cenário diferente.
    wireMock.stubFor(
        post(urlEqualTo("/auth/token"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "access_token": "fake-jwt",
                          "expires_in": 300,
                          "token_type": "Bearer"
                        }
                        """)));
  }

  @Test
  @DisplayName("emissão OK retorna AuthorizationResult.Authorized com chave de 44 dígitos")
  void emissao_OK_retorna_Authorized() {
    wireMock.stubFor(
        post(urlEqualTo("/nfe"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "id": "dfe-1",
                          "ambiente": "homologacao",
                          "status": "AUTORIZADO",
                          "chave": "35260612345678000199550010000000011000000019",
                          "autorizacao": {
                            "protocolo": "135260000123456",
                            "data_autorizacao": "2026-06-02T10:15:30-03:00"
                          }
                        }
                        """)));

    AuthorizationResult result = provider.authorize(sampleDraft());

    assertThat(result).isInstanceOf(AuthorizationResult.Authorized.class);
    AuthorizationResult.Authorized auth = (AuthorizationResult.Authorized) result;
    assertThat(auth.fiscalKey()).hasSize(44);
    assertThat(auth.fiscalKey()).isEqualTo("35260612345678000199550010000000011000000019");
    assertThat(auth.protocol()).isEqualTo("135260000123456");
    assertThat(auth.authorizedAt()).isNotNull();
  }

  @Test
  @DisplayName("emissão REJEITADA retorna AuthorizationResult.Rejected com código 539")
  void emissao_REJEITADA_retorna_Rejected() {
    wireMock.stubFor(
        post(urlEqualTo("/nfe"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "id": "dfe-rej-1",
                          "ambiente": "homologacao",
                          "status": "REJEITADO",
                          "motivo": {
                            "codigo": "539",
                            "descricao": "Duplicidade de NF-e com diferenca na chave de acesso"
                          }
                        }
                        """)));

    AuthorizationResult result = provider.authorize(sampleDraft());

    assertThat(result).isInstanceOf(AuthorizationResult.Rejected.class);
    AuthorizationResult.Rejected rejected = (AuthorizationResult.Rejected) result;
    assertThat(rejected.code()).isEqualTo("539");
    assertThat(rejected.reason()).contains("Duplicidade");
  }

  @Test
  @DisplayName("5xx upstream propaga como FiscalProviderException")
  void emissao_5xx_lanca_FiscalProviderException() {
    wireMock.stubFor(
        post(urlEqualTo("/nfe"))
            .willReturn(
                aResponse()
                    .withStatus(503)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "error": {
                            "code": "upstream_unavailable",
                            "message": "SEFAZ temporariamente indisponível"
                          }
                        }
                        """)));

    assertThatThrownBy(() -> provider.authorize(sampleDraft()))
        .isInstanceOf(FiscalProviderException.class)
        .hasMessageContaining("upstream_unavailable");
  }

  @Test
  @DisplayName("401 invalida cache de token e retenta com token novo")
  void emissao_401_invalida_cache_e_retenta() {
    // 1ª chamada de token → token-A
    wireMock.stubFor(
        post(urlEqualTo("/auth/token"))
            .inScenario("token-refresh")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "access_token": "token-A",
                          "expires_in": 300,
                          "token_type": "Bearer"
                        }
                        """))
            .willSetStateTo("token-A-issued"));

    // 2ª chamada de token (após invalidate) → token-B
    wireMock.stubFor(
        post(urlEqualTo("/auth/token"))
            .inScenario("token-refresh")
            .whenScenarioStateIs("token-A-issued")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "access_token": "token-B",
                          "expires_in": 300,
                          "token_type": "Bearer"
                        }
                        """)));

    // /nfe com Bearer token-A → 401
    wireMock.stubFor(
        post(urlEqualTo("/nfe"))
            .withHeader("Authorization", equalTo("Bearer token-A"))
            .willReturn(
                aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "error": {
                            "code": "token_expired",
                            "message": "Token expirado"
                          }
                        }
                        """)));

    // /nfe com Bearer token-B → 200 AUTORIZADO
    wireMock.stubFor(
        post(urlEqualTo("/nfe"))
            .withHeader("Authorization", equalTo("Bearer token-B"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "id": "dfe-retry-1",
                          "ambiente": "homologacao",
                          "status": "AUTORIZADO",
                          "chave": "35260612345678000199550010000000021000000028",
                          "autorizacao": {
                            "protocolo": "135260000654321",
                            "data_autorizacao": "2026-06-02T10:16:00-03:00"
                          }
                        }
                        """)));

    AuthorizationResult result = provider.authorize(sampleDraft());

    assertThat(result).isInstanceOf(AuthorizationResult.Authorized.class);
    AuthorizationResult.Authorized auth = (AuthorizationResult.Authorized) result;
    assertThat(auth.fiscalKey()).isEqualTo("35260612345678000199550010000000021000000028");

    // Asserta que o /auth/token foi chamado duas vezes (uma inicial + uma após invalidate em 401).
    wireMock.verify(2, postRequestedFor(urlEqualTo("/auth/token")));
  }

  @Test
  @DisplayName("status desconhecido lança FiscalProviderException")
  void emissao_status_desconhecido_lanca() {
    wireMock.stubFor(
        post(urlEqualTo("/nfe"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "id": "dfe-unknown",
                          "ambiente": "homologacao",
                          "status": "INVENTED"
                        }
                        """)));

    assertThatThrownBy(() -> provider.authorize(sampleDraft()))
        .isInstanceOf(FiscalProviderException.class)
        .hasMessageContaining("INVENTED");
  }

  private InvoiceDraft sampleDraft() {
    InvoiceDraft.DraftTax icms =
        new InvoiceDraft.DraftTax(
            TaxType.ICMS,
            "102",
            new BigDecimal("100.00"),
            new BigDecimal("0.00"),
            new BigDecimal("0.00"),
            null,
            null,
            null);
    InvoiceDraft.DraftItem item =
        new InvoiceDraft.DraftItem(
            1,
            "SKU-001",
            "Produto Teste",
            "84713012",
            "5102",
            "0",
            new BigDecimal("1.000"),
            "UN",
            "UN",
            new BigDecimal("100.00"),
            new BigDecimal("100.00"),
            BigDecimal.ZERO,
            new BigDecimal("100.00"),
            List.of(icms));
    return new InvoiceDraft(
        InvoiceId.newId(),
        BranchId.newId(),
        "12345678000199",
        FiscalModel.NFE,
        "1",
        1L,
        NfeEnvironment.HOMOLOGACAO,
        InvoiceOperation.SAIDA,
        ConsumerType.FINAL,
        "12345678909",
        "Cliente Teste",
        new BigDecimal("100.00"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new BigDecimal("100.00"),
        List.of(item));
  }
}
