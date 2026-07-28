# 07 — Testes

Estratégia de testes do Modular ERP POC. Testes são **parte do contrato de pronto** — feature sem teste não merge.

## Princípios

1. **Pirâmide clássica** — muitos unitários (rápidos), alguns de integração (médios), poucos E2E (lentos).
2. **Testcontainers (Postgres real) para integração** — sem H2, sem mock de banco.
3. **ArchUnit + Modulith Tests** validam arquitetura, não só lógica.
4. **Tests as documentation** — nomes claros descrevem comportamento esperado.
5. **Sem flakiness aceita** — teste intermitente é bug; ou estabiliza, ou deleta.
6. **Coverage > 80%** em domínio; **branch coverage** > 70% em regras de negócio. Métrica é piso, não meta.

## Pirâmide

```
                  ┌──────────┐
                  │   E2E    │   ← smoke tests críticos via REST Assured
                  │  (5-10)  │
                ┌─┴──────────┴─┐
                │  Integration │  ← @SpringBootTest + Testcontainers
                │   (100-300)  │
              ┌─┴──────────────┴─┐
              │      Unit         │  ← POJO, JUnit + Mockito + AssertJ
              │    (500-2000)     │
              └───────────────────┘
```

Proporção esperada por slice típico:

- **Unit**: 70% dos testes, 30% do tempo de execução.
- **Integration**: 28% dos testes, 60% do tempo.
- **E2E**: 2% dos testes, 10% do tempo.

## Estrutura

```
src/test/java/dev/dimi/erp/
├── arch/                                       ← ArchUnit tests
│   ├── ArchitectureTest.java
│   └── ModuleStructureTest.java
├── shared/                                     ← unit tests de VOs comuns
│   └── vo/
│       ├── CpfTest.java
│       └── ...
├── identidade/
│   ├── internal/application/                   ← unit tests com mocks
│   │   ├── RegisterUserServiceTest.java
│   │   └── ...
│   └── IdentidadeModuleTest.java               ← @ApplicationModuleTest
├── fiscal/
│   ├── internal/application/
│   └── FiscalModuleTest.java
├── integration/                                ← integração cross-module
│   ├── PosCloseSaleIT.java
│   └── PurchaseReceiveIT.java
├── e2e/                                        ← smoke tests
│   └── HealthSmokeTest.java
├── fixtures/                                   ← builders e factories
│   ├── PersonFixture.java
│   ├── InvoiceFixture.java
│   └── TenantFixture.java
└── support/                                    ← infra de teste compartilhada
    ├── AbstractIntegrationTest.java
    ├── PostgresContainerSupport.java
    └── TestTenantContext.java
```

### Sufixos

- `*Test.java` — Surefire (unit, rodam em `mvn test`).
- `*IT.java` — Failsafe (integração, rodam em `mvn verify`).
- Modulith tests usam `@ApplicationModuleTest` e ficam dentro de cada módulo (`<Modulo>ModuleTest.java`).

## Unit tests

### Características

- **Sem Spring** — instanciar classes direto via construtor.
- **Mock de colaboradores** quando necessário (Mockito).
- **Sem banco** — repositories são interfaces; mockar via Mockito ou usar Fake in-memory.
- **Determinísticos** — `Clock` injetado, sem `Instant.now()` direto.
- **Rápidos** — < 50ms por teste; suite inteira < 30s.

### Exemplo

