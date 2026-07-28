package dev.dimi.erp.identidade.internal.web.dto;

import dev.dimi.erp.identidade.internal.application.UserSummary;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Response paginada de usuários. Formato simples por enquanto (offset-based via {@link
 * org.springframework.data.domain.Pageable}). Migrar para cursor-based quando uma listagem real
 * passar de algumas centenas de usuários.
 */
public record UserPageApiResponse(
    List<UserApiResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext) {

  public static UserPageApiResponse from(Page<UserSummary> page) {
    return new UserPageApiResponse(
        page.map(UserApiResponse::from).getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.hasNext());
  }
}
