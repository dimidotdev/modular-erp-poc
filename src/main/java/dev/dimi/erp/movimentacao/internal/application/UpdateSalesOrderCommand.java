package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.movimentacao.SalesOrderId;
import dev.dimi.erp.produto.PriceTableId;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Comando de atualização parcial de pedido em DRAFT/PENDING_APPROVAL. Quando {@code items} é
 * informado (não null), substitui o conjunto inteiro; quando null, preserva os existentes. Campos
 * de cabeçalho null não tocam.
 */
public record UpdateSalesOrderCommand(
    SalesOrderId salesOrderId,
    PersonId salespersonId,
    UUID paymentConditionId,
    PriceTableId priceTableId,
    LocalDate validUntil,
    String notes,
    List<RegisterSalesOrderCommand.Item> items,
    UserId actor) {

  /** Indica se o caller pediu para substituir os itens (mesmo que com lista vazia). */
  public boolean replacesItems() {
    return items != null;
  }

  /** Lista resolvida — vazia quando não foi informada. */
  public List<RegisterSalesOrderCommand.Item> itemsOrEmpty() {
    return items == null ? List.of() : items;
  }
}