```java
class RegisterPersonServiceTest {

    PersonRepository persons = mock(PersonRepository.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    Clock clock = Clock.fixed(Instant.parse("2026-05-15T10:00:00Z"), ZoneOffset.UTC);

    RegisterPersonService service = new RegisterPersonService(persons, events, clock);

    @Nested
    @DisplayName("register(cmd)")
    class Register {

        @Test
        void quandoCpfNovo_devePersistirEPublicarEvento() {
            when(persons.existsByCpf_Value("12345678909")).thenReturn(false);
            when(persons.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var cmd = new RegisterPersonCommand("João", new Cpf("12345678909"), PersonType.PF);

            var id = service.register(cmd);

            assertThat(id).isNotNull();
            verify(persons).save(argThat(p ->
                p.getName().equals("João") && p.getCpf().value().equals("12345678909")));
            verify(events).publishEvent(argThat(e ->
                e instanceof PersonRegisteredEvent ev && ev.personId().equals(id)));
        }

        @Test
        void quandoCpfJaExiste_deveLancarDuplicateCpfException() {
            when(persons.existsByCpf_Value("12345678909")).thenReturn(true);

            var cmd = new RegisterPersonCommand("Outra", new Cpf("12345678909"), PersonType.PF);

            assertThatThrownBy(() -> service.register(cmd))
                .isInstanceOf(DuplicateCpfException.class)
                .hasFieldOrPropertyWithValue("cpf", "12345678909");

            verify(persons, never()).save(any());
            verify(events, never()).publishEvent(any());
        }
    }
}
```

### Naming

`<metodo>_<situacao>_<resultadoEsperado>`:

- `register_quandoCpfNovo_devePersistirEPublicarEvento`
- `register_quandoCpfJaExiste_deveLancarDuplicateCpfException`
- `cancel_quandoNotaJaCancelada_deveLancarConflictException`
- `emit_quandoFilialSemCertificado_deveLancarFilialSemCertificadoException`

Aceitar variações: `<comportamento>` direto quando descrever o cenário inteiro:

- `naoDevePermitirCancelarNotaJaCancelada`
- `devePropagarTenantContextEmEventosAssincronos`

### Mocks com cuidado

- **Mocks de objetos próprios** (repositories, ports) — OK.
- **Mocks de Value Objects** — proibido (instancie o real).
- **Mocks de classes finais ou third-party complexas** — preferir wrapper próprio que aí mocka.
- **`@MockBean` em integração** — preferir fakes injetados via `@TestConfiguration`.

## Integration tests

### Características

- **`@SpringBootTest`** carregando a aplicação real (ou slice via `@WebMvcTest`/`@DataJpaTest`).
- **Postgres via Testcontainers** — schema dedicado para teste, migrations aplicadas.
- **Tenant context setado** via `TestTenantContext` (helper).
- **Transações isoladas** — cada teste em transação rollback (`@Transactional` no teste), exceto quando precisar testar commit (eventos pós-commit, listeners).
- **Truncate entre testes** quando rollback não cabe — método `truncateTenantTables()` no base.

### Base class

```java
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("modular_erp_poc_test")
        .withReuse(true);

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired protected TenantProvisioningService provisioner;
    @Autowired protected TransactionTemplate tx;
    @Autowired protected DataSource dataSource;

    protected TenantId activeTenantId;

    @BeforeEach
    void setupTenant() {
        var t = provisioner.provision(ProvisionTenantCommand.synthetic());
        activeTenantId = t.id();
        TestTenantContext.set(t);
        truncateTenantTables(t.schemaName());
    }

    @AfterEach
    void tearDownTenant() {
        TestTenantContext.clear();
        dropSchemaQuietly(activeTenantId);
    }

    protected void truncateTenantTables(String schema) {
        // método compartilhado: TRUNCATE de todas as tabelas de domínio do tenant
        // listadas explicitamente para evitar truncar tabelas que ainda não existem
    }
}
```

### Exemplo de controller IT

```java
class PersonControllerIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void post_quandoPayloadValido_deveCriarPessoaERetornar201() throws Exception {
        var req = new CreatePersonRequest("João", "123.456.789-09", "PF", null, null, List.of());

        mvc.perform(post("/api/v1/cadastros/persons")
                .header("Authorization", "Bearer " + tokenFor("OWNER"))
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").value("João"))
            .andExpect(jsonPath("$.cpf").value("12345678909"));

        // verifica que evento foi publicado e processado
        assertThat(personRepository.count()).isEqualTo(1);
        assertThat(auditRepository.findByEventType("PERSON_REGISTERED")).hasSize(1);
    }

    @Test
    void post_quandoSemPermissao_deveRetornar403() throws Exception {
        var req = new CreatePersonRequest("João", "123.456.789-09", "PF", null, null, List.of());

        mvc.perform(post("/api/v1/cadastros/persons")
                .header("Authorization", "Bearer " + tokenFor("CONSULTA"))
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.type").value(endsWith("/problems/forbidden")));
    }
}
```

