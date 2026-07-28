package dev.dimi.erp.identidade.internal.application;

import java.time.Duration;
import java.time.Instant;

/**
 * Resultado do step-up: novo access token carregando claim {@code sua} dentro da janela de frescor.
 *
 * <p>O refresh token NÃO é rotacionado — step-up é uma re-emissão de access token na mesma sessão.
 */
public record StepUpResult(String accessToken, Duration accessTokenTtl, Instant stepUpAt) {}
