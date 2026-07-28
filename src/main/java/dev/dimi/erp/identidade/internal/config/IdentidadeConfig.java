package dev.dimi.erp.identidade.internal.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Beans transversais ao módulo identidade. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentidadeProperties.class)
class IdentidadeConfig {

  @Bean
  PasswordEncoder passwordEncoder(IdentidadeProperties props) {
    var a = props.password().argon2();
    return new Argon2PasswordEncoder(
        a.saltLength(), a.hashLength(), a.parallelism(), a.memoryKb(), a.iterations());
  }
}
