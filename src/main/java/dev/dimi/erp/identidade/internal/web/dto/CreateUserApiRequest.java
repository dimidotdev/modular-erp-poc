package dev.dimi.erp.identidade.internal.web.dto;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.identidade.internal.application.CreateUserCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request body do {@code POST /api/v1/admin/users}.
 *
 * <p>{@code password} é validado pelo {@code PasswordPolicyValidator} no service — aqui só
 * checagens estruturais. {@code roles} usa códigos ({@code OWNER}, {@code VENDEDOR}, etc), não IDs:
 * mais legível e estável entre tenants.
 */
public record CreateUserApiRequest(
    @NotBlank
        @Size(min = 3, max = 64)
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9._-]+$", message = "username inválido")
        String username,
    @NotBlank @Email @Size(max = 200) String email,
    @NotBlank @Size(max = 200) String fullName,
    @NotBlank @Size(min = 12, max = 200) String password,
    @NotEmpty List<@NotBlank @Size(max = 64) String> roles,
    Boolean mustChangePassword) {

  public CreateUserCommand toCommand(UserId createdBy) {
    return new CreateUserCommand(
        username,
        email,
        fullName,
        password,
        roles,
        mustChangePassword == null || mustChangePassword,
        createdBy);
  }
}
