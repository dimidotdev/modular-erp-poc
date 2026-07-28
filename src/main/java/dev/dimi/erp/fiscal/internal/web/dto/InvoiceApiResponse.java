package dev.dimi.erp.fiscal.internal.web.dto;

import dev.dimi.erp.fiscal.ConsumerType;
import dev.dimi.erp.fiscal.FiscalModel;
import dev.dimi.erp.fiscal.FiscalOriginType;
import dev.dimi.erp.fiscal.InvoiceOperation;
import dev.dimi.erp.fiscal.InvoicePurpose;
import dev.dimi.erp.fiscal.InvoiceStatus;
import dev.dimi.erp.fiscal.NfeEnvironment;
import dev.dimi.erp.fiscal.internal.domain.Invoice;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Projeção REST de {@link Invoice}. Carrega o snapshot necessário para clientes consumirem após
 * emissão: chave fiscal, protocolo, status e totais. Itens não fazem parte da resposta de emissão
 * para manter o payload enxuto — slice 8.6 introduz {@code /invoices/{id}/items}.
 */
public record InvoiceApiResponse(
    UUID id,
    UUID branchId,
    FiscalModel model,
    String series,
    long number,
    InvoiceStatus status,
    InvoiceOperation operation,
    InvoicePurpose purpose,
    ConsumerType consumerType,
    NfeEnvironment environment,
    UUID customerPersonId,
    String customerCpfCnpj,
    FiscalOriginType originType,
    UUID originId,
    String fiscalKey,
    String sefazProtocol,
    Instant sefazAuthorizedAt,
    String sefazRejectionCode,
    String sefazRejectionReason,
    BigDecimal totalProducts,
    BigDecimal totalDiscount,
    BigDecimal totalFreight,
    BigDecimal totalTaxes,
    BigDecimal total,
    Instant issuedAt) {

  public static InvoiceApiResponse from(Invoice invoice) {
    return new InvoiceApiResponse(
        invoice.getId(),
        invoice.getBranchId(),
        invoice.getModel(),
        invoice.getSeries(),
        invoice.getNumber(),
        invoice.getStatus(),
        invoice.getOperation(),
        invoice.getPurpose(),
        invoice.getConsumerType(),
        invoice.getNfeEnvironment(),
        invoice.getCustomerPersonId(),
        invoice.getCustomerCpfCnpj(),
        invoice.getOriginType(),
        invoice.getOriginId(),
        invoice.getFiscalKey(),
        invoice.getSefazProtocol(),
        invoice.getSefazAuthorizedAt(),
        invoice.getSefazRejectionCode(),
        invoice.getSefazRejectionReason(),
        invoice.getTotalProducts(),
        invoice.getTotalDiscount(),
        invoice.getTotalFreight(),
        invoice.getTotalTaxes(),
        invoice.getTotal(),
        invoice.getIssuedAt());
  }
}
