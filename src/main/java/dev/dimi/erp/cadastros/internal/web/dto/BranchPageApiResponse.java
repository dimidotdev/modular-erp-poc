package dev.dimi.erp.cadastros.internal.web.dto;

import dev.dimi.erp.cadastros.internal.application.BranchSummary;
import java.util.List;
import org.springframework.data.domain.Page;

/** Response paginada de filiais. */
public record BranchPageApiResponse(
    List<BranchApiResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext) {

  public static BranchPageApiResponse from(Page<BranchSummary> page) {
    return new BranchPageApiResponse(
        page.map(BranchApiResponse::from).getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.hasNext());
  }
}
