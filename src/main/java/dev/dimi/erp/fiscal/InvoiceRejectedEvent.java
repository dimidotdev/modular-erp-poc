package dev.dimi.erp.fiscal;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.util.UUID;

/**
 * SEFAZ rejeitou a emissão (validação semântica). Invoice permanece em {@code REJECTED} aguardando
 * {@code POST /invoices/{id}/retry} (slice 8.6 — em 8.2 apenas o registro acontece).
 *
 * <p>Consumidor primário em 8.2 é {@code auditoria} (categoria {@code FISCAL}). Movimentação NÃO
 * atualiza {@code sale.invoice_id} em rejeição — a venda continua sem NFe associada.
 */
public record InvoiceRejectedEvent(
    InvoiceId invoiceId,
    TenantId tenantId,
    BranchId branchId,
    FiscalModel model,
    String series,
    long number,
    FiscalOriginType originType,
    UUID originId,
    String rejectionCode,
    String rejectionReason,
    UserId attemptedBy,
    Instant occurredAt) {}
