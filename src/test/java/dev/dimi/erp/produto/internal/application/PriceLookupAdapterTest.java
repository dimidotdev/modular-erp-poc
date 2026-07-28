package dev.dimi.erp.produto.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.produto.PackagingId;
import dev.dimi.erp.produto.PriceLookupPort;
import dev.dimi.erp.produto.internal.domain.PriceTable;
import dev.dimi.erp.produto.internal.domain.PriceTableItem;
import dev.dimi.erp.produto.internal.persistence.PriceTableItemRepository;
import dev.dimi.erp.produto.internal.persistence.PriceTableRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * RN-PRD-004: valida a regra de preferência do {@link PriceLookupAdapter} — tabela específica de
 * filial vence sobre tabela global. {@link PriceTableRepository#findActiveOn} já devolve ordenado
 * (filial primeiro); o adapter pega o primeiro hit com item para a packaging.
 */
@ExtendWith(MockitoExtension.class)
class PriceLookupAdapterTest {

  private static final PackagingId PACK = new PackagingId(UUID.randomUUID());
  private static final BranchId BRANCH = new BranchId(UUID.randomUUID());
  private static final LocalDate DATE = LocalDate.of(2026, 6, 1);

  @Mock private PriceTableRepository tables;
  @Mock private PriceTableItemRepository items;

  private PriceLookupAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new PriceLookupAdapter(tables, items);
  }

  @Test
  @DisplayName("tabela de filial vence quando há item para a packaging")
  void tabelaDaFilialVence() {
    PriceTable filial = PriceTable.create("Filial", BRANCH.value(), LocalDate.of(2026, 1, 1), null);
    PriceTable global = PriceTable.create("Global", null, LocalDate.of(2026, 1, 1), null);
    given(tables.findActiveOn(BRANCH.value(), DATE)).willReturn(List.of(filial, global));

    PriceTableItem itemFilial =
        PriceTableItem.create(
            filial.id().value(), PACK.value(), new BigDecimal("90.00"), new BigDecimal("0.1000"));
    given(items.findByPriceTableIdAndPackagingId(filial.id().value(), PACK.value()))
        .willReturn(Optional.of(itemFilial));

    Optional<PriceLookupPort.PriceQuote> quote = adapter.resolve(PACK, BRANCH, DATE);

    assertThat(quote).isPresent();
    assertThat(quote.get().salePrice()).isEqualByComparingTo("90.00");
    assertThat(quote.get().priceTableId()).isEqualTo(filial.id());
  }

  @Test
  @DisplayName("cai para global quando tabela de filial não tem item da packaging")
  void caiParaGlobalQuandoFilialNaoTemItem() {
    PriceTable filial = PriceTable.create("Filial", BRANCH.value(), LocalDate.of(2026, 1, 1), null);
    PriceTable global = PriceTable.create("Global", null, LocalDate.of(2026, 1, 1), null);
    given(tables.findActiveOn(BRANCH.value(), DATE)).willReturn(List.of(filial, global));
    given(items.findByPriceTableIdAndPackagingId(filial.id().value(), PACK.value()))
        .willReturn(Optional.empty());
    PriceTableItem itemGlobal =
        PriceTableItem.create(
            global.id().value(), PACK.value(), new BigDecimal("100.00"), BigDecimal.ZERO);
    given(items.findByPriceTableIdAndPackagingId(global.id().value(), PACK.value()))
        .willReturn(Optional.of(itemGlobal));

    Optional<PriceLookupPort.PriceQuote> quote = adapter.resolve(PACK, BRANCH, DATE);

    assertThat(quote).isPresent();
    assertThat(quote.get().salePrice()).isEqualByComparingTo("100.00");
    assertThat(quote.get().priceTableId()).isEqualTo(global.id());
  }

  @Test
  @DisplayName("sem tabela vigente devolve empty")
  void semTabelaDevolveEmpty() {
    given(tables.findActiveOn(BRANCH.value(), DATE)).willReturn(List.of());
    assertThat(adapter.resolve(PACK, BRANCH, DATE)).isEmpty();
  }

  @Test
  @DisplayName("tabelas vigentes mas sem item para a packaging devolve empty")
  void tabelasMasSemItemDevolveEmpty() {
    PriceTable global = PriceTable.create("Global", null, LocalDate.of(2026, 1, 1), null);
    given(tables.findActiveOn(BRANCH.value(), DATE)).willReturn(List.of(global));
    given(items.findByPriceTableIdAndPackagingId(global.id().value(), PACK.value()))
        .willReturn(Optional.empty());

    assertThat(adapter.resolve(PACK, BRANCH, DATE)).isEmpty();
  }
}
