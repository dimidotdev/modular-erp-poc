package dev.dimi.erp.identidade.integration;

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
 * IT dos endpoints administrativos de usuário ({@code /api/v1/admin/users/**}).
 *
 * <p>Cobre: criação como OWNER (caminho feliz), listagem, desativação; e os bloqueios de
 * autorização — 401 sem JWT, 403 quando o token é de usuário sem a permission.
 */
class AdminUsersIT extends AbstractWebIntegrationTest {

  private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";

  private String tenantSlug;
  private String ownerAccessToken;
  private String temporaryPassword;

  @BeforeEach
  void provisionAndLogin() throws Exception {
    tenantSlug = "adm-" + System.nanoTime();
    String body =
        """
        {
          "slug": "%s",
          "displayName": "Admin IT Co",
          "legalName": "Admin IT Limitada",
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

    LoginApiResponse login = doLogin("owner", temporaryPassword);
    ownerAccessToken = login.accessToken();
  }

  @Test
  @DisplayName("POST /admin/users cria usuário com hasAuthority('usuario.criar')")
  void create_pelOwner_201() throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/admin/users")
                    .header("Authorization", "Bearer " + ownerAccessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "username": "vendedor1",
                          "email": "vendedor1@example.com",
                          "fullName": "Vendedor Um",
                          "password": "S3nhaForteParaTeste-X",
                          "roles": ["VENDEDOR"],
                          "mustChangePassword": true
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("vendedor1"))
            .andExpect(jsonPath("$.roles[0]").value("VENDEDOR"))
            .andReturn();

    JsonNode created = objectMapper.readTree(res.getResponse().getContentAsString());
    String id = created.get("id").asText();

    // GET único deve voltar com o mesmo registro.
    mockMvc
        .perform(
            get("/api/v1/admin/users/" + id).header("Authorization", "Bearer " + ownerAccessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("vendedor1"));

    // Listagem inclui owner + vendedor1.
    MvcResult listResult =
        mockMvc
            .perform(
                get("/api/v1/admin/users").header("Authorization", "Bearer " + ownerAccessToken))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode list = objectMapper.readTree(listResult.getResponse().getContentAsString());
    assertThat(list.get("totalElements").asInt()).isGreaterThanOrEqualTo(2);
  }

  @Test
  @DisplayName("PATCH /admin/users/{id}/desativar marca DEACTIVATED")
  void deactivate_pelOwner_200() throws Exception {
    String id = createUser("victor", "victor@example.com", "Victor", "VENDEDOR");
    String steppedUpToken = doStepUp(ownerAccessToken, temporaryPassword);
    mockMvc
        .perform(
            patch("/api/v1/admin/users/" + id + "/desativar")
                .header("Authorization", "Bearer " + steppedUpToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"saiu da empresa\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DEACTIVATED"));
  }

  @Test
  @DisplayName("MANAGER não pode conceder a role OWNER")
  void createOwner_porManager_403() throws Exception {
    createUser("manager", "manager@example.com", "Manager", "MANAGER");
    LoginApiResponse manager = doLogin("manager", "Senha-Forte-V3ndedor!");
    String steppedUpToken = doStepUp(manager.accessToken(), "Senha-Forte-V3ndedor!");

    mockMvc
        .perform(
            post("/api/v1/admin/users")
                .header("Authorization", "Bearer " + steppedUpToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "username": "owner2",
                      "email": "owner2@example.com",
                      "fullName": "Second Owner",
                      "password": "S3nhaForteParaTeste-X",
                      "roles": ["OWNER"]
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("POST /admin/users sem Authorization devolve 401")
  void create_semJwt_401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"x\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("usuário sem permission usuario.criar recebe 403 ao tentar criar")
  void create_porUsuarioSemPermission_403() throws Exception {
    // Owner cria um VENDEDOR (que NÃO tem usuario.criar) com senha conhecida.
    createUser("seller", "seller@example.com", "Seller", "VENDEDOR");

    // Owner suspende para forçar troca? Não — vamos só logar como seller direto.
    // Como o user recém-criado tem mustChangePassword=true mas a política do JWT não bloqueia
    // login por isso (campo só é exposto na response para o front), seller consegue logar.
    LoginApiResponse seller = doLogin("seller", "Senha-Forte-V3ndedor!");

    mockMvc
        .perform(
            post("/api/v1/admin/users")
                .header("Authorization", "Bearer " + seller.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "username": "outro",
                      "email": "outro@example.com",
                      "fullName": "Outro User",
                      "password": "S3nhaForteParaTeste-X",
                      "roles": ["VENDEDOR"]
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  // ----------------------------------------------------------------------
  // Helpers
  // ----------------------------------------------------------------------

  private String createUser(String username, String email, String fullName, String role)
      throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/admin/users")
                    .header("Authorization", "Bearer " + ownerAccessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "username": "%s",
                          "email": "%s",
                          "fullName": "%s",
                          "password": "Senha-Forte-V3ndedor!",
                          "roles": ["%s"]
                        }
                        """
                            .formatted(username, email, fullName, role)))
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

  private String doStepUp(String accessToken, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/step-up")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .get("accessToken")
        .asText();
  }
}
