package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Operação solicitada conflita com o estado atual da filial.
 *
 * <p>Exemplos: tentar desativar a única filial matriz; tentar reativar filial já ativa; tentar
 * desmarcar matriz sem promover outra antes.
 */
public class InvalidBranchStateException extends DomainException {

  private final BranchId branchId;
  private final String reason;

  public InvalidBranchStateException(BranchId branchId, String reason, String detail) {
    super("cadastros.invalid-branch-state", HttpStatus.CONFLICT, "Estado inválido", detail);
    this.branchId = branchId;
    this.reason = reason;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of(
        "branchId", branchId == null ? "" : branchId.value().toString(), "reason", reason);
  }
}
