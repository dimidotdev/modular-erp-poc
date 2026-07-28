package dev.dimi.erp.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * Avalia se há um Docker daemon acessível antes de habilitar testes anotados com {@link
 * EnabledIfDockerAvailable}. Falha silenciosa ({@code disabled}) quando indisponível — testes são
 * marcados como SKIPPED em vez de causar erro de classe.
 *
 * <p>Localmente, o usuário pode habilitar Docker com {@code sudo usermod -aG docker $USER && newgrp
 * docker}. Em CI (GitHub Actions ubuntu-latest) o Docker já é acessível por padrão.
 */
class DockerAvailableCondition implements ExecutionCondition {

  private static final ConditionEvaluationResult ENABLED =
      ConditionEvaluationResult.enabled("Docker daemon acessível");
  private static final ConditionEvaluationResult DISABLED =
      ConditionEvaluationResult.disabled(
          "Docker daemon não acessível — habilite com `sudo usermod -aG docker $USER && newgrp docker`");

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    try {
      if (DockerClientFactory.instance().isDockerAvailable()) {
        return ENABLED;
      }
    } catch (Throwable ignored) {
      // qualquer erro de conexão = considerado indisponível
    }
    return DISABLED;
  }
}
