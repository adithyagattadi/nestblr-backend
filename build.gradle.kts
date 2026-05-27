// NestBLR Backend — Ktor 3.5.0 + Exposed 1.0 + PostgreSQL/PostGIS
// Verified: May 2026 versions

val ktor_version = "3.5.0"
val kotlin_version = "2.3.20"
val exposed_version = "1.0.0"
val postgres_version = "42.7.4"
val hikari_version = "6.2.1"
val logback_version = "1.5.13"

plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    id("io.ktor.plugin") version "3.5.0"
}

group = "com.nestblr"
version = "0.0.1"

application {
    mainClass.set("com.nestblr.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor server
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-server-status-pages:$ktor_version")
    implementation("io.ktor:ktor-server-call-logging:$ktor_version")
    implementation("io.ktor:ktor-server-cors:$ktor_version")
    implementation("io.ktor:ktor-server-rate-limit:$ktor_version")
    implementation("io.ktor:ktor-server-config-yaml:$ktor_version")

    // Database — Exposed 1.0
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-json:$exposed_version")

    // PostgreSQL driver (PostGIS uses standard PG driver — spatial queries via raw SQL)
    implementation("org.postgresql:postgresql:$postgres_version")
    implementation("net.postgis:postgis-jdbc:2024.1.0") // PostGIS JDBC extension

    // Connection pool
    implementation("com.zaxxer:HikariCP:$hikari_version")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logback_version")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}

kotlin {
    jvmToolchain(21)
}
