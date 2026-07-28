package dev.dimi.erp.fiscal.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.BranchQueryPort;
import dev.dimi.erp.cadastros.BranchView;
import dev.dimi.erp.cadastros.PersonQueryPort;
import dev.dimi.erp.cadastros.PersonView;
import dev.dimi.erp.fiscal.AuthorizationResult;
import dev.dimi.erp.fiscal.BranchNotFiscallyConfiguredException;
import dev.dimi.erp.fiscal.ConsumerType;
import dev.dimi.erp.fiscal.FiscalModel;
import dev.dimi.erp.fiscal.FiscalOriginType;
import dev.dimi.erp.fiscal.FiscalProvider;
import dev.dimi.erp.fiscal.InvoiceAuthorizedEvent;
import dev.dimi.erp.fiscal.InvoiceDraft;
import dev.dimi.erp.fiscal.InvoiceId;
import dev.dimi.erp.fiscal.InvoiceOperation;
import dev.dimi.erp.fiscal.InvoicePurpose;
import dev.dimi.erp.fiscal.InvoiceRejectedEvent;
import dev.dimi.erp.fiscal.NfeEnvironment;
import dev.dimi.erp.fiscal.PersonAnonymizedForInvoiceException;
import dev.dimi.erp.fiscal.SaleNotInvoiceableException;
import dev.dimi.erp.fiscal.internal.domain.FiscalEvent;
import dev.dimi.erp.fiscal.internal.domain.FiscalEventType;
import dev.dimi.erp.fiscal.internal.domain.Invoice;
import dev.dimi.erp.fiscal.internal.domain.InvoiceItem;
import dev.dimi.erp.fiscal.internal.persistence.FiscalEventRepository;
import dev.dimi.erp.fiscal.internal.persistence.InvoiceItemRepository;
import dev.dimi.erp.fiscal.internal.persistence.InvoiceRepository;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.movimentacao.SaleForInvoicingView;
import dev.dimi.erp.movimentacao.SaleId;
import dev.dimi.erp.movimentacao.SaleInvoiceLink;
import dev.dimi.erp.movimentacao.SaleQueryPort;
import dev.dimi.erp.produto.ProductQueryPort;
import dev.dimi.erp.produto.ProductQueryPort.PackagingSnapshot;
import dev.dimi.erp.shared.modularization.ModuleAccessGuard;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestrador da emissão NFe a partir de uma {@code Sale} fechada.
 *
 * <p>Fluxo (slice 8.3a enriquecido):
 *
 * <ol>
 *   <li>Idempotência por {@code (origin=SALE, originId, model=NFE)} (RN-FIS-007); por {@code
 *       Idempotency-Key} header (RN-FIS-008) se fornecido.
 *   <li>Carrega snapshot da venda via {@link SaleQueryPort} (exige {@code CLOSED}).
 *   <li>Carrega cliente via {@link PersonQueryPort}; bloqueia se anonimizado (LGPD).
 *   <li>Carrega filial via {@link BranchQueryPort} e verifica {@code canEmitInvoice} (RN-FIS-001).
 *   <li>Aloca próximo número via {@link NumberAllocationService} (lock pessimista).
 *   <li>Persiste {@link Invoice} {@code DRAFT → PENDING} + {@link InvoiceItem}s — itens
 *       enriquecidos com SKU/NCM/origem do {@link PackagingSnapshot}.
 *   <li>Calcula impostos por item via {@link TaxCalculationService}; persiste {@code
 *       TaxCalculation} e atualiza {@code invoice.totalTaxes}.
 *   <li>Chama {@link FiscalProvider#authorize}. Resultado: {@code AUTHORIZED} ou {@code REJECTED}.
 *   <li>Registra {@link FiscalEvent} append-only e publica evento de domínio. Se autorizado, {@link
 *       SaleInvoiceLink#linkInvoice} fecha o ciclo com movimentação.
 * </ol>
 *
 * <p>Limitações 8.3a (refino em 8.3b / 8.6):
 *
 * <ul>
 *   <li>Série hardcoded {@code "1"} — sem endpoint para múltiplas séries por modelo (8.6).
 *   <li>Provider real ACBr entra em 8.3b — em 8.3a só o {@code InMemoryFiscalProvider} responde.
 *   <li>Falha técnica do provider ({@code FiscalProviderException}) propaga e faz rollback —
 *       contingência entra em 8.6.
 *   <li>Cancelamento, CCe, inutilização entram em 8.6.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueInvoiceFromSaleService {

  /** Série default usada enquanto não há cadastro de séries (8.6 introduz CRUD). */
  private static final String DEFAULT_SERIES = "1";

  private static final FiscalModel DEFAULT_MODEL = FiscalModel.NFE;

  /** Origem da mercadoria — v1 hardcoded "0" (nacional). Importação fica para slice futuro. */
  private static final String DEFAULT_ITEM_ORIGIN = "0";

  /** Unidade fallback quando o packaging não declara unidade comercial/fiscal. */
  private static final String DEFAULT_UNIT = "UN";

  private final SaleQueryPort sales;
  private final SaleInvoiceLink saleInvoiceLink;
  private final BranchQueryPort branches;
  private final PersonQueryPort persons;
  private final ProductQueryPort products;
  private final InvoiceRepository invoices;
  private final InvoiceItemRepository invoiceItems;
  private final FiscalEventRepository fiscalEvents;
  private final NumberAllocationService numberAllocation;
  private final CfopResolver cfopResolver;
  private final TaxCalculationService taxCalculation;
  private final FiscalProvider provider;
  private final ApplicationEventPublisher events;
  private final ModuleAccessGuard moduleAccessGuard;

  /**
   * Emite NFe a partir de venda fechada.
   *
   * @param saleId venda origem (deve estar {@code CLOSED})
   * @param actingUser usuário autenticado executando a ação
   * @param idempotencyKey opcional — segunda chamada com mesma key retorna invoice existente
   * @return invoice resultante (existente ou recém-criado) + flag {@code created}
   */
  @Transactional
  public Result issueFromSale(SaleId saleId, UserId actingUser, String idempotencyKey) {
    // 0) Guard cross-module — aborta antes de qualquer side-effect se fiscal está desligado.
    moduleAccessGuard.requireEnabled("fiscal");

    // 1) idempotência por origem (RN-FIS-007)
    Optional<Invoice> byOrigin =
        invoices.findByOriginTypeAndOriginIdAndModel(
            FiscalOriginType.SALE, saleId.value(), DEFAULT_MODEL);
    if (byOrigin.isPresent()) {
      log.debug(
          "emit from-sale dedup by origin sale={} invoice={}",
          saleId.value(),
          byOrigin.get().getId());
      return new Result(byOrigin.get(), false);
    }

    // 2) idempotência por header (RN-FIS-008)
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      Optional<Invoice> byKey = invoices.findByIdempotencyKey(idempotencyKey);
      if (byKey.isPresent()) {
        log.debug("emit from-sale dedup by idempotency-key invoice={}", byKey.get().getId());
        return new Result(byKey.get(), false);
      }
    }

    // 3) carregar venda
    SaleForInvoicingView sale =
        sales
            .findForInvoicing(saleId)
            .orElseThrow(() -> new SaleNotInvoiceableException(saleId.value().toString()));

    // 4) carregar cliente + bloquear anonimização (LGPD)
    PersonView customer =
        persons
            .findById(sale.customerId())
            .orElseThrow(
                () ->
                    new PersonAnonymizedForInvoiceException(sale.customerId().value().toString()));
    if (customer.anonymized() || customer.document() == null) {
      throw new PersonAnonymizedForInvoiceException(sale.customerId().value().toString());
    }

    // 5) carregar filial + verificar elegibilidade fiscal
    BranchView branch =
        branches
            .findById(sale.branchId())
            .orElseThrow(
                () -> new BranchNotFiscallyConfiguredException(sale.branchId().value().toString()));
    if (branch.fiscal() == null || !branches.canEmitInvoice(sale.branchId())) {
      throw new BranchNotFiscallyConfiguredException(sale.branchId().value().toString());
    }

    // 6) alocar próximo número (lock pessimista)
    long number = numberAllocation.allocate(sale.branchId(), DEFAULT_MODEL, DEFAULT_SERIES);

    // 7) montar e persistir invoice DRAFT → PENDING + items enriquecidos
    NfeEnvironment env = NfeEnvironment.valueOf(branch.fiscal().nfeEnvironment());
    Invoice invoice =
        Invoice.draft(
            sale.branchId().value(),
            DEFAULT_MODEL,
            DEFAULT_SERIES,
            number,
            InvoiceOperation.SAIDA,
            InvoicePurpose.NORMAL,
            ConsumerType.NORMAL,
            FiscalOriginType.SALE,
            saleId.value(),
            env,
            idempotencyKey);
    invoice.updateCustomer(sale.customerId().value(), customer.document());
    invoice.markPending();
    invoices.save(invoice);

    String defaultCfop = cfopResolver.resolveDefault(InvoiceOperation.SAIDA);
    List<InvoiceItem> persistedItems = buildItems(invoice, sale, defaultCfop);
    invoiceItems.saveAll(persistedItems);

    // 8) calcular impostos por item + agregar total
    Map<UUID, List<InvoiceDraft.DraftTax>> taxesByItemId = new HashMap<>();
    BigDecimal totalTaxes = BigDecimal.ZERO;
    for (InvoiceItem item : persistedItems) {
      TaxCalculationService.ItemResult itemResult =
          taxCalculation.computeForItem(item, branch.fiscal());
      taxesByItemId.put(item.getId(), itemResult.draftTaxes());
      totalTaxes = totalTaxes.add(itemResult.totalTax());
    }
    invoice.updateTotals(
        sale.totalGross(), sale.totalDiscount(), sale.totalFreight(), totalTaxes, sale.total());
    invoices.save(invoice);

    // 9) chamar provider — falha técnica propaga e faz rollback
    InvoiceDraft draft = buildDraft(invoice, persistedItems, branch, customer, taxesByItemId);
    AuthorizationResult outcome = provider.authorize(draft);

    // 10) aplicar resultado + registrar fiscal_event + publicar evento de domínio
    TenantId tenantId = new TenantId(TenantContext.currentOrThrow().id());
    Instant now = Instant.now();
    switch (outcome) {
      case AuthorizationResult.Authorized auth -> {
        invoice.markAuthorized(auth.fiscalKey(), auth.protocol(), auth.authorizedAt());
        fiscalEvents.save(
            FiscalEvent.record(
                invoice.getId(),
                FiscalEventType.AUTHORIZATION,
                auth.protocol(),
                "AUTHORIZED",
                null,
                null,
                actingUser.value()));
        saleInvoiceLink.linkInvoice(saleId, invoice.getId());
        events.publishEvent(
            new InvoiceAuthorizedEvent(
                new InvoiceId(invoice.getId()),
                tenantId,
                new BranchId(invoice.getBranchId()),
                invoice.getModel(),
                invoice.getSeries(),
                invoice.getNumber(),
                auth.fiscalKey(),
                invoice.getOriginType(),
                invoice.getOriginId(),
                invoice.getTotal(),
                actingUser,
                now));
        log.info(
            "invoice authorized sale={} invoice={} key={} number={}",
            saleId.value(),
            invoice.getId(),
            auth.fiscalKey(),
            number);
      }
      case AuthorizationResult.Rejected rej -> {
        invoice.markRejected(rej.code(), rej.reason());
        fiscalEvents.save(
            FiscalEvent.record(
                invoice.getId(),
                FiscalEventType.REJECTION,
                null,
                "REJECTED:" + rej.code(),
                null,
                rej.reason(),
                actingUser.value()));
        events.publishEvent(
            new InvoiceRejectedEvent(
                new InvoiceId(invoice.getId()),
                tenantId,
                new BranchId(invoice.getBranchId()),
                invoice.getModel(),
                invoice.getSeries(),
                invoice.getNumber(),
                invoice.getOriginType(),
                invoice.getOriginId(),
                rej.code(),
                rej.reason(),
                actingUser,
                now));
        log.warn(
            "invoice rejected sale={} invoice={} code={} reason={}",
            saleId.value(),
            invoice.getId(),
            rej.code(),
            rej.reason());
      }
    }

    return new Result(invoice, true);
  }

  /**
   * Itens enriquecidos com SKU/descrição/NCM/CEST/origem vindos do {@link ProductQueryPort}. Quando
   * o packaging não existe ou está desativado, cai em placeholders defensivos — o provider pode
   * rejeitar via SEFAZ mas a emissão não trava por isso.
   */
  private List<InvoiceItem> buildItems(
      Invoice invoice, SaleForInvoicingView sale, String defaultCfop) {
    List<InvoiceItem> items = new ArrayList<>(sale.items().size());
    for (SaleForInvoicingView.Item s : sale.items()) {
      BigDecimal gross = s.quantity().multiply(s.unitPrice());
      PackagingSnapshot snapshot = products.findPackaging(s.packagingId()).orElse(null);
      String itemCode = snapshot == null ? null : snapshot.sku();
      String description =
          snapshot == null || snapshot.productName() == null
              ? "Produto " + s.lineNumber()
              : snapshot.productName();
      String ncm = snapshot == null ? null : snapshot.ncm();
      String cest = snapshot == null ? null : snapshot.cest();
      String origin =
          snapshot == null || snapshot.origin() == null ? DEFAULT_ITEM_ORIGIN : snapshot.origin();
      items.add(
          InvoiceItem.create(
              invoice.getId(),
              s.lineNumber(),
              s.packagingId().value(),
              itemCode,
              description,
              ncm,
              cest,
              defaultCfop,
              origin,
              s.quantity(),
              DEFAULT_UNIT,
              DEFAULT_UNIT,
              s.unitPrice(),
              gross,
              s.discountValue() == null ? BigDecimal.ZERO : s.discountValue(),
              s.netValue()));
    }
    return items;
  }

  private InvoiceDraft buildDraft(
      Invoice invoice,
      List<InvoiceItem> items,
      BranchView branch,
      PersonView customer,
      Map<UUID, List<InvoiceDraft.DraftTax>> taxesByItemId) {
    List<InvoiceDraft.DraftItem> draftItems =
        items.stream()
            .map(
                i ->
                    new InvoiceDraft.DraftItem(
                        i.getLineNumber(),
                        i.getItemCode(),
                        i.getItemDescription(),
                        i.getNcm(),
                        i.getCfop(),
                        i.getOrigin(),
                        i.getQuantity(),
                        i.getUnitCommercial(),
                        i.getUnitFiscal(),
                        i.getUnitValue(),
                        i.getGrossValue(),
                        i.getDiscount(),
                        i.getNetValue(),
                        taxesByItemId.getOrDefault(i.getId(), List.of())))
            .toList();
    return new InvoiceDraft(
        new InvoiceId(invoice.getId()),
        new BranchId(invoice.getBranchId()),
        branch.fiscal() == null ? null : branch.fiscal().cnpj(),
        invoice.getModel(),
        invoice.getSeries(),
        invoice.getNumber(),
        invoice.getNfeEnvironment(),
        invoice.getOperation(),
        invoice.getConsumerType(),
        invoice.getCustomerCpfCnpj(),
        customer.legalName(),
        invoice.getTotalProducts(),
        invoice.getTotalDiscount(),
        invoice.getTotalFreight(),
        invoice.getTotalTaxes(),
        invoice.getTotal(),
        draftItems);
  }

  /** Wrapper do retorno do service: {@code created=true} quando a chamada gerou novo invoice. */
  public record Result(Invoice invoice, boolean created) {}
}
