# syntax=docker/dockerfile:1
#
# Multi-stage build following the Spring Boot reference ("Dockerfiles" and "AOT Cache"):
#   1. build:   compile and package with the Maven wrapper (no local JDK or Maven needed)
#   2. runtime: the extracted, layered application on a JRE, with a Java 25 AOT cache
#               (classes pre-loaded and linked during the build, so startup is faster)
#
# Build: docker build -t api-spring-music .      (or: docker compose build)

ARG JDK_IMAGE=bellsoft/liberica-openjdk-debian:25
ARG JRE_IMAGE=bellsoft/liberica-openjre-debian:25-cds

FROM ${JDK_IMAGE} AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
# The cache mount keeps downloaded dependencies between builds.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -q -DskipTests package \
 && cp target/api-spring-music-*.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM ${JRE_IMAGE} AS runtime
WORKDIR /application
RUN useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin spring

# One layer per Spring Boot layer, least to most often changed, so a code change only
# replaces the small "application" layer.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

# AOT cache training run: start the application context and exit. No database is
# reachable during the build, so Flyway is skipped here (it runs on the real start).
RUN java -XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh -Dspring.flyway.enabled=false \
        -jar application.jar

USER spring
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness > /dev/null || exit 1

# Size the heap from the container's memory limit; JAVA_OPTS adds more flags at run time.
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java -XX:AOTCache=app.aot -XX:MaxRAMPercentage=75 $JAVA_OPTS -jar application.jar"]
