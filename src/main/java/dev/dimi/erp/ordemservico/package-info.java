/**
 * Módulo Ordem de Serviço (OS).
 *
 * <p>Slice 9.2b (billing): adiciona a saga de faturamento sobre os itens de 9.2a. Tabela {@code
 * service_order_billing} registra cada faturamento (parcial ou final) da OS, com soft FKs para a
 * NFe (produtos consumidos) e a NFSe (serviços prestados) emitidas via {@code FiscalProvider}.
 *
 * <p><b>Desacoplamento {@code ordemservico} ↔ {@code fiscal} ↔ {@code financeiro}:</b>
 *
 * <ul>
 *   <li>A <b>emissão</b> da NFe+NFSe é orquestrada pelo módulo {@code fiscal} ({@code POST
 *       /api/v1/fiscal/invoices/from-os/{osId}}), que consome {@code OsQueryPort} para ler a OS e
 *       devolve o link via {@code OsBillingLink} (síncrono na mesma tx, espelhando o padrão {@code
 *       /from-sale/{saleId}}). Por isso {@code fiscal} NÃO entra em {@code allowedDependencies}
 *       aqui — a relação é {@code fiscal → ordemservico}, não o inverso.
 *   <li>O <b>financeiro</b> reage a {@code OsBilledEvent} (evento da API pública deste módulo)
 *       gerando o MVF a receber. A topologia é {@code financeiro → ordemservico}, então {@code
 *       financeiro} também NÃO entra em {@code allowedDependencies} aqui.
 * </ul>
 *
 * <p>Saga inversa de cancelamento pós-faturamento estorna NFe + NFSe + MVF + estoque consumido,
 * usando eventos compensatórios encadeados (cancelamento de NFe → evento → financeiro estorna MVF →
 * evento → ordem-servico devolve estoque consumido ao saldo) — toda a coordenação acontece via
 * eventos da API pública, sem o módulo OS importar tipos de {@code fiscal} ou {@code financeiro}.
 *
 * <p>Continuam FORA do escopo de 9.2b, reservados para slices futuros:
 *
 * <ul>
 *   <li>{@code service_order_payment} — parcelas e condição de pagamento da OS (entra junto com
 *       refino de condições no financeiro).
 *   <li>Auto-consume de estoque on transition de status (segue manual via {@code POST
 *       /products/{lineNumber}/consume}).
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Ordem de Serviço",
    allowedDependencies = {"identidade", "cadastros", "produto", "estoque", "shared"})
package dev.dimi.erp.ordemservico;
