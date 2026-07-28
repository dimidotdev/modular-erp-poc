/**
 * Módulo Contratos.
 *
 * <p>Slice 10.1 (foundation): contract + items + lifecycle (DRAFT/ACTIVE/SUSPENDED/EXPIRED/
 * CANCELLED/TERMINATED_EARLY) + amendments (EXTENSION/VALUE/ITEM_*) + adjustments (IGPM/IPCA/INPC/
 * MANUAL). Ciclos de faturamento (billing cycles), job diário, listener financeiro pra MVF a
 * receber entram em slice 10.2.
 *
 * <p>{@code allowedDependencies} 10.1 = {@code identidade}, {@code cadastros}, {@code produto},
 * {@code shared}. Quando 10.2 adicionar billing cycles + listener, financeiro entra como consumer
 * via evento gordinho (contratos publica; financeiro consome). Não precisa importar fiscal/
 * financeiro/ordemservico em 10.1 — listeners/integrações vivem do outro lado.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Contratos",
    allowedDependencies = {"identidade", "cadastros", "produto", "shared"})
package dev.dimi.erp.contratos;
