package dev.dimi.erp.produto.internal.web.dto;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.PackagingId;
import dev.dimi.erp.produto.PriceTableId;
import dev.dimi.erp.produto.internal.application.UpsertPriceItemsCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Payload de {@code PUT /api/v1/produtos/price-tables/{id}/items}. {@code replaceAll=true}
 * (default) substitui todos os itens da tabela; false faz upsert preservando os ausentes.
 */
public record UpsertPriceItemsApiRequest(@NotEmpty @Valid List<Item> items, Boolean replaceAll) {

  public record Item(
      @NotNull UUID packagingId,
      @NotNull @PositiveOrZero BigDecimal salePrice,
      @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal discountMax) {}

  public UpsertPriceItemsCommand toCommand(PriceTableId priceTableId, UserId actor) {
    List<UpsertPriceItemsCommand.Item> mapped =
        items.stream()
            .map(
                i ->
                    new UpsertPriceItemsCommand.Item(
                        new PackagingId(i.packagingId()),
                        i.salePrice(),
                        i.discountMax() == null ? BigDecimal.ZERO : i.discountMax()))
            .toList();
    return new UpsertPriceItemsCommand(
        priceTableId, mapped, replaceAll == null || replaceAll, actor);
  }
}
