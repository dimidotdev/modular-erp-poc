package dev.dimi.erp.fiscal.internal.adapter.acbr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Payload de {@code POST /nfe} no SaaS oficial {@code acbr.api.br}.
 *
 * <p>O contrato espelha o esquema XML da SEFAZ — {@code infNFe} é o nó técnico completo
 * (identificação, emitente, destinatário, itens, totais, transporte, pagamentos) e {@code
 * infNFeSupl} carrega QR Code/URL chave de consulta para NFCe (modelo 65, não usado no v1).
 *
 * <p>Tipamos como {@code Map<String,Object>} porque o esquema NFe tem dezenas de campos
 * condicionais por regime tributário, finalidade, modalidade de frete etc. Construir um POJO
 * fortemente tipado aqui replicaria centenas de classes que o SaaS já validou — o adapter monta o
 * mapa via {@code IssueInvoiceFromSaleService} a partir de {@code InvoiceDraft}, e o upstream
 * devolve {@link Dfe} já mapeado para nosso domínio.
 *
 * <p>{@code ambiente}: {@code producao} ou {@code homologacao}. {@code referencia}: chave
 * idempotente do nosso lado (UUID do invoice) para deduplicação no upstream.
 */
public record NfePedidoEmissao(
    @JsonProperty("infNFe") Map<String, Object> infNFe,
    @JsonProperty("infNFeSupl") Map<String, Object> infNFeSupl,
    String ambiente,
    String referencia) {}
