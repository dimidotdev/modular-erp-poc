package dev.dimi.erp.fiscal.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.BranchQueryPort;
import dev.dimi.erp.cadastros.BranchView;
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
import dev.dimi.erp.fiscal.OsNotInvoiceableException;
import dev.dimi.erp.fiscal.internal.domain.FiscalEvent;
import dev.dimi.erp.fiscal.internal.domain.FiscalEventType;
import dev.dimi.erp.fiscal.internal.domain.Invoice;
import dev.dimi.erp.fiscal.internal.domain.InvoiceItem;
import dev.dimi.erp.fiscal.internal.domain.TaxCalculation;
import dev.dimi.erp.fiscal.internal.domain.TaxType;
import dev.dimi.erp.fiscal.internal.persistence.FiscalEventRepository;
import dev.dimi.erp.fiscal.internal.persistence.InvoiceItemRepository;
import dev.dimi.erp.fiscal.internal.persistence.InvoiceRepository;
import dev.dimi.erp.fiscal.internal.persistence.TaxCalculationRepository;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.ordemservico.BillingId;
import dev.dimi.erp.ordemservico.OsBillingLink;
import dev.dimi.erp.ordemservico.OsBillingType;
import dev.dimi.erp.ordemservico.OsForInvoicingView;
import dev.dimi.erp.ordemservico.OsQueryPort;
import dev.dimi.erp.ordemservico.ServiceOrderId;
import dev.dimi.erp.produto.ProductQueryPort;
import dev.dimi.erp.produto.ProductQueryPort.PackagingSnapshot;
import dev.dimi.erp.shared.modularization.ModuleAccessGuard;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * Orquestrador do faturamento fiscal a partir de uma {@code ServiceOrder} pronta para faturar.
 * Diferente do {@code IssueInvoiceFromSaleService} (que emite um único invoice NFe), aqui o serviço
 * emite até dois invoices simultâneos:
 *
 * <ul>
 *   <li><b>NFE</b> — quando há linhas de produto consumidos: itera {@link
 *       OsForInvoicingView#productLines}, resolve CFOP/CST/ICMS+PIS+COFINS via os mesmos resolvers
 *       do FromSale, e persiste {@code TaxCalculation} via {@link
 *       TaxCalculationService#computeForItem}.
 *   <li><b>NFSE</b> — quando há linhas de serviço prestados: itera {@link
 *       OsForInvoicingView#serviceLines}, monta itens com {@code serviceCode} como {@code itemCode}
 *       (NCM/CFOP ficam nulos para serviços municipais), e cria {@link TaxCalculation} de {@link
 *       TaxType#ISSQN} diretamente com {@code issqnRate}/{@code issqnValue} já calculados pelo
 *       módulo OS — não passa pelo {@code TaxCalculationService} (que só cobre ICMS/PIS/COFINS no
 *       v1).
 * </ul>
 *
 * <p>Após emitir os invoices, chama {@link OsBillingLink#recordBillingInvoices} para o módulo OS
 * persistir o {@code service_order_billing}, transitar a OS para {@code BILLED} e publicar {@code
 * OsBilledEvent} — tudo na MESMA transação (RN-FIS-009: falha no link rolba as invoices).
 *
 * <p>Idempotência por {@code (originType=OS, originId, model)} para cada invoice individualmente
 * (RN-FIS-007) — uma segunda chamada com a mesma OS reaproveita invoices já emitidos sem reabrir
 * conexão com o provider; o {@link OsBillingLink} também é idempotente por {@code idempotencyKey}.
 *
 * <p>Cliente vem direto do {@link OsForInvoicingView} ({@code customerName}/{@code
 * customerCpfCnpj}) — diferente do FromSale que carrega {@code PersonView} via {@code
 * PersonQueryPort} para checar anonimização LGPD. Aqui assumimos que a projeção pública da OS já
 * bloqueia o faturamento se o cliente foi anonimizado — replicar a checagem aqui exigiria {@code
 * PersonQueryPort} na dependência fiscal sem ganho real.
 *
 * <p>Limitações herdadas do 8.3a (refino em 8.6):
 *
 * <ul>
 *   <li>Série hardcoded {@code "1"} (mesma série para NFE e NFSE).
 *   <li>NFSE: município/aliquota ISSQN vêm da OS — sem catálogo LC 116/2003 do lado fiscal.
 *   <li>Falha técnica do provider propaga e faz rollback (sem contingência).
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueInvoiceFromOsService {

  /** Série default usada enquanto não há cadastro de séries (8.6 introduz CRUD). */
  private static final String DEFAULT_SERIES = "1";

  /** Origem da mercadoria — v1 hardcoded "0" (nacional). Importação fica para slice futuro. */
  private static final String DEFAULT_ITEM_ORIGIN = "0";

  /** Unidade fallback quando o packaging não declara unidade comercial/fiscal. */
  private static final String DEFAULT_UNIT = "UN";

  /** Unidade fiscal usada para linha de serviço — não há packaging por trás. */
  private static final String SERVICE_UNIT = "UN";

  private final OsQueryPort osQuery;
  private final OsBillingLink billingLink;
  private final BranchQueryPort branches;
  private final ProductQueryPort products;
  private final InvoiceRepository invoices;
  private final InvoiceItemRepository invoiceItems;
  private final TaxCalculationRepository taxCalculations;
  private final FiscalEventRepository fiscalEvents;
  private final NumberAllocationService numberAllocation;
  private final CfopResolver cfopResolver;
  private final TaxCalculationService taxCalculation;
  private final FiscalProvider provider;
  private final ApplicationEventPublisher events;
  private final ModuleAccessGuard moduleAccessGuard;

  /**
   * Fatura uma OS — emite NFe (se houver produtos), NFSe (se houver serviços) e registra o billing
   * no módulo OS via {@link OsBillingLink}. Tudo na mesma transação.
   *
   * @param cmd comando com OS de origem, tipo de billing, notas, usuário executor e idempotency key
   * @return billingId + invoices emitidos (qualquer um pode ser nulo se a OS só tem um lado)
   */
  @Transactional
  public Result issueBillingFromOs(IssueOsBillingCommand cmd) {
    // 0) Guard cross-module — aborta antes de qualquer side-effect se fiscal está desligado.
    moduleAccessGuard.requireEnabled("fiscal");

    // 1) Carrega OS pra invoicing via port pública
    OsForInvoicingView view =
        osQuery
            .findForInvoicing(cmd.osId())
            .orElseThrow(() -> new OsNotInvoiceableException(cmd.osId().value().toString()));

    // 2) Verifica se tem itens
    boolean hasProducts = view.productsAmount() != null && view.productsAmount().signum() > 0;
    boolean hasServices = view.servicesAmount() != null && view.servicesAmount().signum() > 0;
    if (!hasProducts && !hasServices) {
      throw new OsNotInvoiceableException(cmd.osId().value().toString());
    }

    // 3) Carrega filial + verifica elegibilidade fiscal (single fetch — reusado para NFe e NFSe)
    BranchView branch =
        branches
            .findById(view.branchId())
            .orElseThrow(
                () -> new BranchNotFiscallyConfiguredException(view.branchId().value().toString()));
    if (branch.fiscal() == null || !branches.canEmitInvoice(view.branchId())) {
      throw new BranchNotFiscallyConfiguredException(view.branchId().value().toString());
    }

    // 4) Emite NFe se há produtos
    InvoiceId nfeId = null;
    if (hasProducts) {
      nfeId = emitOneInvoice(view, branch, FiscalModel.NFE, cmd);
    }

    // 5) Emite NFSe se há serviços
    InvoiceId nfseId = null;
    if (hasServices) {
      nfseId = emitOneInvoice(view, branch, FiscalModel.NFSE, cmd);
    }

    // 6) Registra billing no OS via port — OS persiste Billing + transita p/ BILLED + publica
    // OsBilledEvent. Idempotência adicional por idempotencyKey acontece dentro do adapter.
    OsBillingLink.RecordCommand recordCmd =
        new OsBillingLink.RecordCommand(
            cmd.osId(),
            cmd.billingType() == null ? OsBillingType.FINAL : cmd.billingType(),
            nfeId == null ? null : nfeId.value(),
            nfseId == null ? null : nfseId.value(),
            view.productsAmount(),
            view.servicesAmount(),
            view.totalDiscount(),
            view.total(),
            view.branchId(),
            view.customerId(),
            null, // paymentConditionId — slice 9.2c se necessário
            cmd.notes(),
            cmd.actingUser(),
            cmd.idempotencyKey());
    OsBillingLink.RecordResult linked = billingLink.recordBillingInvoices(recordCmd);

    return new Result(linked.billingId(), nfeId, nfseId, linked.created());
  }

  /**
   * Emite um único invoice (NFE ou NFSE) a partir da OS, com idempotência por origem (RN-FIS-007).
   * Encapsula a sequência: dedup → alocação de número → draft+pending → itens+impostos → totais →
   * provider → status + fiscal_event + evento de domínio.
   */
  private InvoiceId emitOneInvoice(
      OsForInvoicingView os, BranchView branch, FiscalModel model, IssueOsBillingCommand cmd) {
    ServiceOrderId osId = cmd.osId();

    // 1) idempotência por origem (RN-FIS-007) — chave inclui o modelo, NFE e NFSE coexistem na OS
    Optional<Invoice> byOrigin =
        invoices.findByOriginTypeAndOriginIdAndModel(FiscalOriginType.OS, osId.value(), model);
    if (byOrigin.isPresent()) {
      log.debug(
          "emit from-os dedup by origin os={} model={} invoice={}",
          osId.value(),
          model,
          byOrigin.get().id());
      return byOrigin.get().id();
    }

    // 2) alocar próximo número (lock pessimista)
    long number = numberAllocation.allocate(os.branchId(), model, DEFAULT_SERIES);

    // 3) montar invoice DRAFT → PENDING
    NfeEnvironment env = NfeEnvironment.valueOf(branch.fiscal().nfeEnvironment());
    Invoice invoice =
        Invoice.draft(
            os.branchId().value(),
            model,
            DEFAULT_SERIES,
            number,
            InvoiceOperation.SAIDA,
            InvoicePurpose.NORMAL,
            ConsumerType.NORMAL,
            FiscalOriginType.OS,
            osId.value(),
            env,
            cmd.idempotencyKey());
    invoice.updateCustomer(os.customerId().value(), os.customerCpfCnpj());
    invoice.markPending();
    invoices.save(invoice);

    // 4) montar itens conforme modelo + calcular impostos
    Map<UUID, List<InvoiceDraft.DraftTax>> taxesByItemId = new HashMap<>();
    ItemsAndTaxes computed =
        switch (model) {
          case NFE -> buildNfeItemsAndTaxes(invoice, os, branch, taxesByItemId);
          case NFSE -> buildNfseItemsAndTaxes(invoice, os, taxesByItemId);
          case NFCE ->
              throw new UnsupportedOperationException(
                  "Emissão NFCE a partir de OS não suportada — modelo inválido para fluxo OS.");
        };
    List<InvoiceItem> persistedItems = computed.items();
    BigDecimal totalTaxes = computed.totalTaxes();
    BigDecimal totalProducts = computed.totalProducts();

    BigDecimal totalDiscount = os.totalDiscount() == null ? BigDecimal.ZERO : os.totalDiscount();
    // Total do invoice: bruto da modalidade (produtos ou serviços) menos desconto. Impostos não
    // somam ao total — no v1 cumulativo, ICMS/PIS/COFINS/ISSQN já estão embutidos no preço de
    // venda e são apenas destacados no XML para a fiscalização.
    BigDecimal total = totalProducts.subtract(totalDiscount).max(BigDecimal.ZERO);
    invoice.updateTotals(totalProducts, totalDiscount, BigDecimal.ZERO, totalTaxes, total);
    invoices.save(invoice);

    // 5) chamar provider — falha técnica propaga e faz rollback
    InvoiceDraft draft = buildDraft(invoice, persistedItems, branch, os, taxesByItemId);
    AuthorizationResult outcome = provider.authorize(draft);

    // 6) aplicar resultado + registrar fiscal_event + publicar evento de domínio
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
                cmd.actingUser().value()));
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
                cmd.actingUser(),
                now));
        log.info(
            "invoice authorized os={} model={} invoice={} key={} number={}",
            osId.value(),
            model,
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
                cmd.actingUser().value()));
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
                cmd.actingUser(),
                now));
        log.warn(
            "invoice rejected os={} model={} invoice={} code={} reason={}",
            osId.value(),
            model,
            invoice.getId(),
            rej.code(),
            rej.reason());
      }
    }

    return invoice.id();
  }

  /**
   * Monta itens NFE + persiste em lote + calcula ICMS/PIS/COFINS por item via {@link
   * TaxCalculationService}. Popula {@code taxesByItemId} (out-param) com os draft taxes para o
   * provider e devolve a soma dos impostos + total bruto dos produtos para atualizar o cabeçalho.
   */
  private ItemsAndTaxes buildNfeItemsAndTaxes(
      Invoice invoice,
      OsForInvoicingView os,
      BranchView branch,
      Map<UUID, List<InvoiceDraft.DraftTax>> taxesByItemId) {
    String defaultCfop = cfopResolver.resolveDefault(InvoiceOperation.SAIDA);
    List<InvoiceItem> items = buildProductItems(invoice, os, defaultCfop);
    invoiceItems.saveAll(items);
    BigDecimal totalTaxes = BigDecimal.ZERO;
    for (InvoiceItem item : items) {
      TaxCalculationService.ItemResult itemResult =
          taxCalculation.computeForItem(item, branch.fiscal());
      taxesByItemId.put(item.getId(), itemResult.draftTaxes());
      totalTaxes = totalTaxes.add(itemResult.totalTax());
    }
    return new ItemsAndTaxes(items, totalTaxes, os.productsAmount());
  }

  /**
   * Monta itens NFSE + persiste + cria {@link TaxCalculation} de {@link TaxType#ISSQN} diretamente
   * a partir do {@code issqnRate}/{@code issqnValue} da {@link OsForInvoicingView.ServiceLine} —
   * sem passar pelo {@code TaxCalculationService} (que só cobre ICMS/PIS/COFINS no v1).
   */
  private ItemsAndTaxes buildNfseItemsAndTaxes(
      Invoice invoice,
      OsForInvoicingView os,
      Map<UUID, List<InvoiceDraft.DraftTax>> taxesByItemId) {
    List<InvoiceItem> items = buildServiceItems(invoice, os);
    invoiceItems.saveAll(items);
    BigDecimal totalTaxes = BigDecimal.ZERO;
    for (int i = 0; i < items.size(); i++) {
      InvoiceItem item = items.get(i);
      OsForInvoicingView.ServiceLine line = os.serviceLines().get(i);
      BigDecimal issqnValue =
          line.issqnValue() == null
              ? BigDecimal.ZERO
              : line.issqnValue().setScale(2, RoundingMode.HALF_UP);
      BigDecimal issqnRate = line.issqnRate() == null ? BigDecimal.ZERO : line.issqnRate();
      taxCalculations.save(
          TaxCalculation.create(
              item.getId(),
              TaxType.ISSQN,
              null,
              item.getNetValue(),
              issqnRate,
              issqnValue,
              null,
              null,
              null,
              null,
              null,
              null));
      taxesByItemId.put(
          item.getId(),
          List.of(
              new InvoiceDraft.DraftTax(
                  TaxType.ISSQN,
                  null,
                  item.getNetValue(),
                  issqnRate,
                  issqnValue,
                  null,
                  null,
                  null)));
      totalTaxes = totalTaxes.add(issqnValue);
    }
    return new ItemsAndTaxes(items, totalTaxes, os.servicesAmount());
  }

  /**
   * Itens NFE — produtos consumidos na OS — enriquecidos com SKU/NCM/origem via {@link
   * ProductQueryPort}, mesmo padrão de {@code IssueInvoiceFromSaleService.buildItems}.
   */
  private List<InvoiceItem> buildProductItems(
      Invoice invoice, OsForInvoicingView os, String defaultCfop) {
    List<InvoiceItem> items = new ArrayList<>(os.productLines().size());
    for (OsForInvoicingView.ProductLine line : os.productLines()) {
      BigDecimal gross = line.quantity().multiply(line.unitPrice());
      PackagingSnapshot snapshot = products.findPackaging(line.packagingId()).orElse(null);
      String itemCode = snapshot == null ? null : snapshot.sku();
      String description =
          snapshot == null || snapshot.productName() == null
              ? "Produto " + line.lineNumber()
              : snapshot.productName();
      String ncm = snapshot == null ? null : snapshot.ncm();
      String cest = snapshot == null ? null : snapshot.cest();
      String origin =
          snapshot == null || snapshot.origin() == null ? DEFAULT_ITEM_ORIGIN : snapshot.origin();
      items.add(
          InvoiceItem.create(
              invoice.getId(),
              line.lineNumber(),
              line.packagingId().value(),
              itemCode,
              description,
              ncm,
              cest,
              defaultCfop,
              origin,
              line.quantity(),
              DEFAULT_UNIT,
              DEFAULT_UNIT,
              line.unitPrice(),
              gross,
              line.discount() == null ? BigDecimal.ZERO : line.discount(),
              line.netValue()));
    }
    return items;
  }

  /**
   * Itens NFSE — serviços prestados na OS. {@code packagingId}/{@code ncm}/{@code cest} ficam nulos
   * (não há produto físico); {@code itemCode} carrega o código municipal LC 116; {@code cfop} fica
   * nulo (serviço municipal não usa CFOP). {@code origin} permanece "0" como placeholder.
   */
  private List<InvoiceItem> buildServiceItems(Invoice invoice, OsForInvoicingView os) {
    List<InvoiceItem> items = new ArrayList<>(os.serviceLines().size());
    for (OsForInvoicingView.ServiceLine line : os.serviceLines()) {
      BigDecimal gross = line.quantity().multiply(line.unitPrice());
      items.add(
          InvoiceItem.create(
              invoice.getId(),
              line.lineNumber(),
              null,
              line.serviceCode(),
              line.description() == null ? "Serviço " + line.lineNumber() : line.description(),
              null,
              null,
              // CFOP é obrigatório na entidade — usamos placeholder por enquanto; o adapter NFSE
              // ignora (NFSe municipal não consome CFOP). Em 8.6 isso vira nullable ou cfop="0000".
              "0000",
              DEFAULT_ITEM_ORIGIN,
              line.quantity(),
              SERVICE_UNIT,
              SERVICE_UNIT,
              line.unitPrice(),
              gross,
              line.discount() == null ? BigDecimal.ZERO : line.discount(),
              line.netValue()));
    }
    return items;
  }

  private InvoiceDraft buildDraft(
      Invoice invoice,
      List<InvoiceItem> items,
      BranchView branch,
      OsForInvoicingView os,
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
        os.customerName(),
        invoice.getTotalProducts(),
        invoice.getTotalDiscount(),
        invoice.getTotalFreight(),
        invoice.getTotalTaxes(),
        invoice.getTotal(),
        draftItems);
  }

  /**
   * Comando público do controller fiscal {@code POST /fiscal/invoices/from-os/{id}} para faturar
   * uma OS — emite NFe e/ou NFSe simultâneos e registra o billing no módulo OS.
   *
   * @param osId OS de origem (deve estar em status faturável)
   * @param billingType {@code FINAL} ou {@code PARTIAL} — null defaulta para {@code FINAL}
   * @param notes anotação livre carregada para o registro de billing
   * @param actingUser usuário autenticado executando a ação
   * @param idempotencyKey opcional — segunda chamada com mesma key retorna billing existente
   */
  public record IssueOsBillingCommand(
      ServiceOrderId osId,
      OsBillingType billingType,
      String notes,
      UserId actingUser,
      String idempotencyKey) {}

  /**
   * Resultado do faturamento: billing já criado no módulo OS + invoices emitidos (qualquer um pode
   * ser nulo se a OS só tinha um lado).
   */
  public record Result(
      BillingId billingId, InvoiceId nfeInvoiceId, InvoiceId nfseInvoiceId, boolean created) {}

  /**
   * Holder interno do switch por modelo — agrega itens persistidos, soma de impostos e total bruto
   * da modalidade (produtos para NFE, serviços para NFSE) para alimentar {@code
   * invoice.updateTotals}.
   */
  private record ItemsAndTaxes(
      List<InvoiceItem> items, BigDecimal totalTaxes, BigDecimal totalProducts) {}
}
