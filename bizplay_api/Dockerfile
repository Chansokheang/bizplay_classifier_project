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

# Networks with TLS interception (the lab's Somansa proxy) re-sign every HTTPS connection, so
# Maven's downloads from Central die with "PKIX path building failed" unless the proxy's CA is
# trusted by THIS container's JVM. Any .pem/.crt in docker/trusted-ca/ is imported; the folder
# ships with only a README, so builds on open networks are byte-for-byte unaffected.
# See docker/trusted-ca/README.md for how to capture the proxy CA on the server.
COPY docker/trusted-ca/ /tmp/trusted-ca/
RUN for c in /tmp/trusted-ca/*.pem /tmp/trusted-ca/*.crt; do \
      [ -f "$c" ] || continue; \
      keytool -importcert -noprompt -trustcacerts -cacerts \
        -storepass changeit -alias "extra-$(basename "$c")" -file "$c"; \
    done

# The POM alone first, so the dependency layer stays cached until dependencies change.
COPY pom.xml ./

# The wrapper is deliberately NOT used here. This base image already ships Maven 3.9.9,
# while ./mvnw downloads its own distribution (3.9.12, see .mvn/wrapper) from
# repo.maven.apache.org on every cold build - a second network dependency the build does
# not need, plus the exec-bit and CRLF pitfalls a wrapper copied from a Windows checkout
# brings with it.
RUN mvn -B -DskipTests dependency:go-offline || true

# Copy source code
COPY src src

# Build the application. No -q: when this step fails inside Docker the log IS the
# diagnosis, and a quiet Maven leaves nothing behind but a line number.
RUN mvn -B -DskipTests clean package

# --------------------------
# Stage 2: Runtime
# --------------------------
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="BizPlay Team"
LABEL description="BizPlay Classifier API - Runtime Image"
LABEL version="1.1"

# No package install here on purpose. curl was only ever used by the HEALTHCHECK below,
# and Alpine already ships BusyBox wget - so reaching out to the Alpine CDN mid-build
# bought nothing, and failed closed on any network that cannot reach it.

# Create non-root user for security
RUN addgroup -g 1001 -S bizplay && \
    adduser -u 1001 -S bizplay -G bizplay

# Same proxy-CA trust as the build stage — the RUNNING app calls cloud-dev.bizplay.biz and the
# LLM endpoints over HTTPS through the same intercepting proxy. Runs before USER bizplay
# because the JVM truststore belongs to root. No-op when the folder holds only the README.
COPY docker/trusted-ca/ /tmp/trusted-ca/
RUN for c in /tmp/trusted-ca/*.pem /tmp/trusted-ca/*.crt; do \
      [ -f "$c" ] || continue; \
      keytool -importcert -noprompt -trustcacerts -cacerts \
        -storepass changeit -alias "extra-$(basename "$c")" -file "$c"; \
    done && rm -rf /tmp/trusted-ca

WORKDIR /app

# Copy JAR from build stage
COPY --from=build --chown=bizplay:bizplay /workspace/target/*.jar app.jar

# Switch to non-root user
USER bizplay

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:8080/v3/api-docs || exit 1

# JVM optimization flags
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+OptimizeStringConcat \
    -XX:+UseStringDeduplication \
    -Djava.security.egd=file:/dev/./urandom"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
