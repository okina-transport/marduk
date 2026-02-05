package no.rutebanken.marduk.routes.kafka;

import no.rutebanken.marduk.config.KafkaConfig;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

import static no.rutebanken.marduk.config.KafkaConfig.TOPIC_EXPORT_STATUS;
import static no.rutebanken.marduk.config.KafkaConfig.TOPIC_EXPORT_TO_CONSUMER_STATUS;
import static no.rutebanken.marduk.routes.kafka.KafkaHeaders.CLIENT_HEADER;
import static no.rutebanken.marduk.routes.kafka.KafkaHeaders.ENV_HEADER;

@Component
public class KafkaRouteBuilder extends BaseRouteBuilder {

    public static final String ROUTE_SEND_EXPORT_STATUS_TO_KAFKA = "direct:send_export_status_to_kafka";
    public static final String ROUTE_SEND_EXPORT_STATUS_TO_CONSUMER_TO_KAFKA = "direct:send_export_status_to_consumer_to_kafka";

    private final KafkaConfig kafkaConfig;

    public KafkaRouteBuilder(KafkaConfig kafkaConfig) {
        this.kafkaConfig = kafkaConfig;
    }

    @Override
    public void configure() throws Exception {
        if (kafkaConfig.isSendExportStatus()) {
            from(ROUTE_SEND_EXPORT_STATUS_TO_KAFKA)
                    .marshal()
                    .json()
                    .log(LoggingLevel.INFO, "Sending export status to Kafka ${body}")
                    .removeHeaders("*")
                    .setHeader(CLIENT_HEADER, constant(kafkaConfig.getClient().getBytes(StandardCharsets.UTF_8)))
                    .setHeader(ENV_HEADER, constant(kafkaConfig.getEnvironment().getBytes(StandardCharsets.UTF_8)))
                    .to(kafkaConfig.createCamelProducerConfig(TOPIC_EXPORT_STATUS));
        } else {
            from(ROUTE_SEND_EXPORT_STATUS_TO_KAFKA)
                    .log(LoggingLevel.WARN, "Sending export status to Kafka is disabled")
                    .to("stub:nowhere") // does nothing but is required otherwise camel crash @ start-up
                    .end();
        }

        if (kafkaConfig.isSendExportToConsumerStatus()) {
            from(ROUTE_SEND_EXPORT_STATUS_TO_CONSUMER_TO_KAFKA)
                    .marshal()
                    .json()
                    .log(LoggingLevel.INFO,"Sending export to consumer status to Kafka ${body}")
                    .removeHeaders("*")
                    .setHeader(CLIENT_HEADER, constant(kafkaConfig.getClient().getBytes(StandardCharsets.UTF_8)))
                    .setHeader(ENV_HEADER, constant(kafkaConfig.getEnvironment().getBytes(StandardCharsets.UTF_8)))
                    .to(kafkaConfig.createCamelProducerConfig(TOPIC_EXPORT_TO_CONSUMER_STATUS));
        } else {
            from(ROUTE_SEND_EXPORT_STATUS_TO_CONSUMER_TO_KAFKA)
                    .log(LoggingLevel.WARN, "Sending export to consumer status to Kafka is disabled")
                    .to("stub:nowhere") // does nothing but is required otherwise camel crash @ start-up
                    .end();
        }
    }
}
