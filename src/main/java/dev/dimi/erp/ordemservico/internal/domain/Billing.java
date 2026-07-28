package dev.dimi.erp.ordemservico.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.ordemservico.BillingId;
import dev.dimi.erp.ordemservico.ServiceOrderId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Faturamento de uma OS — espelha {@code service_order_billing}. Slice 9.2b: cobre o caso em que a
 * OS pronta gera simultaneamente uma NFe (linhas de produto) e uma NFSe (linhas de serviço). Pelo
 * menos uma das duas referências fiscais ({@code nfeInvoiceId}/{@code nfseInvoiceId}) precisa
 * existir — invariância validada em {@link #issue} e por {@code CHECK ck_os_billing_has_invoice} no
 * SQL.
 *
 * <p>FKs a {@code service_order}, invoice e user ficam como UUID raw (não @ManyToOne) seguindo o
 * mesmo padrão de {@link ServiceOrder} — evita cascata acidental e lazy fetch desnecessário no
 * fluxo de leitura por id.
 *
 * <p>Cancelamento é idempotente: {@link #markCancelled} lança {@link IllegalStateException} se
 * {@code cancelledAt} já estiver populado — o caller (application service) deve consultar antes.
 */
@Entity
@Table(name = "service_order_billing")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "osId", "billingType", "issuedAt", "total", "cancelledAt"})
public class Billing {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "os_id", nullable = false, updatable = false)
  private UUID osId;

  @Enumerated(EnumType.STRING)
  @Column(name = "billing_type", nullable = false)
  private BillingType billingType;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "nfe_invoice_id")
  private UUID nfeInvoiceId;

  @Column(name = "nfse_invoice_id")
  private UUID nfseInvoiceId;

  @Column(name = "products_amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal productsAmount;

  @Column(name = "services_amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal servicesAmount;

  @Column(name = "total", nullable = false, precision = 15, scale = 2)
  private BigDecimal total;

  @Column(name = "notes")
  private String notes;

  @Column(name = "issued_by", nullable = false, updatable = false)
  private UUID issuedBy;

  @Column(name = "idempotency_key", updatable = false)
  private String idempotencyKey;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "cancellation_reason")
  private String cancellationReason;

  @Column(name = "cancelled_by")
  private UUID cancelledBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public BillingId typedId() {
    return new BillingId(id);
  }

  public ServiceOrderId osIdTyped() {
    return new ServiceOrderId(osId);
  }

  /**
   * Cria novo registro de faturamento. Valida que pelo menos um dos invoice ids está presente — sem
   * isso o billing não tem nenhuma referência fiscal e {@code ck_os_billing_has_invoice} rejeitaria
   * o insert.
   */
  public static Billing issue(
      ServiceOrderId osId,
      BillingType billingType,
      UUID nfeInvoiceId,
      UUID nfseInvoiceId,
      BigDecimal productsAmount,
      BigDecimal servicesAmount,
      BigDecimal total,
      String notes,
      UUID issuedBy,
      String idempotencyKey) {
    if (nfeInvoiceId == null && nfseInvoiceId == null) {
      throw new IllegalArgumentException(
          "billing precisa ter ao menos uma referência fiscal (NFe ou NFSe)");
    }
    Billing b = new Billing();
    b.id = UuidCreator.getTimeOrderedEpoch();
    b.osId = Objects.requireNonNull(osId, "osId").value();
    b.billingType = Objects.requireNonNull(billingType, "billingType");
    b.issuedAt = Instant.now();
    b.nfeInvoiceId = nfeInvoiceId;
    b.nfseInvoiceId = nfseInvoiceId;
    b.productsAmount = productsAmount == null ? BigDecimal.ZERO : productsAmount;
    b.servicesAmount = servicesAmount == null ? BigDecimal.ZERO : servicesAmount;
    b.total = Objects.requireNonNull(total, "total");
    b.notes = notes;
    b.issuedBy = Objects.requireNonNull(issuedBy, "issuedBy");
    b.idempotencyKey = idempotencyKey;
    return b;
  }

  /**
   * Marca como cancelado. Idempotência: lança {@link IllegalStateException} se já estava cancelado.
   * Caller (service de cancelamento) deve consultar antes e levantar a exceção pública apropriada.
   */
  public void markCancelled(String reason, UUID cancelledBy) {
    if (this.cancelledAt != null) {
      throw new IllegalStateException("billing já cancelado");
    }
    this.cancelledAt = Instant.now();
    this.cancellationReason = reason;
    this.cancelledBy = Objects.requireNonNull(cancelledBy, "cancelledBy");
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
    if (issuedAt == null) {
      issuedAt = now;
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
    if (!(o instanceof Billing other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
