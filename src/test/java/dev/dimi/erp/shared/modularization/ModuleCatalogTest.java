package dev.dimi.erp.shared.modularization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ModuleCatalogTest {

  @Test
  void rotasReaisSaoResolvidasParaOModuloCorreto() {
    Map<String, String> routes =
        Map.of(
            "/api/v1/admin/users", "identidade",
            "/api/v1/cadastros/persons", "cadastros",
            "/api/v1/produtos/brands", "produto",
            "/api/v1/estoque/locations", "estoque",
            "/api/v1/fiscal/invoices", "fiscal");

    routes.forEach(
        (path, expectedModule) ->
            assertThat(ModuleCatalog.resolveByPath(path))
                .get()
                .extracting(ModuleCatalog.ModuleDef::code)
                .isEqualTo(expectedModule));
  }
}
