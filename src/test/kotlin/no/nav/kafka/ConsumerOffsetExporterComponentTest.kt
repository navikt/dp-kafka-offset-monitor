package no.nav.kafka

import io.prometheus.client.CollectorRegistry
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.Properties
import mu.KotlinLogging
import no.nav.common.JAASCredential
import no.nav.common.KafkaEnvironment
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ConsumerOffsetExporterComponentTest {

    companion object {
        private const val username = "srvkafkaclient"
        private const val password = "kafkaclient"
        val LOGGER = KotlinLogging.logger {}
        const val topicName = "test-topic-1"
        private val embeddedEnvironment = KafkaEnvironment(
            users = listOf(JAASCredential(username, password)),
            autoStart = false,
            withSchemaRegistry = false,
            withSecurity = true,
            topicInfos = listOf(KafkaEnvironment.TopicInfo(name = topicName, partitions = 1))
        )

        val env = Environment(
            bootstrapServersUrl = embeddedEnvironment.brokersURL,
            namespace = "dagpenger",
            consumerGroups = "test-group1",
            username = username,
            password = password
        )

        @BeforeAll
        @JvmStatic
        fun setup() {
            embeddedEnvironment.start()
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            embeddedEnvironment.tearDown()
        }
    }

    @Test
    fun test() {

        KafkaProducer<String, String>(producerProps()).use { p ->
            p.send(ProducerRecord(topicName, "one", "one")).get()
            p.send(ProducerRecord(topicName, "two", "two")).get()
            p.send(ProducerRecord(topicName, "three", "three")).get()
            p.send(ProducerRecord(topicName, "four", "four")).get()
            p.send(ProducerRecord(topicName, "five", "five")).get()
        }

        val consumer = KafkaConsumer<String, String>(consumerProps())
        consumer.subscribe(listOf(topicName))
        val records = consumer.poll(Duration.of(5, ChronoUnit.SECONDS)) // (max.poll.records i konfigurasjonen er satt til 1)
        records.forEach { LOGGER.info("$it") }
        consumer.commitSync()

        val offsetExporter = ConsumerOffsetExporter(env)
        offsetExporter.kafkaOffsetScraper()
        Thread.sleep(2000)
        val lag = CollectorRegistry.defaultRegistry.getSampleValue(
            "dagpenger_consumer_offset_lag",
            arrayOf("group_id", "partition", "topic"),
            arrayOf("test-group1", "0", topicName)
        )
        assertEquals(4.0, lag)
        consumer.close()
    }

    private fun consumerProps(): Properties {
        return Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, env.bootstrapServersUrl)
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true)
            put(ConsumerConfig.GROUP_ID_CONFIG, env.consumerGroups)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "1")
            put(ConsumerConfig.CLIENT_ID_CONFIG, "exporter-test-client")
            putAll(credentials())
        }
    }

    private fun producerProps(): Properties {
        return Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, env.bootstrapServersUrl)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
            put(ProducerConfig.ACKS_CONFIG, "1")
            putAll(credentials())
        }
    }

    private fun credentials(): Properties {
        return Properties().apply {
            put(SaslConfigs.SASL_MECHANISM, "PLAIN")
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT")
            put(
                SaslConfigs.SASL_JAAS_CONFIG,
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${username}\" password=\"${password}\";"
            )
        }
    }
}
