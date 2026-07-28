package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.identidade.UserId;
import java.util.UUID;

/**
 * Comando para registrar uma nova filial no tenant atual.
 *
 * <p>{@code isMain=true} desmarca automaticamente a matriz anterior (RN-CAD-014). {@code addressId}
 * é opcional — pode ser definido depois via update.
 */
public record RegisterBranchCommand(
    String code, String name, boolean isMain, UUID addressId, UserId registeredBy) {

  public RegisterBranchCommand {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("code obrigatório");
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name obrigatório");
    }
  }
}
