

plugins {
    kotlin("jvm") version "1.3.10"
    id("com.diffplug.gradle.spotless") version "3.13.0"
    id("application")
}

val prometheus_version = "0.5.0"
val ktor_version = "1.0.0"
val kotlin_logging_version = "1.4.9"
val kafka_version = "2.0.1"
val log4j2_version = "2.11.1"

repositories {
    mavenCentral()
    maven("https://dl.bintray.com/kotlin/ktor")
    maven("https://dl.bintray.com/kotlin/kotlinx")
    maven("https://dl.bintray.com/kittinunf/maven")
    maven("http://packages.confluent.io/maven/")
    maven("https://dl.bintray.com/spekframework/spek-dev")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.github.microutils:kotlin-logging:$kotlin_logging_version")
    implementation("org.apache.kafka:kafka-clients:$kafka_version")

    implementation("io.prometheus:simpleclient_common:$prometheus_version")
    implementation("io.prometheus:simpleclient_hotspot:$prometheus_version")

    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-gson:$ktor_version")

    implementation("org.apache.logging.log4j:log4j-api:$log4j2_version")
    implementation("org.apache.logging.log4j:log4j-core:$log4j2_version")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:$log4j2_version")

    testImplementation ("org.spekframework.spek2:spek-dsl-jvm:2.0.0-alpha.1") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testImplementation ("org.spekframework.spek2:spek-runner-junit5:2.0.0-alpha.1") {
        exclude(group = "org.junit.platform")
        exclude(group = "org.jetbrains.kotlin")
    }

    // spek requires kotlin-reflect
    testRuntimeOnly (kotlin("reflect"))
    testImplementation("no.nav:kafka-embedded-env:2.0.1")
}

application {
    applicationName = "kafka-offset-monitor"
    mainClassName = "no.nav.kafka.PrometheusConsumerOffsetMonitor"
}

spotless {
    kotlin {
        ktlint()
    }
    kotlinGradle {
        target("*.gradle.kts", "additionalScripts/*.gradle.kts")
        ktlint()
    }
}

val ktlint by configurations.creating

dependencies {
    ktlint("com.github.shyiko:ktlint:0.29.0")
}

val klintIdea by tasks.creating(JavaExec::class) {
    description = "Apply ktlint rules to IntelliJ"
    classpath = ktlint
    main = "com.github.shyiko.ktlint.Main"
    args = listOf("--apply-to-idea-project", "-y")
}
