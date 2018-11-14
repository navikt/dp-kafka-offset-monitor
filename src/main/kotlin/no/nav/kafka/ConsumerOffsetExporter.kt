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
    private val httpPort: Int = environment.httpPort ?: 8089
    private val consumerGroups: String = environment.consumerGroups

    private val lagOffset: Gauge = Gauge.build()
        .namespace(environment.namespace)
        .name("consumer_offset_lag")
        .help("Offset lag of a topic/partition")
        .labelNames("group_id", "partition", "topic")
        .register()

    init {
        DefaultExports.initialize()
        consumer = createNewConsumer(environment)
        client = createAdminClient(environment)
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
            val topicAndPartition = consumerGroupOffsets.partitionsToOffsetAndMetadata().get()
            topicAndPartition.forEach { topic, offset ->
                val currentOffset = offset.offset()
                val lag = getLogEndOffset(topic) - currentOffset
                lagOffset.labels(group, topic.partition().toString(), topic.topic()).set(lag.toDouble())
                LOGGER.debug("Lag $lag for topic ${topic.topic()} for partition ${topic.partition()}, current offset is $currentOffset")
            }
        }
    }

    private fun createAdminClient(environment: Environment): AdminClient {
        val props = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, environment.bootstrapServersUrl)
        }
        return AdminClient.create(props)
    }

    private fun start() {
        LOGGER.info { "STARTING" }
        val timer = Timer("offsetChecker", true)
        val timerTask = timer.scheduleAtFixedRate(TimeUnit.SECONDS.toMillis(5), TimeUnit.SECONDS.toMillis(10)) {
            kafkaOffsetScraper()
        }
        Runtime.getRuntime().addShutdownHook(Thread {
            timerTask.cancel()
        })
        embeddedServer(Netty, httpPort) {
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