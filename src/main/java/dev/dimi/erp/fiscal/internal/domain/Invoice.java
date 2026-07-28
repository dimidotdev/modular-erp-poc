package dev.dimi.erp.fiscal.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.fiscal.ConsumerType;
import dev.dimi.erp.fiscal.FiscalModel;
import dev.dimi.erp.fiscal.FiscalOriginType;
import dev.dimi.erp.fiscal.InvoiceId;
import dev.dimi.erp.fiscal.InvoiceOperation;
import dev.dimi.erp.fiscal.InvoicePaymentMethod;
import dev.dimi.erp.fiscal.InvoicePurpose;
import dev.dimi.erp.fiscal.InvoiceStatus;
import dev.dimi.erp.fiscal.NfeEnvironment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Documento fiscal agnóstico (NFE/NFCE/NFSE). Carrega snapshot do cliente, totais, chave fiscal de
 * 44 dígitos e protocolo SEFAZ. Sem {@code @Version} no slice 8.1 (defer; RN-FIS-002 serializa via
 * allocator + step-up).
 */
@Entity
@Table(name = "invoice")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "branchId", "model", "series", "number", "status"})
public class Invoice {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "branch_id", nullable = false, updatable = false)
  private UUID branchId;

  @Enumerated(EnumType.STRING)
  @Column(name = "model", nullable = false, updatable = false)
  private FiscalModel model;

  @Column(name = "series", nullable = false, updatable = false)
  private String series;

  @Column(name = "number", nullable = false, updatable = false)
  private long number;

  @Column(name = "issued_at", nullable = false, updatable = false)
  private Instant issuedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private InvoiceStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "operation", nullable = false, updatable = false)
  private InvoiceOperation operation;

  @Enumerated(EnumType.STRING)
  @Column(name = "purpose", nullable = false, updatable = false)
  private InvoicePurpose purpose;

  @Enumerated(EnumType.STRING)
  @Column(name = "consumer_type", nullable = false, updatable = false)
  private ConsumerType consumerType;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_method")
  private InvoicePaymentMethod paymentMethod;

  @Column(name = "customer_person_id", updatable = false)
  private UUID customerPersonId;

  @Column(name = "customer_cpf_cnpj", updatable = false)
  private String customerCpfCnpj;

  @Enumerated(EnumType.STRING)
  @Column(name = "origin_type", nullable = false, updatable = false)
  private FiscalOriginType originType;

  @Column(name = "origin_id", updatable = false)
  private UUID originId;

  @Column(name = "fiscal_key")
  private String fiscalKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "nfe_environment", nullable = false, updatable = false)
  private NfeEnvironment nfeEnvironment;

  @Column(name = "sefaz_protocol")
  private String sefazProtocol;

  @Column(name = "sefaz_authorized_at")
  private Instant sefazAuthorizedAt;

  @Column(name = "sefaz_rejection_code")
  private String sefazRejectionCode;

  @Column(name = "sefaz_rejection_reason")
  private String sefazRejectionReason;

  @Column(name = "cancellation_protocol")
  private String cancellationProtocol;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "cancellation_reason")
  private String cancellationReason;

  @Column(name = "total_products", nullable = false, precision = 15, scale = 2)
  private BigDecimal totalProducts;

  @Column(name = "total_discount", nullable = false, precision = 15, scale = 2)
  private BigDecimal totalDiscount;

  @Column(name = "total_freight", nullable = false, precision = 15, scale = 2)
  private BigDecimal totalFreight;

  @Column(name = "total_taxes", nullable = false, precision = 15, scale = 2)
  private BigDecimal totalTaxes;

  @Column(name = "total", nullable = false, precision = 15, scale = 2)
  private BigDecimal total;

  @Column(name = "notes")
  private String notes;

  @Column(name = "xml_storage_path")
  private String xmlStoragePath;

  @Column(name = "danfe_storage_path")
  private String danfeStoragePath;

  @Column(name = "idempotency_key", updatable = false)
  private String idempotencyKey;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public InvoiceId id() {
    return new InvoiceId(id);
  }

  public BranchId branchIdTyped() {
    return new BranchId(branchId);
  }

  public PersonId customerPersonIdTyped() {
    return customerPersonId == null ? null : new PersonId(customerPersonId);
  }

  public static Invoice draft(
      UUID branchId,
      FiscalModel model,
      String series,
      long number,
      InvoiceOperation operation,
      InvoicePurpose purpose,
      ConsumerType consumerType,
      FiscalOriginType originType,
      UUID originId,
      NfeEnvironment nfeEnvironment,
      String idempotencyKey) {
    Invoice inv = new Invoice();
    inv.id = UuidCreator.getTimeOrderedEpoch();
    inv.branchId = Objects.requireNonNull(branchId, "branchId");
    inv.model = Objects.requireNonNull(model, "model");
    inv.series = Objects.requireNonNull(series, "series");
    inv.number = number;
    inv.operation = Objects.requireNonNull(operation, "operation");
    inv.purpose = Objects.requireNonNull(purpose, "purpose");
    inv.consumerType = Objects.requireNonNull(consumerType, "consumerType");
    inv.originType = Objects.requireNonNull(originType, "originType");
    inv.originId = originId;
    inv.nfeEnvironment = Objects.requireNonNull(nfeEnvironment, "nfeEnvironment");
    inv.idempotencyKey = idempotencyKey;
    inv.issuedAt = Instant.now();
    inv.status = InvoiceStatus.DRAFT;
    inv.totalProducts = BigDecimal.ZERO;
    inv.totalDiscount = BigDecimal.ZERO;
    inv.totalFreight = BigDecimal.ZERO;
    inv.totalTaxes = BigDecimal.ZERO;
    inv.total = BigDecimal.ZERO;
    return inv;
  }

  public void markPending() {
    this.status = InvoiceStatus.PENDING;
  }

  public void markAuthorized(String fiscalKey, String protocol, Instant authorizedAt) {
    this.status = InvoiceStatus.AUTHORIZED;
    this.fiscalKey = fiscalKey;
    this.sefazProtocol = protocol;
    this.sefazAuthorizedAt = authorizedAt;
  }

  public void markRejected(String code, String reason) {
    this.status = InvoiceStatus.REJECTED;
    this.sefazRejectionCode = code;
    this.sefazRejectionReason = reason;
  }

  public void markCancelled(String protocol, String reason) {
    this.status = InvoiceStatus.CANCELLED;
    this.cancellationProtocol = protocol;
    this.cancellationReason = reason;
    this.cancelledAt = Instant.now();
  }

  public void markInutilized() {
    this.status = InvoiceStatus.INUTILIZED;
  }

  public void updateTotals(
      BigDecimal totalProducts,
      BigDecimal totalDiscount,
      BigDecimal totalFreight,
      BigDecimal totalTaxes,
      BigDecimal total) {
    this.totalProducts = totalProducts;
    this.totalDiscount = totalDiscount;
    this.totalFreight = totalFreight;
    this.totalTaxes = totalTaxes;
    this.total = total;
  }

  public void updateStorage(String xmlStoragePath, String danfeStoragePath) {
    this.xmlStoragePath = xmlStoragePath;
    this.danfeStoragePath = danfeStoragePath;
  }

  public void updateCustomer(UUID personId, String cpfCnpj) {
    this.customerPersonId = personId;
    this.customerCpfCnpj = cpfCnpj;
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
    if (!(o instanceof Invoice other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
