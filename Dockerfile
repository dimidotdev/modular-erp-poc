# syntax=docker/dockerfile:1.7
#
# Imagem da API Modular ERP POC — Spring Boot 3.5 / Java 21 LTS.
# Build multi-stage com JAR em camadas e execução sem privilégios.
#
# Build:    docker build -t modular-erp-poc:dev .
# Run:      docker run --rm -p 8080:8080 --network host \
#               -e SPRING_PROFILES_ACTIVE=local modular-erp-poc:dev

# ---- Stage 1: build ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Camada de dependências separada da camada de código para cache eficiente:
# alterações em src/ não invalidam o download do Maven repo.
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline

COPY src ./src
RUN ./mvnw -q -B -DskipTests clean package

# ---- Stage 2: layer extraction ----
# Spring Boot layered JARs separam dependencies / loader / snapshot / application.
# Isso permite que a layer 'application' (pequena, muda toda build) seja a única
# rebaixada quando código muda, sem refetchar libs grandes.
FROM eclipse-temurin:21-jre-jammy AS extract
WORKDIR /work
COPY --from=build /workspace/target/modular-erp-poc-*.jar app.jar
# Spring Boot 3.5+ — `jarmode=tools` substitui o `layertools` deprecated.
# `--destination` explícito evita o subfolder default que o nome do jar imprime.
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination /layers

# ---- Stage 3: runtime ----
FROM eclipse-temurin:21-jre-jammy

# Non-root: UID/GID fixos para execução previsível em containers.
RUN groupadd -g 1000 app \
 && useradd -u 1000 -g 1000 -m -s /sbin/nologin app
USER app
WORKDIR /app

# Ordem das layers do mais estável (raramente muda) para o mais volátil (muda toda build).
COPY --from=extract --chown=app:app /layers/dependencies/          ./
COPY --from=extract --chown=app:app /layers/spring-boot-loader/    ./
COPY --from=extract --chown=app:app /layers/snapshot-dependencies/ ./
COPY --from=extract --chown=app:app /layers/application/           ./

ENV JAVA_TOOL_OPTIONS="-XX:+UseG1GC \
                       -XX:+UseStringDeduplication \
                       -XX:MaxRAMPercentage=75 \
                       -Dnetworkaddress.cache.ttl=30"

EXPOSE 8080
ENTRYPOINT ["java","org.springframework.boot.loader.launch.JarLauncher"]
