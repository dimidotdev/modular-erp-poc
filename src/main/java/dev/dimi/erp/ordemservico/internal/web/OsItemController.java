package dev.dimi.erp.ordemservico.internal.web;

import dev.dimi.erp.cadastros.EmployeeId;
import dev.dimi.erp.estoque.MovementId;
import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.ordemservico.OsLaborId;
import dev.dimi.erp.ordemservico.OsProductId;
import dev.dimi.erp.ordemservico.OsServiceId;
import dev.dimi.erp.ordemservico.ServiceOrderId;
import dev.dimi.erp.ordemservico.internal.application.AddOsProductService;
import dev.dimi.erp.ordemservico.internal.application.AddOsServiceService;
import dev.dimi.erp.ordemservico.internal.application.AppointLaborService;
import dev.dimi.erp.ordemservico.internal.application.ConsumeOsProductService;
import dev.dimi.erp.ordemservico.internal.application.ServiceOrderQueryService;
import dev.dimi.erp.ordemservico.internal.domain.OsProduct;
import dev.dimi.erp.ordemservico.internal.domain.OsService;
import dev.dimi.erp.ordemservico.internal.persistence.OsProductRepository;
import dev.dimi.erp.ordemservico.internal.web.dto.AddItemResponse;
import dev.dimi.erp.ordemservico.internal.web.dto.AddProductRequest;
import dev.dimi.erp.ordemservico.internal.web.dto.AddServiceRequest;
import dev.dimi.erp.ordemservico.internal.web.dto.OsLaborApiResponse;
import dev.dimi.erp.ordemservico.internal.web.dto.OsProductApiResponse;
import dev.dimi.erp.ordemservico.internal.web.dto.OsServiceApiResponse;
import dev.dimi.erp.ordemservico.internal.web.dto.StartLaborRequest;
import dev.dimi.erp.produto.PackagingId;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST dos itens de OS — produtos, serviços e apontamentos de labor — sobre {@code
 * /api/v1/ordens-servico/{id}/**}.
 *
 * <p>Controller separado de {@code ServiceOrderController} (slice 9.1) para manter responsabilidade
 * por agregado e evitar inchar o controller de cabeçalho da OS. Cada endpoint resolve o ator via
 * {@code @AuthenticationPrincipal} e delega para o service correspondente.
 */
@RestController
@RequestMapping("/api/v1/ordens-servico")
@RequiredArgsConstructor
class OsItemController {

  private final AddOsProductService addProductService;
  private final ConsumeOsProductService consumeProductService;
  private final AddOsServiceService addServiceService;
  private final AppointLaborService appointLaborService;
  private final ServiceOrderQueryService queryService;
  private final OsProductRepository products;

  // ---------------- PRODUCTS ----------------

  @PostMapping("/{id}/products")
  @PreAuthorize("hasAuthority('os.editar')")
  public ResponseEntity<AddItemResponse> addProduct(
      @PathVariable("id") UUID id,
      @Valid @RequestBody AddProductRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    OsProductId productId =
        addProductService.addProduct(
            new ServiceOrderId(id),
            new PackagingId(body.packagingId()),
            body.quantity(),
            body.unitPrice(),
            body.discountValue(),
            principal.userId());
    int lineNumber = resolveLineNumber(productId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(AddItemResponse.ofProduct(productId, lineNumber));
  }

  @PostMapping("/{id}/products/{lineNumber}/consume")
  @PreAuthorize("hasAuthority('os.editar')")
  public ResponseEntity<ConsumeResponse> consumeProduct(
      @PathVariable("id") UUID id,
      @PathVariable("lineNumber") int lineNumber,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    MovementId movementId =
        consumeProductService.consume(new ServiceOrderId(id), lineNumber, principal.userId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new ConsumeResponse(movementId.value(), lineNumber));
  }

  @GetMapping("/{id}/products")
  @PreAuthorize("hasAuthority('os.consultar')")
  public List<OsProductApiResponse> listProducts(@PathVariable("id") UUID id) {
    return queryService.findProducts(new ServiceOrderId(id)).stream()
        .map(OsProductApiResponse::from)
        .toList();
  }

  // ---------------- SERVICES ----------------

  @PostMapping("/{id}/services")
  @PreAuthorize("hasAuthority('os.editar')")
  public ResponseEntity<AddItemResponse> addService(
      @PathVariable("id") UUID id,
      @Valid @RequestBody AddServiceRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    OsServiceId serviceId =
        addServiceService.addService(
            new ServiceOrderId(id),
            body.serviceCode(),
            body.description(),
            body.quantity(),
            body.unitPrice(),
            body.discountValue(),
            body.issqnRate(),
            principal.userId());
    int lineNumber = resolveServiceLineNumber(new ServiceOrderId(id), serviceId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(AddItemResponse.ofService(serviceId, lineNumber));
  }

  @GetMapping("/{id}/services")
  @PreAuthorize("hasAuthority('os.consultar')")
  public List<OsServiceApiResponse> listServices(@PathVariable("id") UUID id) {
    return queryService.findServices(new ServiceOrderId(id)).stream()
        .map(OsServiceApiResponse::from)
        .toList();
  }

  // ---------------- LABORS ----------------

  @PostMapping("/{id}/labors")
  @PreAuthorize("hasAuthority('os.apontar-mao-obra')")
  public ResponseEntity<AddItemResponse> startLabor(
      @PathVariable("id") UUID id,
      @Valid @RequestBody StartLaborRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    OsLaborId laborId =
        appointLaborService.start(
            new ServiceOrderId(id),
            new EmployeeId(body.employeeId()),
            body.description(),
            body.isBillable(),
            body.relatedServiceLine(),
            principal.userId());
    return ResponseEntity.status(HttpStatus.CREATED).body(AddItemResponse.ofLabor(laborId));
  }

  @PatchMapping("/{id}/labors/{laborId}/finish")
  @PreAuthorize("hasAuthority('os.apontar-mao-obra')")
  public ResponseEntity<Void> finishLabor(
      @PathVariable("id") UUID id,
      @PathVariable("laborId") UUID laborId,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    appointLaborService.finish(new OsLaborId(laborId), principal.userId());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/labors")
  @PreAuthorize("hasAuthority('os.consultar')")
  public List<OsLaborApiResponse> listLabors(@PathVariable("id") UUID id) {
    return queryService.findLabors(new ServiceOrderId(id)).stream()
        .map(OsLaborApiResponse::from)
        .toList();
  }

  // ---------------- Helpers ----------------

  /**
   * Resolve {@code lineNumber} pelo {@link OsProductId} recém-criado. {@code AddOsProductService}
   * só devolve o ID — buscar a entidade aqui evita expandir a assinatura do service só para o
   * response REST.
   */
  private int resolveLineNumber(OsProductId productId) {
    OsProduct p =
        products
            .findById(productId.value())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "OsProduct recém-criado não encontrado: " + productId.value()));
    return p.getLineNumber();
  }

  private int resolveServiceLineNumber(ServiceOrderId osId, OsServiceId serviceId) {
    List<OsService> all = queryService.findServices(osId);
    return all.stream()
        .filter(s -> s.getId().equals(serviceId.value()))
        .findFirst()
        .map(OsService::getLineNumber)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "OsService recém-criado não encontrado: " + serviceId.value()));
  }

  /** Resposta do {@code POST /products/{lineNumber}/consume} — devolve o movimento de estoque. */
  public record ConsumeResponse(UUID stockMovementId, int lineNumber) {}
}
