package dev.dimi.erp.compras.internal.web;

import dev.dimi.erp.compras.PurchaseReceiptId;
import dev.dimi.erp.compras.internal.application.ReceivePurchaseOrderService;
import dev.dimi.erp.compras.internal.web.dto.PurchaseReceiptApiResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de consulta de {@code purchase_receipt} ({@code /api/v1/compras/receipts/**}). POST de
 * criação fica em {@code /api/v1/compras/orders/{id}/receive} (controller de orders) — receipt
 * existe sempre referenciando um PC.
 */
@RestController
@RequestMapping("/api/v1/compras/receipts")
@RequiredArgsConstructor
class PurchaseReceiptController {

  private final ReceivePurchaseOrderService receipts;

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('compra.consultar')")
  PurchaseReceiptApiResponse findById(@PathVariable("id") UUID id) {
    return PurchaseReceiptApiResponse.from(receipts.findById(new PurchaseReceiptId(id)));
  }
}
