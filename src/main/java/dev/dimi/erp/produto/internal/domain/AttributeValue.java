package dev.dimi.erp.produto.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Valor de atributo aplicado a um {@link Product} OU a uma {@link Variant} — nunca aos dois ao
 * mesmo tempo (CHECK em DDL). A validação semântica de {@code value} contra {@link
 * AttributeTemplate#getDataType()} fica a cargo do service.
 */
@Entity
@Table(name = "attribute_value")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "templateId", "productId", "variantId"})
public class AttributeValue {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "template_id", nullable = false, updatable = false)
  private UUID templateId;

  @Column(name = "product_id", updatable = false)
  private UUID productId;

  @Column(name = "variant_id", updatable = false)
  private UUID variantId;

  @Column(name = "value", nullable = false)
  private String value;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public AttributeValueId id() {
    return new AttributeValueId(id);
  }

  public static AttributeValue forProduct(UUID templateId, UUID productId, String value) {
    return create(templateId, Objects.requireNonNull(productId, "productId"), null, value);
  }

  public static AttributeValue forVariant(UUID templateId, UUID variantId, String value) {
    return create(templateId, null, Objects.requireNonNull(variantId, "variantId"), value);
  }

  private static AttributeValue create(
      UUID templateId, UUID productId, UUID variantId, String value) {
    AttributeValue av = new AttributeValue();
    av.id = UuidCreator.getTimeOrderedEpoch();
    av.templateId = Objects.requireNonNull(templateId, "templateId");
    av.productId = productId;
    av.variantId = variantId;
    av.value = Objects.requireNonNull(value, "value");
    return av;
  }

  public void changeValue(String newValue) {
    if (newValue != null && !newValue.isBlank()) {
      this.value = newValue;
    }
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
    if (!(o instanceof AttributeValue other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
