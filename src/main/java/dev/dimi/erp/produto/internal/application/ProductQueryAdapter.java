package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.produto.PackagingId;
import dev.dimi.erp.produto.ProductQueryPort;
import dev.dimi.erp.produto.internal.persistence.PackagingRepository;
import dev.dimi.erp.produto.internal.persistence.ProductRepository;
import dev.dimi.erp.produto.internal.persistence.VariantRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementação do {@link ProductQueryPort} — adapter que outros módulos (fiscal, estoque,
 * movimentação) consomem para obter dados de produto/embalagem sem violar fronteiras de módulo.
 *
 * <p>Faz três loads sequenciais (packaging → variant → product) em vez de um join porque o volume
 * por chamada é 1 registro e a transação é readOnly; o custo extra é desprezível e mantém o domínio
 * limpo (sem agregados navegando entidades irmãs).
 */
@Component
@RequiredArgsConstructor
class ProductQueryAdapter implements ProductQueryPort {

  private final PackagingRepository packagings;
  private final VariantRepository variants;
  private final ProductRepository products;

  @Override
  @Transactional(readOnly = true)
  public Optional<PackagingSnapshot> findPackaging(PackagingId packagingId) {
    return packagings
        .findById(packagingId.value())
        .flatMap(
            p ->
                variants
                    .findById(p.getVariantId())
                    .flatMap(
                        v ->
                            products
                                .findById(v.getProductId())
                                .map(
                                    prod ->
                                        new PackagingSnapshot(
                                            p.id(),
                                            prod.id(),
                                            p.getSku(),
                                            p.getBarcode(),
                                            prod.getName(),
                                            prod.getClassification(),
                                            prod.getNcm(),
                                            prod.getCest(),
                                            prod.getOrigin(),
                                            p.isActive() && prod.isActive() && v.isActive()))));
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isActivePackaging(PackagingId packagingId) {
    return findPackaging(packagingId).map(PackagingSnapshot::active).orElse(false);
  }
}
