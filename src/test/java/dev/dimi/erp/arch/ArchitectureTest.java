package dev.dimi.erp.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

/**
 * Regras arquiteturais aplicadas ao pacote {@code dev.dimi.erp}.
 *
 * <p>Detalhes em {@code docs/architecture/01-arquitetura.md} e {@code
 * docs/architecture/04-padroes-codigo.md}.
 */
@AnalyzeClasses(packages = "dev.dimi.erp", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

  private static final DescribedPredicate<JavaMethod> MAPEADO_EM_HTTP =
      new DescribedPredicate<>("mapeado em HTTP") {
        @Override
        public boolean test(JavaMethod method) {
          return method.isAnnotatedWith(GetMapping.class)
              || method.isAnnotatedWith(PostMapping.class)
              || method.isAnnotatedWith(PutMapping.class)
              || method.isAnnotatedWith(PatchMapping.class)
              || method.isAnnotatedWith(DeleteMapping.class);
        }
      };

  @ArchTest
  public void internal_so_e_acessado_de_dentro_do_proprio_modulo(JavaClasses imported) {
    // allowEmptyShould enquanto o esqueleto não tem classes em internal/.
    // Assim que o primeiro slice adicionar classes, a regra valida sem flag.
    classes()
        .that()
        .resideInAPackage("..internal..")
        .should()
        .onlyBeAccessed()
        .byClassesThat()
        .resideInAPackage("..internal..")
        .allowEmptyShould(true)
        .check(imported);
  }

  @ArchTest
  public void sem_System_out_no_codigo_de_producao(JavaClasses imported) {
    noClasses()
        .should()
        .accessField(System.class, "out")
        .orShould()
        .accessField(System.class, "err")
        .check(imported);
  }

  @ArchTest
  public void sem_printStackTrace_no_codigo_de_producao(JavaClasses imported) {
    noClasses().should().callMethod(Throwable.class, "printStackTrace").check(imported);
  }

  /**
   * Endpoints administrativos exigem autorização granular via {@code @PreAuthorize}. Convenção:
   * qualquer classe cujo nome começa com {@code Admin} e termina em {@code Controller} (ex: {@code
   * AdminUserController}) precisa anotar TODOS os métodos mapeados em HTTP com
   * {@code @PreAuthorize}.
   *
   * <p>Controllers públicos de auth ({@code AuthController}) e de plataforma ({@code
   * PlatformTenantController}, gated por header {@code X-Bootstrap-Token}) ficam fora dessa regra —
   * já são protegidos no SecurityConfig.
   */
  @ArchTest
  public void endpoints_admin_tem_PreAuthorize(JavaClasses imported) {
    methods()
        .that()
        .areDeclaredInClassesThat()
        .haveSimpleNameStartingWith("Admin")
        .and()
        .areDeclaredInClassesThat()
        .haveSimpleNameEndingWith("Controller")
        .and(MAPEADO_EM_HTTP)
        .should()
        .beAnnotatedWith(PreAuthorize.class)
        .check(imported);
  }

  /**
   * Controllers de domínio do {@code cadastros} (todos sob {@code internal/web/}) exigem permission
   * granular — sem rota anônima nesse módulo. Equivalente à regra de Admin*Controller, mas aplicada
   * por pacote em vez de prefixo de nome.
   */
  @ArchTest
  public void endpoints_cadastros_tem_PreAuthorize(JavaClasses imported) {
    methods()
        .that()
        .areDeclaredInClassesThat()
        .resideInAPackage("dev.dimi.erp.cadastros.internal.web..")
        .and(MAPEADO_EM_HTTP)
        .should()
        .beAnnotatedWith(PreAuthorize.class)
        .check(imported);
  }

  /**
   * Mesma regra do cadastros aplicada a {@code produto/internal/web/} — toda rota HTTP exige
   * permission granular ({@code produto.*}).
   */
  @ArchTest
  public void endpoints_produto_tem_PreAuthorize(JavaClasses imported) {
    methods()
        .that()
        .areDeclaredInClassesThat()
        .resideInAPackage("dev.dimi.erp.produto.internal.web..")
        .and(MAPEADO_EM_HTTP)
        .should()
        .beAnnotatedWith(PreAuthorize.class)
        .check(imported);
  }

  /**
   * Estoque: toda rota HTTP exige permission granular ({@code estoque.*}). Operações sensíveis
   * (adjustment, reverse) também passam por step-up no controller.
   */
  @ArchTest
  public void endpoints_estoque_tem_PreAuthorize(JavaClasses imported) {
    methods()
        .that()
        .areDeclaredInClassesThat()
        .resideInAPackage("dev.dimi.erp.estoque.internal.web..")
        .and(MAPEADO_EM_HTTP)
        .should()
        .beAnnotatedWith(PreAuthorize.class)
        .check(imported);
  }

  /**
   * Movimentação: toda rota HTTP exige permission granular ({@code pedido.*}, {@code venda.*},
   * etc). Operações sensíveis adicionam step-up nos controllers do slice futuro (cancelamento,
   * saída avulsa).
   */
  @ArchTest
  public void endpoints_movimentacao_tem_PreAuthorize(JavaClasses imported) {
    methods()
        .that()
        .areDeclaredInClassesThat()
        .resideInAPackage("dev.dimi.erp.movimentacao.internal.web..")
        .and(MAPEADO_EM_HTTP)
        .should()
        .beAnnotatedWith(PreAuthorize.class)
        .check(imported);
  }

  /**
   * Compras: toda rota HTTP exige permission granular ({@code compra.*}). Cancelamento de PC,
   * devolução a fornecedor e recebimento excedente passam por step-up nos controllers.
   */
  @ArchTest
  public void endpoints_compras_tem_PreAuthorize(JavaClasses imported) {
    methods()
        .that()
        .areDeclaredInClassesThat()
        .resideInAPackage("dev.dimi.erp.compras.internal.web..")
        .and(MAPEADO_EM_HTTP)
        .should()
        .beAnnotatedWith(PreAuthorize.class)
        .check(imported);
  }

  /**
   * Financeiro: toda rota HTTP exige permission granular ({@code mvf.*}, {@code pagamento.*}, etc).
   * Cancelar/renegociar MVF e estornar pagamento passam por step-up nos controllers.
   */
  @ArchTest
  public void endpoints_financeiro_tem_PreAuthorize(JavaClasses imported) {
    methods()
        .that()
        .areDeclaredInClassesThat()
        .resideInAPackage("dev.dimi.erp.financeiro.internal.web..")
        .and(MAPEADO_EM_HTTP)
        .should()
        .beAnnotatedWith(PreAuthorize.class)
        .check(imported);
  }

  /**
   * Listeners async ({@code @ApplicationModuleListener}) só são instanciados pelo Spring se a
   * classe tiver um stereotype ({@code @Component} ou derivados). Sem isso, o evento é publicado
   * mas nunca consumido — bug que custou retrabalho em financeiro 7.3 e auditoria.
   */
  @ArchTest
  public void listeners_modulith_tem_Component(JavaClasses imported) {
    classes()
        .that(
            new DescribedPredicate<JavaClass>("contêm método @ApplicationModuleListener") {
              @Override
              public boolean test(JavaClass c) {
                return c.getMethods().stream()
                    .anyMatch(m -> m.isAnnotatedWith(ApplicationModuleListener.class));
              }
            })
        .should()
        .beAnnotatedWith(Component.class)
        .orShould()
        .beMetaAnnotatedWith(Component.class)
        .check(imported);
  }
}
