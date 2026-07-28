package dev.dimi.erp.cadastros;

import java.util.Set;

/**
 * Projeção pública de {@code Person} para consumo cross-module via {@link PersonQueryPort}.
 *
 * <p>Sem dados sensíveis: birth/foundation date, IE/IM, endereços e contatos só são acessíveis
 * dentro do módulo cadastros. Documento ({@code cpf} ou {@code cnpj}, conforme {@link PersonType})
 * é exposto porque módulos fiscal/financeiro precisam dele para emitir notas e gerar boletos.
 *
 * <p>{@code anonymized=true} sinaliza que a pessoa foi anonimizada por LGPD (RN-CAD-011) — clientes
 * devem evitar mostrar dados que não existem mais.
 *
 * @param id identificador tipado
 * @param type PF ou PJ
 * @param legalName nome civil (PF) ou razão social (PJ)
 * @param tradeName apelido (PF) ou nome fantasia (PJ); pode ser {@code null}
 * @param document CPF (PF) ou CNPJ (PJ); pode ser {@code null} se anonimizada
 * @param active se a pessoa está ativa
 * @param anonymized se a pessoa foi anonimizada (LGPD)
 * @param activeModalities papéis ativos (CUSTOMER, SUPPLIER, CARRIER, SELLER)
 */
public record PersonView(
    PersonId id,
    PersonType type,
    String legalName,
    String tradeName,
    String document,
    boolean active,
    boolean anonymized,
    Set<Modality> activeModalities) {}
