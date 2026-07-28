package dev.dimi.erp.cadastros;

import java.util.List;
import java.util.Optional;

/**
 * Porta de consulta pública de funcionários para outros módulos (movimentação para validar
 * vendedor/responsável; identidade para sincronizar status de {@code user_account}).
 *
 * <p>Implementação interna em {@code internal/application/EmployeeQueryService}.
 */
public interface EmployeeQueryPort {

  /** Retorna {@link EmployeeView} se existir. */
  Optional<EmployeeView> findById(EmployeeId id);

  /** Atalho para validar FK antes de persistir referência. */
  boolean exists(EmployeeId id);

  /**
   * Vínculo ativo da pessoa na filial (RN-CAD-010 garante no máximo 1). {@link Optional#empty} se
   * não existe ou já foi desligado.
   */
  Optional<EmployeeView> findActive(PersonId personId, BranchId branchId);

  /** Lista funcionários ativos de uma filial (para selects de UI). */
  List<EmployeeView> listActiveByBranch(BranchId branchId);
}
