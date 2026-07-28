package dev.dimi.erp.ordemservico;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Tentativa de consumir item de produto que já foi consumido — {@code consumed_at} não é nulo e
 * {@code stock_movement_id} já aponta para a movimentação OUTBOUND no estoque. Consumo é
 * idempotente do ponto de vista do cliente (mesma resposta 422 + propriedades), mas a operação não
 * dispara um segundo OUTBOUND.
 */
public class OsProductAlreadyConsumedException extends DomainException {

  private final OsProductId productId;

  public OsProductAlreadyConsumedException(OsProductId productId) {
    super(
        "os.product-already-consumed",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Item já foi consumido",
        "Item de produto " + productId.value() + " já teve estoque baixado anteriormente.");
    this.productId = productId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("productId", productId.value());
  }
}
