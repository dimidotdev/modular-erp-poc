package dev.dimi.erp.produto.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.produto.Classification;
import dev.dimi.erp.produto.ProductId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Nível mais alto da hierarquia produto → variant → packaging. Tem identidade fiscal (NCM/CEST,
 * origem) e classificação operacional ({@link Classification}). Variedades e embalagens são
 * associadas via FK em suas próprias tabelas — não há lista navegada aqui para evitar carregamento
 * eager indevido em listagens.
 *
 * <p>Slice 4.1 foundation: estado + factory mínimos; comandos de domínio (rename, reclassify,
 * desativar) entram com os services do slice 4.2.
 */
@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "skuRoot", "name", "classification", "active"})
public class Product {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "sku_root", nullable = false, updatable = false)
  private String skuRoot;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "description")
  private String description;

  @Column(name = "brand_id")
  private UUID brandId;

  @Enumerated(EnumType.STRING)
  @Column(name = "classification", nullable = false)
  private Classification classification;

  @Column(name = "ncm", length = 8)
  private String ncm;

  @Column(name = "cest", length = 7)
  private String cest;

  @Column(name = "origin", length = 1, nullable = false)
  private String origin;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(name = "deactivated_at")
  private Instant deactivatedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public ProductId id() {
    return new ProductId(id);
  }

  /**
   * Registra um novo produto ativo. Validação de NCM obrigatório para classificações não-SERVICO
   * (RN-PRD-002) é checada pelo service caller — DDL também enforça.
   */
  public static Product register(
      String skuRoot,
      String name,
      String description,
      UUID brandId,
      Classification classification,
      String ncm,
      String cest,
      String origin) {
    Product p = new Product();
    p.id = UuidCreator.getTimeOrderedEpoch();
    p.skuRoot = Objects.requireNonNull(skuRoot, "skuRoot");
    p.name = Objects.requireNonNull(name, "name");
    p.description = description;
    p.brandId = brandId;
    p.classification = Objects.requireNonNull(classification, "classification");
    p.ncm = ncm;
    p.cest = cest;
    p.origin = origin == null ? "0" : origin;
    p.active = true;
    return p;
  }

  public void update(
      String name, String description, UUID brandId, String ncm, String cest, String origin) {
    if (name != null) this.name = name;
    if (description != null) this.description = description;
    if (brandId != null) this.brandId = brandId;
    if (ncm != null) this.ncm = ncm;
    if (cest != null) this.cest = cest;
    if (origin != null) this.origin = origin;
  }

  public void deactivate() {
    this.active = false;
    this.deactivatedAt = Instant.now();
  }

  public void reactivate() {
    this.active = true;
    this.deactivatedAt = null;
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
    if (!(o instanceof Product other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
