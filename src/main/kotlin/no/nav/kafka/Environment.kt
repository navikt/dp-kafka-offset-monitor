package no.nav.kafka

data class Environment(
    val httpPort: Int? = null,
    val bootstrapServersUrl: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
    val namespace: String = getEnvVar("PROMETHEUS_NAMESPACE"),
    val consumerGroups: String = getEnvVar("CONSUMER_GROUPS")
) {
    init {
        if (namespace.isBlank()) throw IllegalArgumentException("Must specify a namespace for the application.")
    }
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
