package no.nav.kafka

import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.response.respondText
import io.ktor.response.respondTextWriter
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.hotspot.DefaultExports
import mu.KotlinLogging
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.consumer.ConsumerConfig
import java.util.Properties


private val LOGGER = KotlinLogging.logger {}

class ConsumerOffsetExporter {
    private val collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry

    init {
        DefaultExports.initialize()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val offsetExporter = ConsumerOffsetExporter()
            offsetExporter.start()
        }
    }


    private fun createAdminClient(kafkaHostname: String): AdminClient {
        val props = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaHostname)
        }


        return AdminClient.create(props)
    }
    private fun start() {

        val client = createAdminClient(kafkaHostname = "localhost:9092")


        //https://github.com/skalogs/kafka-lag-prometheus-exporter/blob/master/src/main/java/io/adopteunops/monitoring/kafka/exporter/KafkaExporter.java
       val  t =  client.listConsumerGroupOffsets("test")
        t.partitionsToOffsetAndMetadata().get().forEach { t, u ->

        }
        LOGGER.info { "STARTING" }
        embeddedServer(Netty, 8080) {
            routing {
                get("/") {
                    call.respondText("Hello, world!", ContentType.Text.Html)
                }
                get("/metrics") {
                    val names = call.request.queryParameters.getAll("name[]")?.toSet() ?: setOf()
                    call.respondTextWriter(ContentType.parse(TextFormat.CONTENT_TYPE_004)) {
                        TextFormat.write004(this, collectorRegistry.filteredMetricFamilySamples(names))
                    }
                }
            }
        }.start(wait = true)

    }
}