package dev.dimi.erp.estoque.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.estoque.StockBatchId;
import dev.dimi.erp.produto.PackagingId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Lote opcional associado a uma embalagem ({@link PackagingId}). RN-EST-011: alguns produtos exigem
 * rastreio por lote (config futura); v1 só armazena os lotes — enforcement de obrigatoriedade vai
 * para o service no slice 4.4.
 *
 * <p>{@code expiresOn} no passado emite warning ao ser referenciado em movimento (RN-EST-012), não
 * bloqueia.
 */
@Entity
@Table(name = "stock_batch")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "packagingId", "code", "expiresOn"})
public class StockBatch {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "packaging_id", nullable = false, updatable = false)
  private UUID packagingId;

  @Column(name = "code", nullable = false, updatable = false)
  private String code;

  @Column(name = "expires_on")
  private LocalDate expiresOn;

  @Column(name = "manufactured_on")
  private LocalDate manufacturedOn;

  @Column(name = "notes")
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public StockBatchId id() {
    return new StockBatchId(id);
  }

  public PackagingId packagingIdTyped() {
    return new PackagingId(packagingId);
  }

  public static StockBatch create(
      UUID packagingId, String code, LocalDate manufacturedOn, LocalDate expiresOn, String notes) {
    if (manufacturedOn != null && expiresOn != null && expiresOn.isBefore(manufacturedOn)) {
      throw new IllegalArgumentException("expiresOn não pode ser anterior a manufacturedOn");
    }
    StockBatch b = new StockBatch();
    b.id = UuidCreator.getTimeOrderedEpoch();
    b.packagingId = Objects.requireNonNull(packagingId, "packagingId");
    b.code = Objects.requireNonNull(code, "code");
    b.manufacturedOn = manufacturedOn;
    b.expiresOn = expiresOn;
    b.notes = notes;
    return b;
  }

  public void updateMetadata(LocalDate manufacturedOn, LocalDate expiresOn, String notes) {
    if (manufacturedOn != null) this.manufacturedOn = manufacturedOn;
    if (expiresOn != null) this.expiresOn = expiresOn;
    if (notes != null) this.notes = notes;
    if (this.manufacturedOn != null
        && this.expiresOn != null
        && this.expiresOn.isBefore(this.manufacturedOn)) {
      throw new IllegalArgumentException("expiresOn não pode ser anterior a manufacturedOn");
    }
  }

  @PrePersist
  void onPersist() {
    Instant now = Instant.now();
    if (id == null) {
      id = UuidCreator.getTimeOrderedEpoch();
    }
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof StockBatch other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
