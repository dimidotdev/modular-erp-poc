package dev.dimi.erp.cadastros.internal.web.dto;

import dev.dimi.erp.cadastros.internal.application.PersonSummary;
import java.util.List;
import org.springframework.data.domain.Page;

/** Response paginada de pessoas. Mesmo formato do {@code UserPageApiResponse}. */
public record PersonPageApiResponse(
    List<PersonApiResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext) {

  public static PersonPageApiResponse from(Page<PersonSummary> page) {
    return new PersonPageApiResponse(
        page.map(PersonApiResponse::from).getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.hasNext());
  }
}
