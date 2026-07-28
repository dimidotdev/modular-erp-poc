package dev.dimi.erp.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Marca testes que dependem de Docker. Quando o daemon não estiver acessível na máquina, esses
 * testes são SKIPPED em vez de causar erro — útil para devs sem permissão local; CI continua
 * rodando tudo normalmente.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(DockerAvailableCondition.class)
public @interface EnabledIfDockerAvailable {}
