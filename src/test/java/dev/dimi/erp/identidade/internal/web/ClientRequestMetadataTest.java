package dev.dimi.erp.identidade.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientRequestMetadataTest {

  @Test
  void forwardedForForjadoEhIgnorado() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");
    request.addHeader("X-Forwarded-For", "203.0.113.50");

    assertThat(ClientRequestMetadata.clientIp(request)).isEqualTo("127.0.0.1");
  }

  @Test
  void userAgentEhLimitado() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("User-Agent", "x".repeat(800));

    assertThat(ClientRequestMetadata.userAgent(request)).hasSize(512);
  }
}
