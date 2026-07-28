package dev.dimi.erp.arch;

import dev.dimi.erp.ModularErpPocApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Valida a topologia entre bounded contexts declarada nos {@code package-info.java}.
 *
 * <p>Falha se algum módulo importar outro fora dos {@code allowedDependencies}. Documentação gerada
 * em {@code target/spring-modulith-docs/}.
 */
class ModuleStructureTest {

  private static final ApplicationModules MODULES =
      ApplicationModules.of(ModularErpPocApplication.class);

  @Test
  void valida_topologia_dos_modulos() {
    MODULES.verify();
  }

  @Test
  void escreve_documentacao_modulith() {
    new Documenter(MODULES).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
  }
}
