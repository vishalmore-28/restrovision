# ==============================================================================
# RESTROVISION v2.0 - Production Backend Multi-Stage Dockerfile
# Base: Eclipse Temurin OpenJDK 21 on Alpine Linux
# Security: Non-root execution under appuser, minimal surface attack area
# Health Check: Spring Boot Actuator (/actuator/health)
# ==============================================================================

# --- Stage 1: Build & Package ---
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /workspace

# Cache Maven dependencies layer
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source code and build jar
COPY src ./src
RUN mvn clean package -DskipTests -B

# --- Stage 2: Runtime Minimal Distroless/Alpine Container ---
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="RESTROVISION Platform Security Team <security@restrovision.com>"
LABEL version="2.0.0"
LABEL description="RESTROVISION Production Spring Boot Service"

# Security: Create non-root system group and user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Install curl for robust Actuator health check probes
RUN apk add --no-cache curl

WORKDIR /app

# Copy the built artifact with non-root ownership
COPY --from=builder --chown=appuser:appgroup /workspace/target/restrovision-backend.jar app.jar

# Drop root privileges
USER appuser:appgroup

EXPOSE 8080

# Production Health Check Probe using Spring Boot Actuator
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Production JVM optimizations for container environments
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
