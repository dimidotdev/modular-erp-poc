package dev.dimi.erp.cadastros.internal.web.dto;

import dev.dimi.erp.cadastros.internal.application.EmployeeSummary;
import java.util.List;
import org.springframework.data.domain.Page;

/** Response paginada de funcionários. */
public record EmployeePageApiResponse(
    List<EmployeeApiResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext) {

  public static EmployeePageApiResponse from(Page<EmployeeSummary> page) {
    return new EmployeePageApiResponse(
        page.map(EmployeeApiResponse::from).getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.hasNext());
  }
}
