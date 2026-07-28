package dev.dimi.erp.cadastros.internal.persistence;

import dev.dimi.erp.cadastros.internal.domain.BranchFiscal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BranchFiscalRepository extends JpaRepository<BranchFiscal, UUID> {

  Optional<BranchFiscal> findByCnpj(String cnpj);

  /**
   * Carrega filiais cujo certificado A1 expira dentro da janela {@code [from, to]} — usado pelo job
   * diário de alertas (RN-CAD-009). Filtra somente registros com certificado instalado.
   */
  @Query(
      """
      select bf from BranchFiscal bf
      where bf.certKvSecretName is not null
        and bf.certValidUntil between :from and :to
      """)
  List<BranchFiscal> findCertificatesExpiringBetween(
      @Param("from") Instant from, @Param("to") Instant to);
}
