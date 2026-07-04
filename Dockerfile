# ---- Stage 1: Build ----
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

# Copy Gradle wrapper first (for layer caching)
COPY gradle gradle
COPY gradlew gradlew
COPY gradlew.bat gradlew.bat
COPY settings.gradle.kts settings.gradle.kts
COPY build.gradle.kts build.gradle.kts
# gradle.properties is optional — glob-only form so a missing file doesn't fail the build
COPY gradle.properties* ./

# Prime Gradle wrapper (downloads Gradle distro, caches in layer)
RUN chmod +x gradlew && ./gradlew --version

# Copy source
COPY src src

# Build fat JAR
RUN ./gradlew buildFatJar --no-daemon

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy only the built fat JAR from the builder stage
COPY --from=builder /app/build/libs/*-all.jar app.jar

# Render assigns port via $PORT env var; app reads it via application.yaml
# EXPOSE is documentation-only; actual port comes from env
EXPOSE 8080

# Run with sensible JVM defaults for a container
# -XX:MaxRAMPercentage lets JVM heap scale with container RAM (Render Free = 512MB)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
