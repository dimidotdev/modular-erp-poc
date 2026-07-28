package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Lookup por {@link BranchId} não encontrou filial no tenant atual. */
public class BranchNotFoundException extends DomainException {

  private final BranchId branchId;

  public BranchNotFoundException(BranchId branchId) {
    super(
        "cadastros.branch-not-found",
        HttpStatus.NOT_FOUND,
        "Filial não encontrada",
        "Não existe filial com este id neste tenant.");
    this.branchId = branchId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("branchId", branchId.value().toString());
  }
}
