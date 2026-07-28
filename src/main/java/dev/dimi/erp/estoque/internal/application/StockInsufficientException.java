package dev.dimi.erp.estoque.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.produto.PackagingId;
import dev.dimi.erp.shared.exception.DomainException;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** RN-EST-006: tentativa de saída/reserva levaria saldo abaixo do permitido. */
public class StockInsufficientException extends DomainException {

  private final PackagingId packagingId;
  private final BranchId branchId;
  private final BigDecimal requested;
  private final BigDecimal available;

  public StockInsufficientException(
      PackagingId packagingId, BranchId branchId, BigDecimal requested, BigDecimal available) {
    super(
        "estoque.insufficient",
        HttpStatus.CONFLICT,
        "Saldo insuficiente",
        "Quantidade solicitada excede o saldo disponível para esta embalagem nesta filial.");
    this.packagingId = packagingId;
    this.branchId = branchId;
    this.requested = requested;
    this.available = available;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of(
        "packagingId", packagingId.value().toString(),
        "branchId", branchId.value().toString(),
        "requested", requested.toPlainString(),
        "available", available.toPlainString());
  }
}
