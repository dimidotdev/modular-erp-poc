package dev.dimi.erp.auditoria.internal.listener;

import dev.dimi.erp.auditoria.AuditCategory;
import dev.dimi.erp.auditoria.AuditSeverity;
import dev.dimi.erp.auditoria.internal.application.AuditEventRecord;
import dev.dimi.erp.auditoria.internal.application.AuditWriter;
import dev.dimi.erp.estoque.StockAdjustmentRecordedEvent;
import dev.dimi.erp.estoque.StockLowAlertEvent;
import dev.dimi.erp.estoque.StockMovementRecordedEvent;
import dev.dimi.erp.estoque.StockReservationReleasedEvent;
import dev.dimi.erp.estoque.StockReservedEvent;
import dev.dimi.erp.movimentacao.InventoryAdjustmentRecordedEvent;
import dev.dimi.erp.movimentacao.OutboundVoucherIssuedEvent;
import dev.dimi.erp.movimentacao.SaleClosedEvent;
import dev.dimi.erp.movimentacao.SaleOpenedEvent;
import dev.dimi.erp.movimentacao.SaleReturnClosedEvent;
import dev.dimi.erp.movimentacao.SalesOrderApprovedEvent;
import dev.dimi.erp.movimentacao.SalesOrderConvertedEvent;
import dev.dimi.erp.movimentacao.SalesOrderCreatedEvent;
import dev.dimi.erp.movimentacao.StockTransferClosedEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Persiste eventos da categoria {@code STOCK} (módulos {@code estoque} e {@code movimentacao}).
 *
 * <p>Movimentos rotineiros (entrada/saída via venda/compra) ficam em {@code INFO}; ajuste manual,
 * cancelamento e alerta de saldo baixo sobem para {@code WARN} — exigem revisão posterior. Eventos
 * da {@code movimentacao} (venda, devolução, transferência, OutboundVoucher) caem em categoria
 * {@code STOCK} porque o efeito auditável principal é a movimentação de saldo.
 */
@Component
@Slf4j
@RequiredArgsConstructor
class StockAuditListener {

  private final AuditWriter writer;

  // ---------- estoque ----------

