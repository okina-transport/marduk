package no.rutebanken.marduk.domain;

import java.util.Set;

public record ExportToConsumerStatusDto(ConsumerType consumerType, ExportType exportType, boolean success,
                                        Set<String> operators) {}
