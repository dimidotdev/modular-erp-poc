package dev.dimi.erp.cadastros.internal.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.dimi.erp.cadastros.internal.application.CepLookup;
import dev.dimi.erp.cadastros.internal.application.CepLookupUnavailableException;
import dev.dimi.erp.shared.address.Cep;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ViaCepHttpClientTest {

  private HttpServer server;
  private String baseUrl;

  @BeforeEach
  void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.start();
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/ws";
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  @DisplayName("200 com payload válido devolve CepLookup preenchido")
  void payloadValido() {
    server.createContext(
        "/ws/01310100/json/",
        exchange -> {
          byte[] body =
              ("""
              {
                "cep": "01310-100",
                "logradouro": "Avenida Paulista",
                "complemento": "até 612 - lado par",
                "bairro": "Bela Vista",
                "localidade": "São Paulo",
                "uf": "SP",
                "ibge": "3550308"
              }
              """)
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });

    Optional<CepLookup> result = newClient().lookup(new Cep("01310100"));

    assertThat(result).isPresent();
    CepLookup lookup = result.get();
    assertThat(lookup.cep().value()).isEqualTo("01310100");
    assertThat(lookup.street()).isEqualTo("Avenida Paulista");
    assertThat(lookup.complement()).isEqualTo("até 612 - lado par");
    assertThat(lookup.neighborhood()).isEqualTo("Bela Vista");
    assertThat(lookup.city()).isEqualTo("São Paulo");
    assertThat(lookup.state()).isEqualTo("SP");
    assertThat(lookup.ibgeCode()).isEqualTo("3550308");
  }

  @Test
  @DisplayName("200 com {\"erro\": true} devolve Optional.empty()")
  void cepInexistenteNoViaCep() {
    server.createContext(
        "/ws/99999999/json/",
        exchange -> {
          byte[] body = "{\"erro\": true}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });

    Optional<CepLookup> result = newClient().lookup(new Cep("99999999"));

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("logradouro/bairro em branco viram null no DTO")
  void camposEmBrancoViramNull() {
    server.createContext(
        "/ws/12345678/json/",
        exchange -> {
          byte[] body =
              ("""
              {
                "cep": "12345-678",
                "logradouro": "",
                "complemento": "",
                "bairro": "",
                "localidade": "Cidade",
                "uf": "XX",
                "ibge": ""
              }
              """)
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });

    CepLookup lookup = newClient().lookup(new Cep("12345678")).orElseThrow();

    assertThat(lookup.street()).isNull();
    assertThat(lookup.complement()).isNull();
    assertThat(lookup.neighborhood()).isNull();
    assertThat(lookup.city()).isEqualTo("Cidade");
    assertThat(lookup.state()).isEqualTo("XX");
    assertThat(lookup.ibgeCode()).isNull();
  }

  @Test
  @DisplayName("500 do upstream lança CepLookupUnavailableException")
  void upstream5xx() {
    server.createContext(
        "/ws/01310100/json/",
        exchange -> {
          exchange.sendResponseHeaders(503, -1);
          exchange.close();
        });

    assertThatThrownBy(() -> newClient().lookup(new Cep("01310100")))
        .isInstanceOf(CepLookupUnavailableException.class)
        .hasMessageContaining("HTTP 503");
  }

  @Test
  @DisplayName("cache: segundo lookup do mesmo CEP não bate no upstream")
  void cacheEvitaSegundaChamada() {
    AtomicInteger hits = new AtomicInteger();
    server.createContext(
        "/ws/01310100/json/",
        exchange -> {
          hits.incrementAndGet();
          byte[] body =
              "{\"cep\":\"01310-100\",\"localidade\":\"São Paulo\",\"uf\":\"SP\"}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });

    ViaCepHttpClient client = newClient();
    client.lookup(new Cep("01310100"));
    client.lookup(new Cep("01310100"));
    client.lookup(new Cep("01310100"));

    assertThat(hits.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("cache NÃO grava em falha — próxima chamada tenta novamente")
  void falhaNaoCacheia() {
    AtomicInteger attempts = new AtomicInteger();
    server.createContext(
        "/ws/01310100/json/",
        exchange -> {
          int n = attempts.incrementAndGet();
          if (n == 1) {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
            return;
          }
          byte[] body =
              "{\"cep\":\"01310-100\",\"localidade\":\"São Paulo\",\"uf\":\"SP\"}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });

    ViaCepHttpClient client = newClient();
    assertThatThrownBy(() -> client.lookup(new Cep("01310100")))
        .isInstanceOf(CepLookupUnavailableException.class);

    Optional<CepLookup> retry = client.lookup(new Cep("01310100"));
    assertThat(retry).isPresent();
    assertThat(attempts.get()).isEqualTo(2);
  }

  private ViaCepHttpClient newClient() {
    ViaCepProperties props =
        new ViaCepProperties(
            baseUrl,
            Duration.ofSeconds(2),
            Duration.ofSeconds(4),
            new ViaCepProperties.Cache(100, Duration.ofMinutes(5)));
    return new ViaCepHttpClient(props, new ObjectMapper());
  }
}
