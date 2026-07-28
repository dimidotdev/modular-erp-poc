package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.compras.PurchaseRequestId;
import dev.dimi.erp.identidade.UserId;
import java.time.LocalDate;

/** Comando de abertura de cotação. {@code requestId} null = cotação ad-hoc (sem origem em SC). */
public record OpenQuotationCommand(
    BranchId branchId, PurchaseRequestId requestId, LocalDate deadline, UserId actor) {}
