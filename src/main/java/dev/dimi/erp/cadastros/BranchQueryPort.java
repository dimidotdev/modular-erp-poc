package dev.dimi.erp.cadastros;

import java.util.List;
import java.util.Optional;

/**
 * Porta de consulta pública de filiais para outros módulos (movimentação, estoque, fiscal).
 *
 * <p>O módulo fiscal consome principalmente {@link #findById} para resolver dados de NFe e {@link
 * #canEmitInvoice} como guarda — sem {@code branch_fiscal} a filial existe mas não pode emitir
 * notas (RN-CAD-006).
 */
public interface BranchQueryPort {

  /** Retorna {@link BranchView} se existir no tenant atual. */
  Optional<BranchView> findById(BranchId id);

  /** Atalho para validar FK antes de persistir referência. */
  boolean exists(BranchId id);

  /** Filial matriz do tenant; {@link Optional#empty} apenas em tenants mal-provisionados. */
  Optional<BranchView> findMain();

  /** Lista filiais ativas para uso em selects de UI ou validações. */
  List<BranchView> listActive();

  /**
   * {@code true} se a filial existe, está ativa e tem {@code branch_fiscal} configurado. Pré-
   * condição para o módulo fiscal aceitar emissão de NFe/NFCe (RN-CAD-006).
   */
  boolean canEmitInvoice(BranchId id);
}
