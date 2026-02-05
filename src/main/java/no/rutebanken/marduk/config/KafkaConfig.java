package no.rutebanken.marduk.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_EXPORT_STATUS = "th_export_status";
    public static final String TOPIC_EXPORT_TO_CONSUMER_STATUS = "th_export_to_consumer_status";

    private final boolean sendExportStatus;
    private final boolean sendExportToConsumerStatus;
    private final String client;
    private final String environment;
    private final String brokers;
    private final String clientId;

    public KafkaConfig(
            @Value("${marduk.kafka.sendExportStatus:false}") boolean sendExportStatus,
            @Value("${marduk.kafka.sendExportToConsumerStatus:false}") boolean sendExportToConsumerStatus,
            @Value("${client.name:}") String client,
            @Value("${marduk.kafka.env:}") String environment,
            @Value("${marduk.kafka.brokers:}") String brokers,
            @Value("${marduk.kafka.clentId:}") String clientId) {
        this.sendExportStatus = sendExportStatus;
        this.sendExportToConsumerStatus = sendExportToConsumerStatus;
        this.client = client;
        this.environment = environment;
        this.brokers = brokers;
        this.clientId = clientId;
        if (sendExportStatus || sendExportToConsumerStatus) {
            if (StringUtils.isBlank(brokers)) {
                throw new IllegalArgumentException("Kafka brokers must be configured");
            }
            if (StringUtils.isBlank(clientId)) {
                throw new IllegalArgumentException("Kafka clientId must be configured");
            }
            if (StringUtils.isBlank(environment)) {
                throw new IllegalArgumentException("Kafka environment must be configured");
            }
            if (StringUtils.isBlank(client)) {
                throw new IllegalArgumentException("Kafka client must be configured");
            }
        }
    }

    public String getEnvironment() {
        return environment;
    }

    public String getClient() {
        return client;
    }

    public boolean isSendExportStatus() {
        return sendExportStatus;
    }

    public boolean isSendExportToConsumerStatus() {
        return sendExportToConsumerStatus;
    }

    public String createCamelProducerConfig(String topicName) {
        String config = "kafka:" + topicName;
        config += "?brokers=" + brokers;
        config += "&clientId=" + clientId;
        return config;
    }
}
