package no.nav.kafka

import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.http.ContentType
import io.ktor.response.respondText
import io.ktor.response.respondTextWriter
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Gauge
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import java.io.File
import java.util.Properties
import java.util.Timer
import java.util.concurrent.TimeUnit
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.concurrent.timer

private val LOGGER = KotlinLogging.logger {}

class ConsumerOffsetExporter(environment: Environment) {
    private val collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry
    private val kafkaCredential: KafkaCredential = KafkaCredential(environment.username, environment.password)
    private val consumer: KafkaConsumer<String, String> = createNewConsumer(environment)
    private val adminClient: AdminClient = createAdminClient(environment)
    private val httpPort: Int = 8080
    private val consumerGroups: String = environment.consumerGroups

    private val offsetLagGauge: Gauge = Gauge.build()
        .namespace(environment.namespace)
        .name("consumer_offset_lag")
        .help("Offset lag of a topic/partition")
        .labelNames("group_id", "partition", "topic")
        .register()

    init {
        DefaultExports.initialize()
        Runtime.getRuntime().addShutdownHook(Thread {
            LOGGER.info("Closing the application...")
            consumer.close()
            LOGGER.info("done!")
        })
        LOGGER.info("Monitoring consumer group(s) ${consumerGroups.split(",").joinToString { "\"$it\"\n" }}")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) = runBlocking {
            val offsetExporter = ConsumerOffsetExporter(Environment())
            offsetExporter.start()
        }
    }

    fun kafkaOffsetScraper() {
        LOGGER.info("Scraping consumer groups $consumerGroups")
        consumerGroups.split(",").forEach { group ->
            val consumerGroupOffsets = adminClient.listConsumerGroupOffsets(group)
            consumerGroupOffsets.partitionsToOffsetAndMetadata().whenComplete { topicPartitionsOffsets, throwable ->

                topicPartitionsOffsets?.forEach { topicPartition, offset ->
                    val currentOffset = offset.offset()
                    val endOffset = getLogEndOffset(topicPartition)
                    val lag = endOffset - currentOffset
                    offsetLagGauge.labels(group, topicPartition.partition().toString(), topicPartition.topic())
                        .set(lag.toDouble())
                    LOGGER.info("Lag is -> $lag for topic '${topicPartition.topic()}', partition ${topicPartition.partition()}, current offset $currentOffset , end offset $endOffset for group: $group")
                }
                throwable?.apply {
                    LOGGER.error(throwable) { "Failed to get offset data from consumer group $group" }
                }
            }
        }
    }

    private fun createAdminClient(environment: Environment): AdminClient {
        val props = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, environment.bootstrapServersUrl)
            putAll(credentialProperties())
        }
        return AdminClient.create(props)
    }

    suspend fun start() {
        LOGGER.info { "STARTING" }

        val timerTask = timer(
            name = "offset-checker-task",
            daemon = true,
            initialDelay = TimeUnit.SECONDS.toMillis(5),
            period = TimeUnit.SECONDS.toMillis(10)
        ) {
            LOGGER.info("Looping in timer task")
            kafkaOffsetScraper()
        }

        val app = embeddedServer(Netty, httpPort) {
            install(ContentNegotiation) {
                gson {
                    setPrettyPrinting()
                }
            }
            routing {
                get("/isAlive") {
                    call.respondText("ALIVE", ContentType.Text.Plain)
                }
                get("/isReady") {
                    call.respondText("READY", ContentType.Text.Plain)
                }
                get("/metrics") {
                    val names = call.request.queryParameters.getAll("name")?.toSet() ?: setOf()
                    call.respondTextWriter(ContentType.parse(TextFormat.CONTENT_TYPE_004)) {
                        TextFormat.write004(this, collectorRegistry.filteredMetricFamilySamples(names))
                    }
                }
            }
        }.start(wait = false)

        Runtime.getRuntime().addShutdownHook(Thread {
            app.stop(3, 5, TimeUnit.SECONDS)
        })
        Runtime.getRuntime().addShutdownHook(Thread {
            timerTask.cancel()
        })
    }

    private fun getLogEndOffset(topicPartition: TopicPartition): Long {
        consumer.assign(listOf(topicPartition))
        consumer.seekToEnd(listOf(topicPartition))
        return consumer.position(topicPartition) - 1
    }

    private fun createNewConsumer(environment: Environment): KafkaConsumer<String, String> {
        val groupId = environment.namespace + "-offsetchecker"

        val properties = Properties().apply {
            put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
            put(ConsumerConfig.CLIENT_ID_CONFIG, groupId)
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, environment.bootstrapServersUrl)
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
            put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000")
            put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer"
            )
            put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer"
            )
            putAll(credentialProperties())
        }
        return KafkaConsumer(properties)
    }

    private fun credentialProperties(): Properties {
        return Properties().apply {
            kafkaCredential.let { credential ->
                LOGGER.info { "Using user name ${credential.username} to authenticate against Kafka brokers " }
                put(SaslConfigs.SASL_MECHANISM, "PLAIN")
                put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT")
                put(
                    SaslConfigs.SASL_JAAS_CONFIG,
                    "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${credential.username}\" password=\"${credential.password}\";"
                )

                val trustStoreLocation = System.getenv("NAV_TRUSTSTORE_PATH")
                trustStoreLocation?.let {
                    try {
                        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL")
                        put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, File(it).absolutePath)
                        put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, System.getenv("NAV_TRUSTSTORE_PASSWORD"))
                        LOGGER.info { "Configured '${SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG}' location " }
                    } catch (e: Exception) {
                        LOGGER.error { "Failed to set '${SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG}' location " }
                    }
                }
            }
        }
    }

    data class KafkaCredential(val username: String, val password: String) {
        override fun toString(): String {
            return "username '$username' password '*******'"
        }
    }
}