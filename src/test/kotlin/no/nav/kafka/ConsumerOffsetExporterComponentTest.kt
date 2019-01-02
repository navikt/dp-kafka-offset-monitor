package no.nav.kafka

import no.nav.common.JAASCredential
import no.nav.common.KafkaEnvironment
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ConsumerOffsetExporterComponentTest {


    companion object {
        private const val username = "srvkafkaclient"
        private const val password = "kafkaclient"

        val embeddedEnvironment = KafkaEnvironment(
            users = listOf(JAASCredential(username, password)),
            autoStart = false,
            withSchemaRegistry = true,
            withSecurity = true,
            topics = listOf("test-topic1")
        )

        val env = Environment(
            bootstrapServersUrl = embeddedEnvironment.brokersURL,
            namespace = "dagpenger",
            consumerGroups = "test-group1"
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
        val offsetExporter = ConsumerOffsetExporter(env)
        offsetExporter.start()


        assertEquals("dd", "aa")
    }
}