package no.rutebanken.marduk.domain;

import java.util.Set;

public record ExportStatusDto(ExportType exportType, boolean success, Set<String> operators) {}
