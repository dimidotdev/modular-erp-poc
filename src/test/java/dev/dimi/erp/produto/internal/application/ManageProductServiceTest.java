package dev.dimi.erp.produto.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.Classification;
import dev.dimi.erp.produto.PackagingCreatedEvent;
import dev.dimi.erp.produto.ProductCreatedEvent;
import dev.dimi.erp.produto.ProductId;
import dev.dimi.erp.produto.internal.domain.Packaging;
import dev.dimi.erp.produto.internal.domain.Product;
import dev.dimi.erp.produto.internal.domain.Variant;
import dev.dimi.erp.produto.internal.persistence.CatalogBrandRepository;
import dev.dimi.erp.produto.internal.persistence.PackagingRepository;
import dev.dimi.erp.produto.internal.persistence.ProductRepository;
import dev.dimi.erp.produto.internal.persistence.VariantRepository;
import dev.dimi.erp.shared.multitenancy.Tenant;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ManageProductServiceTest {

  private static final UserId ACTOR = UserId.newId();

  @Mock private ProductRepository products;
  @Mock private VariantRepository variants;
  @Mock private PackagingRepository packagings;
  @Mock private CatalogBrandRepository brands;
  @Mock private ApplicationEventPublisher events;

  private ManageProductService service;

  @BeforeEach
  void setUp() {
    service = new ManageProductService(products, variants, packagings, brands, events);
    TenantContext.set(new Tenant(UUID.randomUUID(), "test-tenant", "tenant_test"));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("registrar PRODUTO sem NCM lança InvalidProductDataException (RN-PRD-002)")
  void registroSemNcmFalha() {
    RegisterProductCommand cmd =
        new RegisterProductCommand(
            "ROOT-1",
            "Produto X",
            null,
            null,
            Classification.PRODUTO,
            null,
            null,
            null,
            null,
            null,
            null,
            ACTOR);

    assertThatThrownBy(() -> service.register(cmd)).isInstanceOf(InvalidProductDataException.class);
    verify(products, never()).save(any());
  }

  @Test
  @DisplayName("registrar SERVICO sem NCM passa")
  void registroServicoSemNcmOk() {
    given(products.existsBySkuRoot("SRV-1")).willReturn(false);
    given(products.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0, Product.class));
    given(variants.save(any(Variant.class))).willAnswer(inv -> inv.getArgument(0, Variant.class));

    RegisterProductCommand cmd =
        new RegisterProductCommand(
            "SRV-1",
            "Servico X",
            null,
            null,
            Classification.SERVICO,
            null,
            null,
            null,
            null,
            null,
            null,
            ACTOR);

    ProductSummary saved = service.register(cmd);

    assertThat(saved.skuRoot()).isEqualTo("SRV-1");
    assertThat(saved.classification()).isEqualTo(Classification.SERVICO);
    verify(events, times(1)).publishEvent(any(ProductCreatedEvent.class));
    verify(events, never()).publishEvent(any(PackagingCreatedEvent.class));
  }

  @Test
  @DisplayName(
      "registro com defaultPackagingSku cria variant + packaging default e emite 2 eventos")
  void registroComPackagingDefault() {
    given(products.existsBySkuRoot("ROOT-2")).willReturn(false);
    given(packagings.existsBySku("ROOT-2-DEF")).willReturn(false);
    given(products.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0, Product.class));
    given(variants.save(any(Variant.class))).willAnswer(inv -> inv.getArgument(0, Variant.class));
    given(packagings.save(any(Packaging.class)))
        .willAnswer(inv -> inv.getArgument(0, Packaging.class));

    RegisterProductCommand cmd =
        new RegisterProductCommand(
            "ROOT-2",
            "Produto Y",
            null,
            null,
            Classification.PRODUTO,
            "61091000",
            null,
            "0",
            null,
            "ROOT-2-DEF",
            null,
            ACTOR);

    service.register(cmd);

    verify(products, times(1)).save(any(Product.class));
    verify(variants, times(1)).save(any(Variant.class));
    verify(packagings, times(1)).save(any(Packaging.class));
    verify(events, times(1)).publishEvent(any(ProductCreatedEvent.class));
    verify(events, times(1)).publishEvent(any(PackagingCreatedEvent.class));
  }

  @Test
  @DisplayName("skuRoot duplicado lança DuplicateSkuException")
  void skuRootDuplicado() {
    given(products.existsBySkuRoot("ROOT-DUP")).willReturn(true);

    RegisterProductCommand cmd =
        new RegisterProductCommand(
            "ROOT-DUP",
            "Y",
            null,
            null,
            Classification.PRODUTO,
            "61091000",
            null,
            null,
            null,
            null,
            null,
            ACTOR);

    assertThatThrownBy(() -> service.register(cmd)).isInstanceOf(DuplicateSkuException.class);
  }

  @Test
  @DisplayName("findById inexistente lança ProductNotFoundException")
  void findByIdInexistente() {
    ProductId id = new ProductId(UUID.randomUUID());
    given(products.findById(id.value())).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.findById(id)).isInstanceOf(ProductNotFoundException.class);
  }
}
