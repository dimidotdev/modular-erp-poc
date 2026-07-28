package dev.dimi.erp.fiscal;

/**
 * Ambiente SEFAZ. PRODUCAO emite com validade fiscal; HOMOLOGACAO usado para testes (NFes não têm
 * validade jurídica).
 */
public enum NfeEnvironment {
  PRODUCAO,
  HOMOLOGACAO
}
