package dev.dimi.erp.cadastros.internal.adapter;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bean {@link SecretClient} criado apenas quando o adapter Key Vault está ativo. Autenticação via
 * {@code DefaultAzureCredential}: tenta Managed Identity → Workload Identity → Azure CLI → IntelliJ
 * — funciona tanto em AKS/Azure App Service quanto em dev com {@code az login}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "erp-poc.cadastros.certificate.store", havingValue = "keyvault")
class KeyVaultClientConfig {

  @Bean
  SecretClient secretClient(
      @Value("${erp-poc.cadastros.certificate.keyvault.endpoint}") String endpoint) {
    return new SecretClientBuilder()
        .vaultUrl(endpoint)
        .credential(new DefaultAzureCredentialBuilder().build())
        .buildClient();
  }
}
