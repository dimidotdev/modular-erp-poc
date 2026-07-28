package dev.dimi.erp.identidade.internal.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Rastreia tentativas de login por {@code (tenantSlug, username)} e por IP, aplicando lockout
 * progressivo com backoff exponencial.
 *
 * <p>Política conforme {@code docs/architecture/02-seguranca.md}:
 *
 * <ul>
 *   <li>Usuário: 10 falhas em 15 min → lockout 15 min. Backoff: 15min → 30min → 1h → 2h → 4h → 8h →
 *       24h (cap).
 *   <li>IP: 100 falhas em 1h → lockout 24h.
 * </ul>
 *
 * <p>Implementação v1: Caffeine in-process. Estado se perde em restart (aceitável; histórico
 * permanente fica em {@code login_attempt}). v1.1: migrar para Redis distribuído quando houver mais
 * de uma instância da app.
 *
 * <p>Sucessos resetam o contador de usuário (mas não o de IP — atacante poderia mascarar com login
 * válido entre tentativas).
 */
@Component
public class LoginLockoutTracker {

  static final int MAX_FAILURES_PER_USER = 10;
  static final Duration USER_WINDOW = Duration.ofMinutes(15);
  static final int MAX_FAILURES_PER_IP = 100;
  static final Duration IP_WINDOW = Duration.ofHours(1);
  static final Duration BASE_LOCKOUT = Duration.ofMinutes(15);
  static final Duration MAX_LOCKOUT = Duration.ofHours(24);

  private final Cache<String, UserState> userCache;
  private final Cache<String, IpState> ipCache;

  public LoginLockoutTracker() {
    this.userCache =
        Caffeine.newBuilder().expireAfterWrite(MAX_LOCKOUT).maximumSize(100_000).build();
    this.ipCache =
        Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(24)).maximumSize(100_000).build();
  }

  /**
   * Verifica se há lockout ativo. Retorna a duração restante ({@code Retry-After}) se bloqueado;
   * vazio se OK.
   */
  public Optional<Duration> checkLockout(String tenantSlug, String username, String ip) {
    Instant now = Instant.now();
    UserState us = userCache.getIfPresent(userKey(tenantSlug, username));
    if (us != null && us.lockedUntil != null && now.isBefore(us.lockedUntil)) {
      return Optional.of(Duration.between(now, us.lockedUntil));
    }
    IpState ips = ipCache.getIfPresent(ip);
    if (ips != null && ips.lockedUntil != null && now.isBefore(ips.lockedUntil)) {
      return Optional.of(Duration.between(now, ips.lockedUntil));
    }
    return Optional.empty();
  }

  /** Registra uma falha de login. Pode disparar lockout se atingir threshold. */
  public void recordFailure(String tenantSlug, String username, String ip) {
    Instant now = Instant.now();
    userCache.asMap().compute(userKey(tenantSlug, username), (k, v) -> nextUserState(v, now));
    ipCache.asMap().compute(ip, (k, v) -> nextIpState(v, now));
  }

  /** Registra sucesso — reseta contador do usuário (IP é preservado). */
  public void recordSuccess(String tenantSlug, String username) {
    userCache.invalidate(userKey(tenantSlug, username));
  }

  private static UserState nextUserState(UserState current, Instant now) {
    if (current == null) {
      return new UserState(now, 1, null, 0);
    }
    // Lockout já expirou? Reset contador, mas mantém lockoutCount para backoff progressivo.
    if (current.lockedUntil != null && !now.isBefore(current.lockedUntil)) {
      return new UserState(now, 1, null, current.lockoutCount);
    }
    // Janela expirou? Recomeça do zero.
    if (Duration.between(current.windowStart, now).compareTo(USER_WINDOW) > 0) {
      return new UserState(now, 1, null, current.lockoutCount);
    }
    int newCount = current.count + 1;
    if (newCount >= MAX_FAILURES_PER_USER) {
      int newLockoutCount = current.lockoutCount + 1;
      Instant until = now.plus(computeBackoff(newLockoutCount));
      return new UserState(current.windowStart, newCount, until, newLockoutCount);
    }
    return new UserState(current.windowStart, newCount, null, current.lockoutCount);
  }

  private static IpState nextIpState(IpState current, Instant now) {
    if (current == null) {
      return new IpState(now, 1, null);
    }
    if (Duration.between(current.windowStart, now).compareTo(IP_WINDOW) > 0) {
      return new IpState(now, 1, null);
    }
    int newCount = current.count + 1;
    if (newCount >= MAX_FAILURES_PER_IP) {
      Instant until = now.plus(Duration.ofHours(24));
      return new IpState(current.windowStart, newCount, until);
    }
    return new IpState(current.windowStart, newCount, null);
  }

  /**
   * Backoff exponencial 2^(n-1) × {@code BASE_LOCKOUT}, com teto em {@code MAX_LOCKOUT}.
   *
   * <p>1: 15min, 2: 30min, 3: 1h, 4: 2h, 5: 4h, 6: 8h, 7+: 24h.
   */
  private static Duration computeBackoff(int lockoutCount) {
    if (lockoutCount <= 0) return BASE_LOCKOUT;
    if (lockoutCount > 7) return MAX_LOCKOUT;
    Duration d = BASE_LOCKOUT.multipliedBy(1L << (lockoutCount - 1));
    return d.compareTo(MAX_LOCKOUT) > 0 ? MAX_LOCKOUT : d;
  }

  private static String userKey(String tenantSlug, String username) {
    return tenantSlug.toLowerCase() + ":" + username.toLowerCase();
  }

  record UserState(Instant windowStart, int count, Instant lockedUntil, int lockoutCount) {}

  record IpState(Instant windowStart, int count, Instant lockedUntil) {}
}
