package dev.dimi.erp.cadastros;

import java.time.LocalDate;

/**
 * Projeção pública de {@code Employee} para consumo cross-module via {@link EmployeeQueryPort}.
 *
 * <p>Útil para validar autoria/responsabilidade em movimentos (ex: vendedor de uma venda precisa
 * ser funcionário ativo da filial onde a venda foi registrada).
 *
 * @param id identificador tipado
 * @param personId pessoa vinculada
 * @param branchId filial onde o vínculo é ativo
 * @param hiredAt data de admissão
 * @param terminatedAt data de desligamento; {@code null} se ainda ativo
 * @param active conveniência == {@code terminatedAt == null}
 */
public record EmployeeView(
    EmployeeId id,
    PersonId personId,
    BranchId branchId,
    LocalDate hiredAt,
    LocalDate terminatedAt,
    boolean active) {}
