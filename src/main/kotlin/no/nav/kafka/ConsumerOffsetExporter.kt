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
import mu.KotlinLogging
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import java.util.Properties
import java.util.Timer
import java.util.concurrent.TimeUnit
import kotlin.concurrent.scheduleAtFixedRate

private val LOGGER = KotlinLogging.logger {}

class ConsumerOffsetExporter(environment: Environment) {
    private val collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry
    private val consumer: KafkaConsumer<String, String>
    private val client: AdminClient
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
        consumer = createNewConsumer(environment)
        client = createAdminClient(environment)

        Runtime.getRuntime().addShutdownHook(Thread {
            LOGGER.info("Closing the application...")
            consumer.close()
            LOGGER.info("done!")
        })
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val offsetExporter = ConsumerOffsetExporter(Environment())
            offsetExporter.start()
        }
    }

    private fun kafkaOffsetScraper() {
        consumerGroups.split(",").forEach { group ->
            val consumerGroupOffsets = client.listConsumerGroupOffsets(group)
            consumerGroupOffsets.partitionsToOffsetAndMetadata().whenComplete { topicPartitionsOffsets, throwable ->
                topicPartitionsOffsets?.forEach { topicPartition, offset ->
                    val currentOffset = offset.offset()
                    val lag = getLogEndOffset(topicPartition) - currentOffset
                    offsetLagGauge.labels(group, topicPartition.partition().toString(), topicPartition.topic())
                        .set(lag.toDouble())
                    LOGGER.debug("Lag is -> $lag for topic '${topicPartition.topic()}', partition ${topicPartition.partition()}, current offset $currentOffset")
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
        }
        return AdminClient.create(props)
    }

    fun start() {
        LOGGER.info { "STARTING" }
        val timer = Timer("offsetChecker", true)
        val timerTask = timer.scheduleAtFixedRate(TimeUnit.SECONDS.toMillis(5), TimeUnit.SECONDS.toMillis(10)) {
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
        }.start(wait = true)

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
        return consumer.position(topicPartition)
    }

    private fun createNewConsumer(environment: Environment): KafkaConsumer<String, String> {
        val properties = Properties()
        val groupId = environment.namespace + "-offsetchecker"
        properties[ConsumerConfig.GROUP_ID_CONFIG] = groupId
        properties[ConsumerConfig.CLIENT_ID_CONFIG] = groupId
        properties[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = environment.bootstrapServersUrl
        properties[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "false"
        properties[ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG] = "30000"
        properties[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] =
            "org.apache.kafka.common.serialization.StringDeserializer"
        properties[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] =
            "org.apache.kafka.common.serialization.StringDeserializer"
        return KafkaConsumer(properties)
    }
}