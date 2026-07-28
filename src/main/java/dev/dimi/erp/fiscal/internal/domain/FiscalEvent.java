package dev.dimi.erp.fiscal.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.fiscal.InvoiceId;
import dev.dimi.erp.identidade.UserId;
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
 * Evento SEFAZ append-only por documento fiscal. v1 (8.1): AUTHORIZATION e REJECTION.
 * CANCELLATION/CCE/INUTILIZATION entram em 8.6.
 *
 * <p><strong>APPEND-ONLY:</strong> sem {@code @PreUpdate}, sem {@code updatedAt}. Todos os campos
 * de negócio são {@code updatable=false}.
 */
@Entity
@Table(name = "fiscal_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "invoiceId", "eventType", "occurredAt"})
public class FiscalEvent {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "invoice_id", nullable = false, updatable = false)
  private UUID invoiceId;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, updatable = false)
  private FiscalEventType eventType;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  @Column(name = "sefaz_protocol", updatable = false)
  private String sefazProtocol;

  @Column(name = "sefaz_status", updatable = false)
  private String sefazStatus;

  @Column(name = "payload_xml_path", updatable = false)
  private String payloadXmlPath;

  @Column(name = "notes", updatable = false)
  private String notes;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public FiscalEventId id() {
    return new FiscalEventId(id);
  }

  public InvoiceId invoiceIdTyped() {
    return new InvoiceId(invoiceId);
  }

  public UserId userIdTyped() {
    return new UserId(userId);
  }

  public static FiscalEvent record(
      UUID invoiceId,
      FiscalEventType eventType,
      String sefazProtocol,
      String sefazStatus,
      String payloadXmlPath,
      String notes,
      UUID userId) {
    FiscalEvent e = new FiscalEvent();
    e.id = UuidCreator.getTimeOrderedEpoch();
    e.invoiceId = Objects.requireNonNull(invoiceId, "invoiceId");
    e.eventType = Objects.requireNonNull(eventType, "eventType");
    e.userId = Objects.requireNonNull(userId, "userId");
    e.sefazProtocol = sefazProtocol;
    e.sefazStatus = sefazStatus;
    e.payloadXmlPath = payloadXmlPath;
    e.notes = notes;
    e.occurredAt = Instant.now();
    return e;
  }

  @PrePersist
  void onPersist() {
    Instant now = Instant.now();
    if (id == null) {
      id = UuidCreator.getTimeOrderedEpoch();
    }
    if (occurredAt == null) {
      occurredAt = now;
    }
    if (createdAt == null) {
      createdAt = now;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FiscalEvent other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