### Helper de token

`AbstractIntegrationTest.tokenFor(role)` gera JWT válido (assinado com mesma chave) para um usuário sintético com a role pedida — evita executar fluxo de login completo em todos os testes.

### Testes de evento pós-commit

Quando precisar testar listeners que rodam após commit:

```java
@Test
void closeSale_devePublicarEventoEProcessarBaixaDeEstoque() {
    var saleId = tx.execute(s -> closeSaleService.close(/* ... */));

    // wait for async listener (Modulith API)
    eventually(() -> {
        var stock = stockRepository.findByProduct(productId);
        assertThat(stock.getQuantity()).isEqualByComparingTo("9");  // 10 - 1
    });
}

protected void eventually(ThrowingRunnable assertion) {
    Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(assertion);
}
```

## Spring Modulith tests

### Validação de topologia

```java
class ModuleStructureTest {

    static final ApplicationModules modules = ApplicationModules.of(ModularErpPocApplication.class);

    @Test
    void valida_topologia_dos_modulos() {
        modules.verify();
    }

    @Test
    void escreve_documentacao() {
        new Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml()
            .writeAggregatingDocument();
    }
}
```

Falha se algum módulo importa fora do `allowedDependencies`. Roda em todo CI.

### Testes por módulo

```java
@ApplicationModuleTest(extraIncludes = "dev.dimi.erp.shared")
class FiscalModuleTest {

    @Autowired EmitInvoiceService emitInvoice;

    @Test
    void emit_publishesAuthorizedEvent(Scenario scenario) {
        var cmd = EmitInvoiceCommand.minimal();
        scenario.stimulate(() -> emitInvoice.emit(cmd))
                .andWaitForEventOfType(InvoiceAuthorizedEvent.class)
                .toArriveAndVerify(ev -> {
                    assertThat(ev.tenantId()).isEqualTo(/* tenant esperado */);
                });
    }
}
```

`@ApplicationModuleTest` carrega só o módulo + dependências declaradas — feedback rápido sem subir app inteira.

## ArchUnit

### Regras vigentes (em `src/test/java/dev/dimi/erp/arch/ArchitectureTest.java`)

```java
@AnalyzeClasses(packages = "dev.dimi.erp")
class ArchitectureTest {

    @ArchTest
    static final ArchRule internal_nao_e_acessado_de_fora_do_modulo =
        classes().that().resideInAPackage("..internal..")
            .should().onlyBeAccessed().byClassesThat()
            .resideInAPackage("..internal..").orShould().resideInTheSamePackage();

    @ArchTest
    static final ArchRule controllers_nao_chamam_repositories =
        noClasses().that().resideInAPackage("..web..")
            .should().dependOnClassesThat().resideInAPackage("..persistence..");

    @ArchTest
    static final ArchRule entidades_jpa_ficam_em_domain_interno =
        classes().that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage("..internal.domain..");

    @ArchTest
    static final ArchRule controllers_nao_recebem_tenant_id_como_parametro =
        noMethods().that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .should().haveRawParameterTypes(types -> Arrays.stream(types)
                .anyMatch(t -> t.getSimpleName().equals("TenantId")));

    @ArchTest
    static final ArchRule services_publicos_sao_interfaces =
        classes().that().resideInAnyPackage("dev.dimi.erp.*")
            .and().haveSimpleNameEndingWith("Port")
            .should().beInterfaces();

    @ArchTest
    static final ArchRule sem_System_out =
        noClasses().should().callMethod(System.class, "out");

    @ArchTest
    static final ArchRule sem_printStackTrace =
        noClasses().should().callMethod(Throwable.class, "printStackTrace");

    @ArchTest
    static final ArchRule entidade_jpa_sem_data_annotation =
        noClasses().that().areAnnotatedWith(Entity.class)
            .should().beAnnotatedWith(Data.class);

    @ArchTest
    static final ArchRule controllers_tem_preauthorize =
        methods().that().areAnnotatedWith(GetMapping.class).or().areAnnotatedWith(PostMapping.class)
            .or().areAnnotatedWith(PutMapping.class).or().areAnnotatedWith(PatchMapping.class)
            .or().areAnnotatedWith(DeleteMapping.class)
            .and().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .and().areDeclaredInClassesThat().resideInAPackage("..web..")
            // não exige em /actuator, /auth/login etc
            .should(haveAuthorizationAnnotation());
}
```

