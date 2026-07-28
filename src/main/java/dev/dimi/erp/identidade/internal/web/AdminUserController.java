package dev.dimi.erp.identidade.internal.web;

import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.identidade.StepUpPolicy;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.identidade.internal.application.UserManagementService;
import dev.dimi.erp.identidade.internal.web.dto.CreateUserApiRequest;
import dev.dimi.erp.identidade.internal.web.dto.DeactivateUserApiRequest;
import dev.dimi.erp.identidade.internal.web.dto.UserApiResponse;
import dev.dimi.erp.identidade.internal.web.dto.UserPageApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints administrativos de usuário ({@code /api/v1/admin/users/**}).
 *
 * <p>Todas as operações exigem autenticação JWT (filtro popula tenant + principal) e permission
 * granular via {@code @PreAuthorize}. Permissions usadas:
 *
 * <ul>
 *   <li>{@code usuario.consultar} — listar e ver detalhe.
 *   <li>{@code usuario.criar} — criar novo usuário.
 *   <li>{@code usuario.editar} — desativar e reativar.
 * </ul>
 *
 * <p>Operações de update de dados pessoais e troca de roles ficam para slice próprio (exigem
 * step-up).
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
class AdminUserController {

  private static final int MAX_PAGE_SIZE = 100;

  private final UserManagementService userService;
  private final StepUpPolicy stepUpPolicy;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('usuario.criar')")
  UserApiResponse create(
      @RequestBody @Valid CreateUserApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    if (body.roles().contains("OWNER")) {
      stepUpPolicy.requireFresh(principal);
    }
    return UserApiResponse.from(userService.create(body.toCommand(principal.userId())));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('usuario.consultar')")
  UserPageApiResponse list(
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(Math.max(0, page), clampPageSize(size));
    return UserPageApiResponse.from(userService.list(pageable));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('usuario.consultar')")
  UserApiResponse findById(@PathVariable("id") UUID id) {
    return UserApiResponse.from(userService.findById(new UserId(id)));
  }

  @PatchMapping("/{id}/desativar")
  @PreAuthorize("hasAuthority('usuario.editar')")
  UserApiResponse deactivate(
      @PathVariable("id") UUID id,
      @RequestBody @Valid DeactivateUserApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    stepUpPolicy.requireFresh(principal);
    return UserApiResponse.from(
        userService.deactivate(new UserId(id), body.reason(), principal.userId()));
  }

  @PatchMapping("/{id}/reativar")
  @PreAuthorize("hasAuthority('usuario.editar')")
  UserApiResponse reactivate(
      @PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
    return UserApiResponse.from(userService.reactivate(new UserId(id), principal.userId()));
  }

  private static int clampPageSize(int requested) {
    if (requested <= 0) return 20;
    return Math.min(requested, MAX_PAGE_SIZE);
  }
}
