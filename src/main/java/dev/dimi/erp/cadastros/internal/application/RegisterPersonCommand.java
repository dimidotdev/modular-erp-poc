package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.Modality;
import dev.dimi.erp.cadastros.PersonType;
import dev.dimi.erp.identidade.UserId;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Comando para registrar uma nova pessoa (PF ou PJ) no tenant atual.
 *
 * <p>Validações estruturais (tamanhos, formato de documento) são responsabilidade do DTO/Value
 * Object. O service valida as regras de negócio (RN-CAD-001/003) e a coerência {@code type} x
 * documento.
 *
 * @param type PF ou PJ — imutável após a criação (RN-CAD-003)
 * @param legalName nome (PF) ou razão social (PJ)
 * @param tradeName nome fantasia (opcional)
 * @param cpf CPF apenas dígitos (obrigatório quando {@code type=PF}; {@code null} caso contrário)
 * @param cnpj CNPJ apenas dígitos (obrigatório quando {@code type=PJ}; {@code null} caso contrário)
 * @param modalities modalidades a ativar no registro; pelo menos uma é obrigatória
 * @param registeredBy admin que disparou o registro (auditoria)
 */
public record RegisterPersonCommand(
    PersonType type,
    String legalName,
    String tradeName,
    String cpf,
    String cnpj,
    String rg,
    String ie,
    String im,
    boolean ieExempt,
    LocalDate birthDate,
    LocalDate foundationDate,
    String notes,
    UUID segmentId,
    UUID regionId,
    Set<Modality> modalities,
    UserId registeredBy) {

  public RegisterPersonCommand {
    if (type == null) {
      throw new IllegalArgumentException("type obrigatório");
    }
    if (legalName == null || legalName.isBlank()) {
      throw new IllegalArgumentException("legalName obrigatório");
    }
    if (modalities == null || modalities.isEmpty()) {
      throw new IllegalArgumentException("pessoa precisa de pelo menos uma modalidade");
    }
    modalities = Set.copyOf(modalities);
  }
}
