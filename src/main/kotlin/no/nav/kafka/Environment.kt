package no.nav.kafka

data class Environment(
    val bootstrapServersUrl: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
    val namespace: String = getEnvVar("PROMETHEUS_NAMESPACE"),
    val consumerGroups: String = getEnvVar("CONSUMER_GROUPS")
) {
    init {
        if (namespace.isBlank()) throw IllegalArgumentException("PROMETHEUS_NAMESPACE cannot be blank. ")
    }
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw IllegalArgumentException("Missing required variable \"$varName\"")
