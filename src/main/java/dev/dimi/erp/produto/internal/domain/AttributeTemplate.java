package dev.dimi.erp.produto.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Definição de atributo dinâmico configurável por tenant. Quando {@code dataType=ENUM}, {@code
 * enumOptions} carrega o array JSON com os valores válidos.
 */
@Entity
@Table(name = "attribute_template")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "code", "name", "dataType", "active"})
public class AttributeTemplate {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "code", nullable = false, updatable = false)
  private String code;

  @Column(name = "name", nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "data_type", nullable = false)
  private AttributeDataType dataType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "enum_options")
  private String enumOptions;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public AttributeTemplateId id() {
    return new AttributeTemplateId(id);
  }

  public static AttributeTemplate create(
      String code, String name, AttributeDataType dataType, String enumOptions) {
    AttributeTemplate t = new AttributeTemplate();
    t.id = UuidCreator.getTimeOrderedEpoch();
    t.code = Objects.requireNonNull(code, "code");
    t.name = Objects.requireNonNull(name, "name");
    t.dataType = Objects.requireNonNull(dataType, "dataType");
    t.enumOptions = enumOptions;
    t.active = true;
    return t;
  }

  public void rename(String name) {
    if (name != null && !name.isBlank()) {
      this.name = name;
    }
  }

  public void deactivate() {
    this.active = false;
  }

  public void reactivate() {
    this.active = true;
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
    if (!(o instanceof AttributeTemplate other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
