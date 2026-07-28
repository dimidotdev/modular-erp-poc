package dev.dimi.erp.ordemservico;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Item de OS (produto, serviço ou apontamento de mão-de-obra) referenciado por id não existe — ou a
 * OS referenciada não o possui. Cobre as três tabelas de item ({@code service_order_product},
 * {@code service_order_service}, {@code service_order_labor}).
 */
public class OsItemNotFoundException extends DomainException {

  private final String itemKind;
  private final UUID itemId;

  public OsItemNotFoundException(String itemKind, UUID itemId) {
    super(
        "os.item-not-found",
        HttpStatus.NOT_FOUND,
        "Item da OS não encontrado",
        "Não existe item do tipo '" + itemKind + "' com id " + itemId + " no tenant atual.");
    this.itemKind = itemKind;
    this.itemId = itemId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("itemKind", itemKind, "itemId", itemId);
  }
}
