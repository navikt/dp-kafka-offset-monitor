

plugins {
    kotlin("jvm") version "1.3.10"
    id("com.diffplug.gradle.spotless") version "3.13.0"
    id("application")
}

val prometheus_version = "0.5.0"
val ktor_version = "1.0.0-beta-4"
val kotlin_logging_version = "1.4.9"
val kafka_version = "2.0.1"

repositories {
    mavenCentral()
    jcenter()
    maven("https://dl.bintray.com/kotlin/ktor")
    maven("https://dl.bintray.com/kotlin/kotlinx")
    maven("https://dl.bintray.com/kittinunf/maven")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.github.microutils:kotlin-logging:$kotlin_logging_version")
    implementation("org.apache.kafka:kafka_2.11:2.0.1")
    implementation("io.prometheus:simpleclient_common:$prometheus_version")
    implementation("io.prometheus:simpleclient_hotspot:$prometheus_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-gson:$ktor_version")
    compile("org.apache.logging.log4j:log4j-api:2.11.1")
    compile("org.apache.logging.log4j:log4j-core:2.11.1")
    compile("org.apache.logging.log4j:log4j-slf4j-impl:2.11.1")
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
