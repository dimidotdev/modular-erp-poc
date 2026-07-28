package dev.dimi.erp.financeiro.internal.application;

import dev.dimi.erp.financeiro.InstallmentStatus;
import dev.dimi.erp.financeiro.internal.domain.FinancialInstallment;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/**
 * Calcula multa e juros de atraso para uma {@link FinancialInstallment} dada uma data de pagamento.
 *
 * <p><b>RN-FIN-009 (v1)</b> — fórmula fixa e única para todo o tenant:
 *
 * <ul>
 *   <li><b>Multa</b>: 2% sobre {@code originalValue}, aplicada uma única vez quando {@code
 *       paymentDate > dueDate}.
 *   <li><b>Juros</b>: 1% ao mês prorata por dias corridos, ou seja, {@code originalValue * 0.01 *
 *       (diasAtraso / 30)}.
 *   <li>Ambos arredondados para {@code scale=2} com {@link RoundingMode#HALF_UP}.
 *   <li>Se {@code diasAtraso <= 0} ou se o status da parcela não for {@link InstallmentStatus#OPEN}
 *       nem {@link InstallmentStatus#PARTIALLY_PAID}, retorna {@code (0, 0)} — parcelas pagas,
 *       canceladas ou renegociadas não acumulam encargos.
 * </ul>
 *
 * <p><b>Simplificação v1</b>: não há período de carência (grace period), não há escalonamento
 * (e.g., multa progressiva por faixa de atraso) e não há configuração por tenant — a fórmula é
 * literal. Quando a tabela {@code tenant_setting} crescer para acomodar parâmetros financeiros
 * (slice futuro), estes valores deixarão de ser constantes e passarão a ser campos lidos do setting
 * do tenant ativo (ex.: {@code fineRate}, {@code interestRateMonthly}, {@code graceDays}). A
 * assinatura deste service não muda — apenas a fonte das taxas.
 */
@Component
class CalculateFineInterestService {

  /** 2% — RN-FIN-009 v1, taxa fixa de multa por atraso. */
  private static final BigDecimal FINE_RATE = new BigDecimal("0.02");

  /** 1% ao mês — RN-FIN-009 v1, taxa fixa de juros mensais (prorata por dia corrido). */
  private static final BigDecimal INTEREST_RATE_MONTHLY = new BigDecimal("0.01");

  private static final BigDecimal THIRTY = new BigDecimal("30");

  /**
   * Calcula multa + juros para a parcela considerando {@code paymentDate}.
   *
   * @param installment parcela com {@code originalValue}, {@code dueDate} e {@code status}.
   * @param paymentDate data efetiva do pagamento (compara contra {@code installment.dueDate}).
   * @return par {@code (fine, interest)} já arredondado para 2 casas; nunca {@code null} nem
   *     negativo.
   */
  public FineInterestResult compute(FinancialInstallment installment, LocalDate paymentDate) {
    InstallmentStatus status = installment.getStatus();
    if (status != InstallmentStatus.OPEN && status != InstallmentStatus.PARTIALLY_PAID) {
      return new FineInterestResult(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    long daysOverdue = ChronoUnit.DAYS.between(installment.getDueDate(), paymentDate);
    if (daysOverdue <= 0) {
      return new FineInterestResult(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    BigDecimal originalValue = installment.getOriginalValue();

    BigDecimal fine = originalValue.multiply(FINE_RATE).setScale(2, RoundingMode.HALF_UP);

    BigDecimal interest =
        originalValue
            .multiply(INTEREST_RATE_MONTHLY)
            .multiply(BigDecimal.valueOf(daysOverdue))
            .divide(THIRTY, 2, RoundingMode.HALF_UP);

    return new FineInterestResult(fine, interest);
  }

  /** Resultado do cálculo: multa + juros já arredondados para 2 casas. */
  public record FineInterestResult(BigDecimal fine, BigDecimal interest) {}
}