## E2E tests

### Stack

- **REST Assured** rodando contra app real (Docker Compose ou Testcontainers compose).
- Apenas **smoke tests críticos**: login, criar pessoa, emitir NFe mock, criar venda, cancelar venda.
- Não substituem integration tests — são canário de "está tudo de pé".

### Exemplo

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
class HealthSmokeTest extends AbstractIntegrationTest {

    @LocalServerPort int port;

    @Test
    void appHealthIsUp() {
        given().port(port)
            .when().get("/actuator/health")
            .then().statusCode(200).body("status", is("UP"));
    }

    @Test
    void loginEmitInvoiceCancelInvoiceFlow() {
        // cria tenant, faz login, cria pessoa, emite invoice mock, cancela
        var token = login("owner@example.invalid", fixturePassword());
        var personId = createPerson(token, "Cliente Teste", "12345678909");
        var invoiceId = emitInvoice(token, personId);
        cancelInvoice(token, invoiceId, "teste");
        // verifica estado final
    }
}
```

## Testes de carga

Não fazem parte do CI regular. Rodados manualmente antes de releases maiores:

- **k6** ou **Gatling** com scripts em `infra/load/`.
- Cenários: 100 vendas/min, emissão de 1000 NFes em 10min, login burst 500 req/min.
- Métricas validadas: p95 latency, error rate, DB connections, heap.
- Resultados arquivados em `infra/load/results/<data>.md`.

## Mutations testing

`PIT` (Pitest) configurado em `pom.xml`, **opcional** — rodado em milestones para verificar qualidade dos testes (testes que não detectam mutações artificiais são fracos).

```bash
./mvnw org.pitest:pitest-maven:mutationCoverage
```

Meta: > 60% mutation score em pacotes de domínio.

## Snapshot tests

Para serializações JSON estáveis (eventos publicados, payloads de webhook), usar **JsonAssertj** ou **JSON Approval** para detectar mudanças não-intencionais.

```java
@Test
void payloadDeInvoiceAuthorizedEvent_naoMuda() {
    var event = new InvoiceAuthorizedEvent(
        new InvoiceId(UUID.fromString("01HXYZ...")),
        new TenantId(UUID.fromString("01ABC...")),
        "NFe 123",
        Money.of("100.00"),
        Instant.parse("2026-05-15T10:00:00Z")
    );

    var json = mapper.writeValueAsString(event);

    Approvals.verifyJson(json);   // gravado em src/test/resources/approved/
}
```

Mudança intencional: rodar com `-Dapprovals.update=true` para regravar.

## Cobertura

### JaCoCo

```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.12</version>
  <executions>
    <execution>
      <goals><goal>prepare-agent</goal></goals>
    </execution>
    <execution>
      <id>report</id>
      <phase>verify</phase>
      <goals><goal>report</goal></goals>
    </execution>
    <execution>
      <id>check</id>
      <phase>verify</phase>
      <goals><goal>check</goal></goals>
      <configuration>
        <rules>
          <rule>
            <element>BUNDLE</element>
            <limits>
              <limit>
                <counter>INSTRUCTION</counter>
                <value>COVEREDRATIO</value>
                <minimum>0.80</minimum>
              </limit>
            </limits>
          </rule>
        </rules>
      </configuration>
    </execution>
  </executions>
