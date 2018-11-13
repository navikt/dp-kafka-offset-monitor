import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.10"
    id("application")
}

val prometheusVersion = "0.5.0"
val ktorVersion = "1.0.0-beta-4"
val kotlinLoggingVersion = "1.4.9"

repositories {
    mavenCentral()
    maven("https://dl.bintray.com/kotlin/ktor")
    maven("https://dl.bintray.com/kotlin/kotlinx")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
    implementation("org.apache.kafka:kafka_2.11:2.0.1")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
}

application {
    applicationName = "kafka-offset-monitor"
    mainClassName = "no.nav.kafka.PrometheusConsumerOffsetMonitor"
}

