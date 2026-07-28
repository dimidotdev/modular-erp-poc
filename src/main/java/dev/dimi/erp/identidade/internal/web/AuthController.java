package dev.dimi.erp.identidade.internal.web;

import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.identidade.internal.application.LoginService;
import dev.dimi.erp.identidade.internal.application.RefreshTokenService;
import dev.dimi.erp.identidade.internal.application.StepUpService;
import dev.dimi.erp.identidade.internal.web.dto.LoginApiRequest;
import dev.dimi.erp.identidade.internal.web.dto.LoginApiResponse;
import dev.dimi.erp.identidade.internal.web.dto.LogoutApiRequest;
import dev.dimi.erp.identidade.internal.web.dto.MeApiResponse;
import dev.dimi.erp.identidade.internal.web.dto.RefreshApiRequest;
import dev.dimi.erp.identidade.internal.web.dto.StepUpApiRequest;
import dev.dimi.erp.identidade.internal.web.dto.StepUpApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de autenticação ({@code /api/v1/auth/**}).
 *
 * <p>v1: {@code /login}, {@code /refresh}, {@code /logout}, {@code /logout-all}. MFA e step-up
 * entram nas próximas etapas do slice identidade.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
class AuthController {

  private final LoginService loginService;
  private final RefreshTokenService refreshService;
  private final StepUpService stepUpService;

  @GetMapping("/me")
  MeApiResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
    return MeApiResponse.from(principal);
  }

  @PostMapping("/login")
  LoginApiResponse login(@RequestBody @Valid LoginApiRequest body, HttpServletRequest request) {
    return LoginApiResponse.from(loginService.authenticate(body.toCommand(request)));
  }

  @PostMapping("/refresh")
  LoginApiResponse refresh(@RequestBody @Valid RefreshApiRequest body, HttpServletRequest request) {
    return LoginApiResponse.from(refreshService.rotate(body.toCommand(request)));
  }

  @PostMapping("/step-up")
  StepUpApiResponse stepUp(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestBody @Valid StepUpApiRequest body,
      HttpServletRequest request) {
    return StepUpApiResponse.from(stepUpService.stepUp(body.toCommand(principal, request)));
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void logout(@RequestBody @Valid LogoutApiRequest body, HttpServletRequest request) {
    refreshService.logout(body.toCommand(false), ClientRequestMetadata.clientIp(request));
  }

  @PostMapping("/logout-all")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void logoutAll(@RequestBody @Valid LogoutApiRequest body, HttpServletRequest request) {
    refreshService.logout(body.toCommand(true), ClientRequestMetadata.clientIp(request));
  }
}
