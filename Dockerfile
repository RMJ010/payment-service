# ─── Stage 1: Build ──────────────────────────────────────────────────────────
# maven:3.9-eclipse-temurin-17 has ARM64 + AMD64 builds — works on Apple Silicon
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Cache the dependency layer separately (only re-downloads when pom.xml changes)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build the application
COPY src ./src
RUN mvn clean package -DskipTests -q

# ─── Stage 2: Runtime ────────────────────────────────────────────────────────
# eclipse-temurin:17-jre (not alpine) has proper ARM64 support
FROM eclipse-temurin:17-jre

WORKDIR /app

# Install curl for healthcheck
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Copy built JAR from build stage
COPY --from=builder /app/target/payment-service-1.0.0.jar app.jar

# Non-root user for security
RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
