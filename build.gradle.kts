import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    application
    kotlin("jvm") version Kotlin.version
    id(Spotless.spotless) version Spotless.version
    id(Shadow.shadow) version Shadow.version
}

repositories {
    jcenter()
    maven("http://packages.confluent.io/maven/")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation(Kafka.clients)

    implementation(Prometheus.common)
    implementation(Prometheus.hotspot)
    implementation(Prometheus.log4j2)

    implementation(Ktor.serverNetty)
    implementation(Ktor.library("gson"))

    implementation(Kotlin.Logging.kotlinLogging)
    implementation(Log4j2.api)
    implementation(Log4j2.core)
    implementation(Log4j2.slf4j)
    implementation(Log4j2.Logstash.logstashLayout)

    testImplementation(KafkaEmbedded.env)
    testImplementation(Junit5.api)
    testRuntimeOnly(Junit5.engine)
}

application {
    applicationName = "kafka-offset-monitor"
    mainClassName = "no.nav.kafka.ConsumerOffsetExporter"
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

configurations.all {
    resolutionStrategy.activateDependencyLocking()
    resolutionStrategy.preferProjectModules()
    resolutionStrategy.eachDependency { DependencyResolver.execute(this) }
}

spotless {
    kotlin {
        ktlint(Klint.version)
    }
    kotlinGradle {
        target("*.gradle.kts", "additionalScripts/*.gradle.kts")
        ktlint(Klint.version)
    }
}

val ktlint by configurations.creating

dependencies {
    ktlint("com.github.shyiko:ktlint:${Klint.version}")
}

val klintIdea by tasks.creating(JavaExec::class) {
    description = "Apply ktlint rules to IntelliJ"
    classpath = ktlint
    main = "com.github.shyiko.ktlint.Main"
    args = listOf("--apply-to-idea-project", "-y")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        showExceptions = true
        showStackTraces = true
        exceptionFormat = TestExceptionFormat.FULL
        events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}

tasks.withType<Wrapper> {
    gradleVersion = "6.0.1"
}