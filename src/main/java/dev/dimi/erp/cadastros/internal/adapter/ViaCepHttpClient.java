package dev.dimi.erp.cadastros.internal.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.dimi.erp.cadastros.internal.application.CepLookup;
import dev.dimi.erp.cadastros.internal.application.CepLookupClient;
import dev.dimi.erp.cadastros.internal.application.CepLookupUnavailableException;
import dev.dimi.erp.shared.address.Cep;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Adapter HTTP do {@link CepLookupClient} contra <a href="https://viacep.com.br">ViaCEP</a>.
 *
 * <p>ViaCEP é gratuito e sem autenticação, com particularidade: para CEP válido em formato mas
 * inexistente, retorna {@code HTTP 200} com payload {@code {"erro": true}} em vez de 404. Esta
 * implementação traduz isso em {@link Optional#empty()}.
 *
 * <p>Cache Caffeine in-process com TTL configurável: o catálogo de CEPs muda raro (logradouro
 * novo), então 30 minutos é seguro para reduzir carga e latência. Cache cobre tanto hits quanto
 * misses ({@code Optional.empty()}); falhas de upstream NÃO são cacheadas — uma indisponibilidade
 * temporária não deve "grudar" no cliente.
 *
 * <p>Timeouts são curtos por design (RN-CAD-015 trata lookup como opcional): preferimos falhar
 * rápido e deixar o usuário digitar manualmente do que segurar a tela esperando o ViaCEP.
 */
@Component
@Slf4j
@EnableConfigurationProperties(ViaCepProperties.class)
public class ViaCepHttpClient implements CepLookupClient {

  private static final String USER_AGENT = "modular-erp-poc/1.0 (+https://example.test)";

  private final ViaCepProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final Cache<String, Optional<CepLookup>> cache;

  public ViaCepHttpClient(ViaCepProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    this.cache =
        Caffeine.newBuilder()
            .expireAfterWrite(properties.cache().ttl())
            .maximumSize(properties.cache().maximumSize())
            .build();
  }

  @Override
  public Optional<CepLookup> lookup(Cep cep) {
    // Caffeine#get(key, fn) é atômico: na chave perdida invoca a função e cacheia o retorno.
    // Se fetch() lançar CepLookupUnavailableException, Caffeine não cacheia — uma queda
    // temporária do ViaCEP não fica "presa" no cache.
    return cache.get(cep.value(), k -> fetch(cep));
  }

  private Optional<CepLookup> fetch(Cep cep) {
    URI uri = URI.create(properties.baseUrl() + "/" + cep.value() + "/json/");
    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .GET()
            .timeout(properties.requestTimeout())
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build();

    HttpResponse<String> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new CepLookupUnavailableException(cep, "I/O: " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CepLookupUnavailableException(cep, "interrupted", e);
    }

    if (response.statusCode() == 400) {
      // ViaCEP devolve 400 em formato inválido — não deveria chegar aqui se Cep VO validou,
      // mas tratamos como "não encontrado" defensivamente.
      return Optional.empty();
    }
    if (response.statusCode() != 200) {
      log.warn("ViaCEP retornou status {} para CEP {}", response.statusCode(), cep.value());
      throw new CepLookupUnavailableException(cep, "HTTP " + response.statusCode(), null);
    }

    JsonNode root;
    try {
      root = objectMapper.readTree(response.body());
    } catch (IOException e) {
      throw new CepLookupUnavailableException(cep, "JSON inválido: " + e.getMessage(), e);
    }

    if (root.path("erro").asBoolean(false)) {
      return Optional.empty();
    }

    return Optional.of(
        new CepLookup(
            cep,
            textOrNull(root, "logradouro"),
            textOrNull(root, "complemento"),
            textOrNull(root, "bairro"),
            textOrNull(root, "localidade"),
            textOrNull(root, "uf"),
            textOrNull(root, "ibge")));
  }

  private static String textOrNull(JsonNode root, String field) {
    JsonNode node = root.path(field);
    if (node.isMissingNode() || node.isNull()) {
      return null;
    }
    String text = node.asText();
    return text.isBlank() ? null : text;
  }
}
