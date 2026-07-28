package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.identidade.UserId;
import java.time.LocalDate;

/** Comando de criação de tabela de preço. {@code branchId} null = tabela global do tenant. */
public record RegisterPriceTableCommand(
    String name, BranchId branchId, LocalDate validFrom, LocalDate validUntil, UserId actor) {}
