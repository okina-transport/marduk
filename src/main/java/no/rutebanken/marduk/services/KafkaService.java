package no.rutebanken.marduk.services;

import no.rutebanken.marduk.domain.ExportStatusDto;
import no.rutebanken.marduk.domain.ExportToConsumerStatusDto;
import org.apache.camel.ProducerTemplate;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.routes.kafka.KafkaRouteBuilder.ROUTE_SEND_EXPORT_STATUS_TO_CONSUMER_TO_KAFKA;
import static no.rutebanken.marduk.routes.kafka.KafkaRouteBuilder.ROUTE_SEND_EXPORT_STATUS_TO_KAFKA;

@Component
public class KafkaService {

    private final ProducerTemplate producerTemplate;

    public KafkaService(ProducerTemplate producerTemplate) {
        this.producerTemplate = producerTemplate;
    }

    public void sendExportStatusToKafka(ExportStatusDto dto) {
        producerTemplate.sendBody(ROUTE_SEND_EXPORT_STATUS_TO_KAFKA, dto);
    }

    public void sendExportToConsumerStatusToKafka(ExportToConsumerStatusDto dto) {
        producerTemplate.sendBody(ROUTE_SEND_EXPORT_STATUS_TO_CONSUMER_TO_KAFKA, dto);
    }

}
