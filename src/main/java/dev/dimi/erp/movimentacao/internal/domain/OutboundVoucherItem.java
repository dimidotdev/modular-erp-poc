package dev.dimi.erp.movimentacao.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.movimentacao.OutboundVoucherId;
import dev.dimi.erp.produto.PackagingId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** Linha de {@link OutboundVoucher}. */
@Entity
@Table(name = "outbound_voucher_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "voucherId", "lineNumber", "packagingId", "quantity"})
public class OutboundVoucherItem {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "voucher_id", nullable = false, updatable = false)
  private UUID voucherId;

  @Column(name = "line_number", nullable = false, updatable = false)
  private int lineNumber;

  @Column(name = "packaging_id", nullable = false, updatable = false)
  private UUID packagingId;

  @Column(name = "quantity", nullable = false, precision = 15, scale = 4)
  private BigDecimal quantity;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public OutboundVoucherItemId id() {
    return new OutboundVoucherItemId(id);
  }

  public OutboundVoucherId voucherIdTyped() {
    return new OutboundVoucherId(voucherId);
  }

  public PackagingId packagingIdTyped() {
    return new PackagingId(packagingId);
  }

  public static OutboundVoucherItem create(
      UUID voucherId, int lineNumber, UUID packagingId, BigDecimal quantity) {
    OutboundVoucherItem i = new OutboundVoucherItem();
    i.id = UuidCreator.getTimeOrderedEpoch();
    i.voucherId = Objects.requireNonNull(voucherId, "voucherId");
    i.lineNumber = lineNumber;
    i.packagingId = Objects.requireNonNull(packagingId, "packagingId");
    i.quantity = Objects.requireNonNull(quantity, "quantity");
    return i;
  }

  @PrePersist
  void onPersist() {
    if (id == null) {
      id = UuidCreator.getTimeOrderedEpoch();
    }
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof OutboundVoucherItem other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
