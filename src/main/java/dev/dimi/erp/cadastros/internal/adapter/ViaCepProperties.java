package dev.dimi.erp.cadastros.internal.adapter;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do adapter {@code ViaCepHttpClient}. Bind de {@code erp-poc.cadastros.viacep.*} em
 * {@code application.yml} (e overrides via env vars).
 *
 * <p>{@code cache.ttl} curto demais (< minutos) anula o benefício; longo demais (> dias) faz dados
 * obsoletos persistirem após mudança de logradouro no ViaCEP. {@code maximumSize} bound previne
 * crescimento descontrolado em caso de ataque com CEPs aleatórios.
 */
@ConfigurationProperties(prefix = "erp-poc.cadastros.viacep")
public record ViaCepProperties(
    String baseUrl, Duration connectTimeout, Duration requestTimeout, Cache cache) {

  public record Cache(int maximumSize, Duration ttl) {}
}
