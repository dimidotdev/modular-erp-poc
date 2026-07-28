package dev.dimi.erp.estoque.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Testa o cálculo de WAC e as invariantes de saldo isoladamente do Spring. Cenários espelham os
 * critérios de aceitação da spec (docs/domain/12-produto-estoque.md).
 */
class StockSnapshotTest {

  private static final UUID PACK = UUID.randomUUID();
  private static final UUID BRANCH = UUID.randomUUID();
  private static final UUID LOC = UUID.randomUUID();

  @Test
  @DisplayName("INBOUND 10 @ 100 em saldo zerado deixa qty=10 e WAC=100.000000")
  void inboundEmSaldoZero() {
    StockSnapshot s = StockSnapshot.zeroed(PACK, BRANCH, LOC, null);
    s.applyInbound(new BigDecimal("10"), new BigDecimal("100"));
    assertThat(s.getQuantityOnHand()).isEqualByComparingTo("10");
    assertThat(s.getWeightedAvgCost()).isEqualByComparingTo("100.000000");
  }

  @Test
  @DisplayName("WAC ponderado: 10@100 + 5@120 = 106.666666 (qty=15)")
  void wacPonderado() {
    StockSnapshot s = StockSnapshot.zeroed(PACK, BRANCH, LOC, null);
    s.applyInbound(new BigDecimal("10"), new BigDecimal("100"));
    s.applyInbound(new BigDecimal("5"), new BigDecimal("120"));
    assertThat(s.getQuantityOnHand()).isEqualByComparingTo("15");
    assertThat(s.getWeightedAvgCost()).isEqualByComparingTo("106.666667");
  }

  @Test
  @DisplayName("INBOUND sem unitCost preserva WAC anterior")
  void inboundSemCustoPreservaWac() {
    StockSnapshot s = StockSnapshot.zeroed(PACK, BRANCH, LOC, null);
    s.applyInbound(new BigDecimal("10"), new BigDecimal("100"));
    s.applyInbound(new BigDecimal("5"), null); // estorno de saída
    assertThat(s.getQuantityOnHand()).isEqualByComparingTo("15");
    assertThat(s.getWeightedAvgCost()).isEqualByComparingTo("100.000000");
  }

  @Test
  @DisplayName("OUTBOUND maior que disponível (on-hand - reserved) lança")
  void outboundExcedeDisponivel() {
    StockSnapshot s = StockSnapshot.zeroed(PACK, BRANCH, LOC, null);
    s.applyInbound(new BigDecimal("10"), new BigDecimal("100"));
    s.reserve(new BigDecimal("3"));
    // disponível = 7. sair 8 quebra invariante (deixaria on-hand=2 < reserved=3).
    assertThatThrownBy(() -> s.applyOutbound(new BigDecimal("8")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("reserve(3) em saldo 10 deixa disponível 7")
  void reservaCalculaDisponivel() {
    StockSnapshot s = StockSnapshot.zeroed(PACK, BRANCH, LOC, null);
    s.applyInbound(new BigDecimal("10"), new BigDecimal("50"));
    s.reserve(new BigDecimal("3"));
    assertThat(s.quantityAvailable()).isEqualByComparingTo("7");
    assertThat(s.getQuantityOnHand()).isEqualByComparingTo("10");
    assertThat(s.getQuantityReserved()).isEqualByComparingTo("3");
  }

  @Test
  @DisplayName("reserva acima do on-hand lança")
  void reservaAcimaDoOnHand() {
    StockSnapshot s = StockSnapshot.zeroed(PACK, BRANCH, LOC, null);
    s.applyInbound(new BigDecimal("5"), new BigDecimal("10"));
    assertThatThrownBy(() -> s.reserve(new BigDecimal("6")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("shipReservation consome reserva E on-hand juntos")
  void shipReservationConsomeAmbos() {
    StockSnapshot s = StockSnapshot.zeroed(PACK, BRANCH, LOC, null);
    s.applyInbound(new BigDecimal("10"), new BigDecimal("50"));
    s.reserve(new BigDecimal("4"));
    s.shipReservation(new BigDecimal("4"));
    assertThat(s.getQuantityOnHand()).isEqualByComparingTo("6");
    assertThat(s.getQuantityReserved()).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("releaseReservation libera sem mexer no on-hand")
  void releaseReservationNaoMexeOnHand() {
    StockSnapshot s = StockSnapshot.zeroed(PACK, BRANCH, LOC, null);
    s.applyInbound(new BigDecimal("10"), new BigDecimal("50"));
    s.reserve(new BigDecimal("4"));
    s.releaseReservation(new BigDecimal("4"));
    assertThat(s.getQuantityOnHand()).isEqualByComparingTo("10");
    assertThat(s.getQuantityReserved()).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("quantity zero ou negativa lança em qualquer mutação")
  void quantityInvalida() {
    StockSnapshot s = StockSnapshot.zeroed(PACK, BRANCH, LOC, null);
    assertThatThrownBy(() -> s.applyInbound(BigDecimal.ZERO, new BigDecimal("10")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> s.applyOutbound(new BigDecimal("-1")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> s.reserve(BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
