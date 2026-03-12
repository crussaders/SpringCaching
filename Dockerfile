# ══════════════════════════════════════════════════════════════════════════════
# Multi-stage Dockerfile for Spring Boot application
# Stage 1: Build with Maven
# Stage 2: Run with minimal JRE
# ══════════════════════════════════════════════════════════════════════════════

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /workspace

# Copy Maven wrapper and POM first (for layer caching)
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies (cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# Copy source and build
COPY src src
RUN ./mvnw package -DskipTests -B

# Extract layers for the optimised runtime image
RUN mkdir -p target/extracted && \
    java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

# Copy Spring Boot layers (ordered for optimal Docker layer caching)
COPY --from=build /workspace/target/extracted/dependencies          ./
COPY --from=build /workspace/target/extracted/spring-boot-loader    ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies ./
COPY --from=build /workspace/target/extracted/application           ./

EXPOSE 8080

ENV JAVA_OPTS="-Xms256m -Xmx512m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
