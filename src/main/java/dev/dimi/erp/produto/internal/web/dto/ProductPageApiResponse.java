package dev.dimi.erp.produto.internal.web.dto;

import dev.dimi.erp.produto.internal.application.ProductSummary;
import java.util.List;
import org.springframework.data.domain.Page;

/** Response paginada de produtos. */
public record ProductPageApiResponse(
    List<ProductApiResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext) {

  public static ProductPageApiResponse from(Page<ProductSummary> page) {
    return new ProductPageApiResponse(
        page.map(ProductApiResponse::from).getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.hasNext());
  }
}
