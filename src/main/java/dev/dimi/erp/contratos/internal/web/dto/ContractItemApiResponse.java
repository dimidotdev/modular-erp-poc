package dev.dimi.erp.contratos.internal.web.dto;

import dev.dimi.erp.contratos.ItemType;
import dev.dimi.erp.contratos.internal.domain.ContractItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Representação REST de {@code contract_item}. Mapping do enum interno {@link
 * ContractItem.ItemType} para o público {@link ItemType} acontece em {@link #from(ContractItem)} —
 * mesmo padrão do {@code BillingApiResponse} no módulo de OS.
 */
public record ContractItemApiResponse(
    UUID id,
    UUID contractId,
    int lineNumber,
    ItemType type,
    UUID packagingId,
    String serviceCode,
    String description,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal discountValue,
    BigDecimal netValue,
    Instant createdAt,
    Instant updatedAt) {

  public static ContractItemApiResponse from(ContractItem it) {
    return new ContractItemApiResponse(
        it.getId(),
        it.getContractId(),
        it.getLineNumber(),
        toPublic(it.getItemType()),
        it.getPackagingId(),
        it.getServiceCode(),
        it.getDescription(),
        it.getQuantity(),
        it.getUnitPrice(),
        it.getDiscountValue(),
        it.getNetValue(),
        it.getCreatedAt(),
        it.getUpdatedAt());
  }

  private static ItemType toPublic(ContractItem.ItemType internal) {
    if (internal == null) return null;
    return switch (internal) {
      case PRODUCT -> ItemType.PRODUCT;
      case SERVICE -> ItemType.SERVICE;
    };
  }
}