  @ApplicationModuleListener
  void on(StockMovementRecordedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("movementId", event.movementId().value().toString());
      payload.put("packagingId", event.packagingId().value().toString());
      payload.put("branchId", event.branchId().value().toString());
      payload.put("direction", event.direction().name());
      payload.put("quantity", event.quantity() == null ? null : event.quantity().toPlainString());
      payload.put("unitCost", event.unitCost() == null ? null : event.unitCost().toPlainString());
      payload.put("originType", event.originType());
      payload.put("originId", event.originId() == null ? null : event.originId().toString());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.recordedBy() == null ? null : event.recordedBy().value(),
              null,
              event.occurredAt(),
              "STOCK_MOVEMENT_RECORDED",
              AuditCategory.STOCK,
              AuditSeverity.INFO,
              "stock_movement",
              event.movementId().value().toString(),
              null,
              null,
              null,
              "Movimento "
                  + event.direction().name()
                  + " quantidade "
                  + (event.quantity() == null ? "0" : event.quantity().toPlainString()),
              payload));
    } catch (RuntimeException e) {
      log.error(
          "falha gravando audit STOCK_MOVEMENT_RECORDED movementId={}",
          event.movementId().value(),
          e);
    }
  }

  @ApplicationModuleListener
  void on(StockAdjustmentRecordedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("movementId", event.movementId().value().toString());
      payload.put("delta", event.delta() == null ? null : event.delta().toPlainString());
      payload.put("reason", event.reason());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.actor() == null ? null : event.actor().value(),
              null,
              event.occurredAt(),
              "STOCK_ADJUSTED",
              AuditCategory.STOCK,
              AuditSeverity.WARN,
              "stock_movement",
              event.movementId().value().toString(),
              null,
              null,
              null,
              "Ajuste manual de estoque ("
                  + (event.delta() == null ? "0" : event.delta().toPlainString())
                  + "): "
                  + event.reason(),
              payload));
    } catch (RuntimeException e) {
      log.error("falha gravando audit STOCK_ADJUSTED movementId={}", event.movementId().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(StockReservedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("packagingId", event.packagingId().value().toString());
      payload.put("branchId", event.branchId().value().toString());
      payload.put("quantity", event.quantity() == null ? null : event.quantity().toPlainString());
      payload.put("originType", event.originType());
      payload.put("originId", event.originId() == null ? null : event.originId().toString());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              null,
              null,
              event.occurredAt(),
              "STOCK_RESERVED",
              AuditCategory.STOCK,
              AuditSeverity.INFO,
              "stock_reservation",
              event.originId() == null ? null : event.originId().toString(),
              null,
              null,
              null,
              "Estoque reservado — quantidade "
                  + (event.quantity() == null ? "0" : event.quantity().toPlainString()),
              payload));
    } catch (RuntimeException e) {
      log.error(
          "falha gravando audit STOCK_RESERVED packagingId={}", event.packagingId().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(StockReservationReleasedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("packagingId", event.packagingId().value().toString());
      payload.put("branchId", event.branchId().value().toString());
      payload.put("quantity", event.quantity() == null ? null : event.quantity().toPlainString());
      payload.put("originType", event.originType());
      payload.put("originId", event.originId() == null ? null : event.originId().toString());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              null,
              null,
              event.occurredAt(),
              "STOCK_RESERVATION_RELEASED",
              AuditCategory.STOCK,
              AuditSeverity.INFO,
              "stock_reservation",
              event.originId() == null ? null : event.originId().toString(),
              null,
              null,
              null,
              "Reserva de estoque liberada — quantidade "
                  + (event.quantity() == null ? "0" : event.quantity().toPlainString()),
              payload));
    } catch (RuntimeException e) {
      log.error(
          "falha gravando audit STOCK_RESERVATION_RELEASED packagingId={}",
          event.packagingId().value(),
          e);
    }
  }

  @ApplicationModuleListener
  void on(StockLowAlertEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("packagingId", event.packagingId().value().toString());
      payload.put("branchId", event.branchId().value().toString());
      payload.put(
          "quantityOnHand",
          event.quantityOnHand() == null ? null : event.quantityOnHand().toPlainString());
      payload.put(
          "threshold", event.threshold() == null ? null : event.threshold().toPlainString());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              null,
              null,
              event.occurredAt(),
              "STOCK_LOW_ALERT",
              AuditCategory.STOCK,
              AuditSeverity.WARN,
              "stock_balance",
              event.packagingId().value().toString(),
              null,
              null,
              null,
              "Saldo baixo — on hand "
                  + (event.quantityOnHand() == null ? "0" : event.quantityOnHand().toPlainString())
                  + " < threshold "
                  + (event.threshold() == null ? "0" : event.threshold().toPlainString()),
              payload));
    } catch (RuntimeException e) {
      log.error(
          "falha gravando audit STOCK_LOW_ALERT packagingId={}", event.packagingId().value(), e);
    }
  }

  // ---------- movimentacao ----------

  @ApplicationModuleListener
  void on(SalesOrderCreatedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("salesOrderId", event.salesOrderId().value().toString());
      payload.put("branchId", event.branchId().value().toString());
      payload.put("customerId", event.customerId().value().toString());
      payload.put("number", event.number());
      payload.put("total", event.total() == null ? null : event.total().toPlainString());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.createdBy() == null ? null : event.createdBy().value(),
              null,
              event.occurredAt(),
              "SALES_ORDER_CREATED",
              AuditCategory.STOCK,
              AuditSeverity.INFO,
              "sales_order",
              event.salesOrderId().value().toString(),
              null,
              null,
              null,
              "Pedido " + event.number() + " criado",
              payload));
    } catch (RuntimeException e) {
      log.error(
          "falha gravando audit SALES_ORDER_CREATED salesOrderId={}",
          event.salesOrderId().value(),
          e);
    }
  }

  @ApplicationModuleListener
  void on(SalesOrderApprovedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("salesOrderId", event.salesOrderId().value().toString());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.approvedBy() == null ? null : event.approvedBy().value(),
              null,
              event.occurredAt(),
              "SALES_ORDER_APPROVED",
              AuditCategory.STOCK,
              AuditSeverity.INFO,
              "sales_order",
              event.salesOrderId().value().toString(),
              null,
              null,
              null,
              "Pedido aprovado",
              payload));
    } catch (RuntimeException e) {
      log.error(
          "falha gravando audit SALES_ORDER_APPROVED salesOrderId={}",
          event.salesOrderId().value(),
          e);
    }
  }

  @ApplicationModuleListener
  void on(SalesOrderConvertedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("salesOrderId", event.salesOrderId().value().toString());
      payload.put("saleId", event.saleId().value().toString());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.convertedBy() == null ? null : event.convertedBy().value(),
              null,
              event.occurredAt(),
              "SALES_ORDER_CONVERTED",
              AuditCategory.STOCK,
              AuditSeverity.INFO,
              "sales_order",
              event.salesOrderId().value().toString(),
              null,
              null,
              null,
              "Pedido convertido em venda " + event.saleId().value(),
              payload));
    } catch (RuntimeException e) {
      log.error(
          "falha gravando audit SALES_ORDER_CONVERTED salesOrderId={}",
          event.salesOrderId().value(),
          e);
    }
  }

  @ApplicationModuleListener
  void on(SaleOpenedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("saleId", event.saleId().value().toString());
      payload.put("branchId", event.branchId().value().toString());
      payload.put("customerId", event.customerId().value().toString());
      payload.put(
          "originSalesOrderId",
          event.originSalesOrderId() == null
              ? null
              : event.originSalesOrderId().value().toString());
      payload.put("number", event.number());
      payload.put("total", event.total() == null ? null : event.total().toPlainString());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.openedBy() == null ? null : event.openedBy().value(),
              null,
              event.occurredAt(),
              "SALE_OPENED",
              AuditCategory.STOCK,
              AuditSeverity.INFO,
              "sale",
              event.saleId().value().toString(),
              null,
              null,
              null,
              "Venda " + event.number() + " aberta",
              payload));
    } catch (RuntimeException e) {
      log.error("falha gravando audit SALE_OPENED saleId={}", event.saleId().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(SaleClosedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("saleId", event.saleId().value().toString());
      payload.put("branchId", event.branchId().value().toString());
      payload.put("customerId", event.customerId().value().toString());
      payload.put(
          "paymentConditionId",
          event.paymentConditionId() == null ? null : event.paymentConditionId().toString());
      payload.put(
          "totalGross", event.totalGross() == null ? null : event.totalGross().toPlainString());
      payload.put(
          "totalDiscount",
          event.totalDiscount() == null ? null : event.totalDiscount().toPlainString());
      payload.put(
          "totalFreight",
          event.totalFreight() == null ? null : event.totalFreight().toPlainString());
      payload.put("total", event.total() == null ? null : event.total().toPlainString());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.closedBy() == null ? null : event.closedBy().value(),
              null,
              event.occurredAt(),
              "SALE_CLOSED",
              AuditCategory.STOCK,
              AuditSeverity.INFO,
              "sale",
              event.saleId().value().toString(),
              null,
              null,
              null,
              "Venda fechada — total "
                  + (event.total() == null ? "0" : event.total().toPlainString()),
              payload));
    } catch (RuntimeException e) {
      log.error("falha gravando audit SALE_CLOSED saleId={}", event.saleId().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(SaleReturnClosedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("returnId", event.returnId().value().toString());
      payload.put("saleId", event.saleId().value().toString());
      payload.put("branchId", event.branchId().value().toString());
      payload.put(
          "refundValue", event.refundValue() == null ? null : event.refundValue().toPlainString());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.closedBy() == null ? null : event.closedBy().value(),
              null,
              event.occurredAt(),
              "SALE_RETURN_CLOSED",
              AuditCategory.STOCK,
              AuditSeverity.WARN,
              "sale_return",
              event.returnId().value().toString(),
              null,
              null,
              null,
              "Devolução fechada — valor "
                  + (event.refundValue() == null ? "0" : event.refundValue().toPlainString()),
              payload));
    } catch (RuntimeException e) {
      log.error("falha gravando audit SALE_RETURN_CLOSED returnId={}", event.returnId().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(StockTransferClosedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("transferId", event.transferId().value().toString());
      payload.put("originBranchId", event.originBranchId().value().toString());
      payload.put("destBranchId", event.destBranchId().value().toString());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.closedBy() == null ? null : event.closedBy().value(),
              null,
              event.occurredAt(),
              "STOCK_TRANSFER_CLOSED",
              AuditCategory.STOCK,
              AuditSeverity.INFO,
              "stock_transfer",
              event.transferId().value().toString(),
              null,
              null,
              null,
              "Transferência fechada de "
                  + event.originBranchId().value()
                  + " para "
                  + event.destBranchId().value(),
              payload));
    } catch (RuntimeException e) {
      log.error(
          "falha gravando audit STOCK_TRANSFER_CLOSED transferId={}",
          event.transferId().value(),
          e);
    }
  }

  @ApplicationModuleListener
  void on(InventoryAdjustmentRecordedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("adjustmentId", event.adjustmentId().value().toString());
      payload.put("branchId", event.branchId().value().toString());
      payload.put("linesCount", event.linesCount());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.recordedBy() == null ? null : event.recordedBy().value(),
              null,
              event.occurredAt(),
              "INVENTORY_ADJUSTMENT_RECORDED",
              AuditCategory.STOCK,
              AuditSeverity.WARN,
              "inventory_adjustment",
              event.adjustmentId().value().toString(),
              null,
              null,
              null,
              "Ajuste de inventário registrado — " + event.linesCount() + " linha(s)",
              payload));
    } catch (RuntimeException e) {
      log.error(
          "falha gravando audit INVENTORY_ADJUSTMENT_RECORDED adjustmentId={}",
          event.adjustmentId().value(),
          e);
    }
  }

  @ApplicationModuleListener
  void on(OutboundVoucherIssuedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("voucherId", event.voucherId().value().toString());
      payload.put("branchId", event.branchId().value().toString());
      payload.put("reason", event.reason().name());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.issuedBy() == null ? null : event.issuedBy().value(),
              null,
              event.occurredAt(),
              "OUTBOUND_VOUCHER_ISSUED",
              AuditCategory.STOCK,
              AuditSeverity.INFO,
              "outbound_voucher",
              event.voucherId().value().toString(),
              null,
              null,
              null,
              "Saída avulsa emitida — motivo " + event.reason().name(),
              payload));
    } catch (RuntimeException e) {
      log.error(
          "falha gravando audit OUTBOUND_VOUCHER_ISSUED voucherId={}",
          event.voucherId().value(),
          e);
    }
  }
}
