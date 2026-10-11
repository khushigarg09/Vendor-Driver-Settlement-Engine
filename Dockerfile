# ==============================================================================
# Multi-Stage Dockerfile for MoveInSync Settlement Engine
# Java 21 LTS | Non-Root User | Distroless/Alpine Minimal Attack Surface
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Builder (Pre-installed Maven 3.9 + Java 21 JDK)
# ------------------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy POM
COPY pom.xml ./

# Pre-fetch dependencies
RUN mvn dependency:resolve -B || true

# Copy application source code
COPY src ./src

# Compile and package application JAR (tests skipped during image assembly)
RUN mvn clean package -DskipTests -B

# ------------------------------------------------------------------------------
# Stage 2: Hardened Non-Root Runtime
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Install curl and tzdata for health checks and timezone resolution
RUN apk add --no-cache curl tzdata

# Set timezone
ENV TZ=Asia/Kolkata

# Create non-root system group and user
RUN addgroup -g 1001 -S settlement && \
    adduser -u 1001 -S settlement -G settlement -h /home/settlement

WORKDIR /app

# Copy executable jar from builder stage
COPY --from=builder --chown=settlement:settlement /build/target/settlement-engine.jar /app/app.jar

# Switch to non-root user
USER settlement:settlement

# Expose Spring Boot HTTP port
EXPOSE 8080

# Configure container health probe against Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Optimized JVM memory and garbage collection parameters for container runtimes
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
