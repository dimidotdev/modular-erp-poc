package dev.dimi.erp.cadastros;

import java.util.Optional;

/**
 * Porta de consulta pública de pessoas para outros módulos (produto, estoque, movimentação,
 * financeiro, fiscal, OS, contratos).
 *
 * <p>Implementação interna em {@code internal/application/PersonQueryService}. Consumidores
 * dependem apenas desta interface — entidades JPA, repositórios e DTOs internos não vazam.
 */
public interface PersonQueryPort {

  /** Retorna {@link PersonView} se existir no tenant atual. */
  Optional<PersonView> findById(PersonId id);

  /** Atalho para validar FK antes de persistir referência. */
  boolean exists(PersonId id);

  /**
   * {@code true} se a pessoa existe, está ativa e não foi anonimizada — pré-condição para usá-la em
   * novas operações comerciais (venda, contrato, contratação como funcionário).
   */
  boolean isActive(PersonId id);
}
