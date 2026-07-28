package dev.dimi.erp.movimentacao.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.movimentacao.OutboundReason;
import dev.dimi.erp.movimentacao.OutboundVoucherId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Saída avulsa — amostra, brinde, garantia, perda ou outros (RN-MOV-022). Reduz estoque sem MVF;
 * NFe opcional configurável no slice fiscal. Não há transição de status — voucher é "emitido" e
 * fim.
 */
@Entity
@Table(name = "outbound_voucher")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "number", "branchId", "reason"})
public class OutboundVoucher {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "number", nullable = false, updatable = false)
  private long number;

  @Column(name = "branch_id", nullable = false, updatable = false)
  private UUID branchId;

  @Enumerated(EnumType.STRING)
  @Column(name = "reason", nullable = false, updatable = false)
  private OutboundReason reason;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "recipient_person_id")
  private UUID recipientPersonId;

  @Column(name = "notes")
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public OutboundVoucherId id() {
    return new OutboundVoucherId(id);
  }

  public BranchId branchIdTyped() {
    return new BranchId(branchId);
  }

  public PersonId recipientPersonIdTyped() {
    return recipientPersonId == null ? null : new PersonId(recipientPersonId);
  }

  public static OutboundVoucher issue(
      long number, UUID branchId, OutboundReason reason, UUID recipientPersonId, String notes) {
    OutboundVoucher v = new OutboundVoucher();
    v.id = UuidCreator.getTimeOrderedEpoch();
    v.number = number;
    v.branchId = Objects.requireNonNull(branchId, "branchId");
    v.reason = Objects.requireNonNull(reason, "reason");
    v.issuedAt = Instant.now();
    v.recipientPersonId = recipientPersonId;
    v.notes = notes;
    return v;
  }

  public void updateNotes(String notes) {
    if (notes != null) this.notes = notes;
  }

  @PrePersist
  void onPersist() {
    Instant now = Instant.now();
    if (id == null) {
      id = UuidCreator.getTimeOrderedEpoch();
    }
    if (issuedAt == null) {
      issuedAt = now;
    }
    if (createdAt == null) {
      createdAt = now;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof OutboundVoucher other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
