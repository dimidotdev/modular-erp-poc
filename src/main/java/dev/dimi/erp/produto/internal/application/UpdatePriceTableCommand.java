package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.PriceTableId;
import java.time.LocalDate;

/** Comando de atualização parcial de tabela de preço. Campos null não são tocados. */
public record UpdatePriceTableCommand(
    PriceTableId priceTableId,
    String name,
    LocalDate validFrom,
    LocalDate validUntil,
    UserId actor) {}
