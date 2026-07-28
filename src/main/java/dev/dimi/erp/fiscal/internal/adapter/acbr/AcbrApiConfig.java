package dev.dimi.erp.fiscal.internal.adapter.acbr;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "erp-poc.fiscal", name = "provider", havingValue = "acbr-api")
@EnableConfigurationProperties(AcbrApiProperties.class)
public class AcbrApiConfig {}
