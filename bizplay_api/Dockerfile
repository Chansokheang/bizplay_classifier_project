# =============================================================
# BizPlay Classifier API - Multi-stage Docker Build
# =============================================================

# --------------------------
# Stage 1: Build
# --------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS build

LABEL maintainer="BizPlay Team"
LABEL description="BizPlay Classifier API - Corporate Expense Classification Service"
LABEL version="1.1"

WORKDIR /workspace

# Copy Maven wrapper and dependencies configuration
COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn

# Make Maven wrapper executable
RUN chmod +x mvnw

# Download dependencies (cached layer)
RUN ./mvnw -q -DskipTests dependency:go-offline || true

# Copy source code
COPY src src

# Build the application
RUN ./mvnw -q -DskipTests clean package

# --------------------------
# Stage 2: Runtime
# --------------------------
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="BizPlay Team"
LABEL description="BizPlay Classifier API - Runtime Image"
LABEL version="1.1"

# Install curl for health checks
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -g 1001 -S bizplay && \
    adduser -u 1001 -S bizplay -G bizplay

WORKDIR /app

# Copy JAR from build stage
COPY --from=build --chown=bizplay:bizplay /workspace/target/*.jar app.jar

# Switch to non-root user
USER bizplay

# Expose application port
EXPOSE 8080

# Health check
# Note: Add spring-boot-starter-actuator to pom.xml to enable /actuator/health endpoint
# For now, checking if port 8080 is responding
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/ || exit 1

# JVM optimization flags
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+OptimizeStringConcat \
    -XX:+UseStringDeduplication \
    -Djava.security.egd=file:/dev/./urandom"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