</plugin>
```

### Metas

- **Domínio (`dev.dimi.erp.*.internal.domain`, `*.application`)**: ≥ 80% instructions, ≥ 70% branches.
- **Web (`*.web`)**: ≥ 70% (cobertura por integration tests).
- **Total**: ≥ 75%.
- **Excluído da meta**: `*Config`, DTOs simples, generated code.

Cobertura é **piso, não meta** — alto sem testes significativos é vaidade.

## Fixtures e builders

Pasta `src/test/java/.../fixtures/` com builders que escondem boilerplate:

```java
public final class PersonFixture {

    public static Person valid() {
        return validBuilder().build();
    }

    public static Person.Builder validBuilder() {
        return Person.builder()
            .id(UuidCreator.getTimeOrderedEpoch())
            .name("Pessoa Teste")
            .cpf(new Cpf("12345678909"))
            .type(PersonType.PF)
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now());
    }

    public static Person withCpf(String cpf) {
        return validBuilder().cpf(new Cpf(cpf)).build();
    }

    private PersonFixture() {}
}
```

Uso:

```java
var person = PersonFixture.valid();
var person2 = PersonFixture.withCpf("11111111111");
```

## Convenções operacionais

### Velocidade

- Suite **unit** completa: **< 30s**.
- Suite **integration** completa: **< 5min**.
- Suite **E2E**: **< 2min** (poucos cenários).
- **Total CI**: **< 10min** (com paralelismo).

### Paralelismo

- Surefire/Failsafe configurados com `<parallel>methods</parallel>` quando seguro.
- Testes que tocam estado compartilhado isolam tenant próprio (cada IT cria seu tenant).
- Testcontainers `reuse.enable=true` para evitar boot repetido.

### Flakiness

- **Tolerância zero**: teste flaky é desabilitado (`@Disabled("flaky #issue-123")`) e investigado em 48h, ou deletado.
- **Race conditions** em listeners async: usar `Awaitility` em vez de `Thread.sleep`.
- **Datas/timezones**: `Clock` injetado, `LocalDate.now(clock)`.

## Não testar

- Getters/setters triviais (Lombok-generated).
- Anotações Spring (framework já testa).
- Configs estáticas (`@Configuration` sem lógica).
- Código de bootstrap (`main()`).

## Anti-padrões

- **Teste com `if/else`** — quebrar em N testes separados.
- **Teste que depende de outro teste** — ordem aleatória deve passar.
- **Teste que depende de internet** — mockar HTTP via WireMock/MockServer.
- **Mock excessivo** (everything is a mock) — teste de coordenação, não comportamento real. Preferir integração quando o caso pede.
- **Asserção genérica** (`assertNotNull(result)`) — falha não informa nada útil.
- **Logs no teste** — se precisar de log para entender o teste, simplifica o teste.
- **Comentários explicando o teste** — refatorar para o código ser auto-explicativo.

## Workflow de teste no slice

1. Escreve teste **unitário** para regra de negócio nova **antes** ou junto com o código (TDD não obrigatório, mas testes simultâneos).
2. Escreve teste **integration** para fluxo completo via controller.
3. Atualiza **ArchUnit** se regra arquitetural nova surgir.
4. Roda `./mvnw verify` — verde antes do commit.
5. CI repete tudo + JaCoCo, Gitleaks e Dependency Review em PRs.

## Roadmap

- **v1.1**: contract testing entre módulos via Pact (quando algum vira serviço).
- **v1.2**: chaos testing (Chaos Mesh) em staging — derrubar pod, DB, ACBr para validar resiliência.
- **v2**: load testing automatizado em PR (k6 contra preview env).
