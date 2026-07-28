package dev.dimi.erp.fiscal.internal.adapter.acbr.dto;

/**
 * Payload de {@code POST /nfe/{id}/cancelamento} no SaaS oficial {@code acbr.api.br}.
 *
 * <p>{@code justificativa} é texto livre de 15 a 255 caracteres por exigência da SEFAZ (RN-FIS
 * cancelamento). A validação de tamanho fica no service que invoca este client — aqui é só
 * transporte.
 */
public record NfePedidoCancelamento(String justificativa) {}
