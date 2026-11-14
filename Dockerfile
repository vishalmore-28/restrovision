# ==============================================================================
# RESTROVISION v2.0 - Root Multi-Stage Dockerfile for Cloud Platforms (Railway, Render, Koyeb)
# ==============================================================================

# --- Stage 1: Build Java 21 Backend ---
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /workspace

# Copy backend pom and cache dependencies
COPY Backend/pom.xml ./pom.xml
RUN mvn dependency:go-offline -B || true

# Copy backend source code and build production jar
COPY Backend/src ./src
RUN mvn clean package -DskipTests -B

# --- Stage 2: Runtime Container ---
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
RUN apk add --no-cache curl

WORKDIR /app

COPY --from=builder --chown=appuser:appgroup /workspace/target/restrovision-backend.jar app.jar

USER appuser:appgroup

EXPOSE 8080
ENV PORT=8080
ENV RESTROVISION_COOKIE_SECURE=false

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
