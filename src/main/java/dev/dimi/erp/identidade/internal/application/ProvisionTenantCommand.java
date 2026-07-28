package dev.dimi.erp.identidade.internal.application;

/**
 * Dados necessários para criar um tenant novo + seu primeiro usuário OWNER.
 *
 * <p>{@code slug} identifica URL/schema; {@code displayName} aparece em UI; {@code legalName} é a
 * razão social. {@code ownerUsername/Email/FullName} dão origem ao primeiro user.
 */
public record ProvisionTenantCommand(
    String slug,
    String displayName,
    String legalName,
    String cnpj,
    String plan,
    String ownerUsername,
    String ownerEmail,
    String ownerFullName) {

  public ProvisionTenantCommand {
    if (slug == null || slug.isBlank()) throw new IllegalArgumentException("slug obrigatório");
    if (displayName == null || displayName.isBlank())
      throw new IllegalArgumentException("displayName obrigatório");
    if (legalName == null || legalName.isBlank())
      throw new IllegalArgumentException("legalName obrigatório");
    if (ownerUsername == null || ownerUsername.isBlank())
      throw new IllegalArgumentException("ownerUsername obrigatório");
    if (ownerEmail == null || ownerEmail.isBlank())
      throw new IllegalArgumentException("ownerEmail obrigatório");
    if (ownerFullName == null || ownerFullName.isBlank())
      throw new IllegalArgumentException("ownerFullName obrigatório");
  }
}
