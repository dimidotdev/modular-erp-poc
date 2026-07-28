package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.produto.PackagingId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Lookup por {@link PackagingId} não encontrou embalagem no tenant atual. */
public class PackagingNotFoundException extends DomainException {

  private final PackagingId packagingId;

  public PackagingNotFoundException(PackagingId packagingId) {
    super(
        "produto.packaging-not-found",
        HttpStatus.NOT_FOUND,
        "Embalagem não encontrada",
        "Não existe embalagem com este id neste tenant.");
    this.packagingId = packagingId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("packagingId", packagingId.value().toString());
  }
}
