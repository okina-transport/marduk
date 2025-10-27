package no.rutebanken.marduk.utils;

import no.rutebanken.marduk.domain.ExportType;
import no.rutebanken.marduk.metrics.PrometheusMetricsService;
import no.rutebanken.marduk.routes.chouette.json.Job;
import no.rutebanken.marduk.routes.chouette.json.JobStatus;
import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PollJobStatusRoute {

    private static final Logger logger = LoggerFactory.getLogger(PollJobStatusRoute.class);

    private final PrometheusMetricsService metrics;

    public PollJobStatusRoute(PrometheusMetricsService metrics) {
        this.metrics = metrics;
    }

    public Boolean convertToBoolean(Object rawProperty){
        if (rawProperty instanceof Boolean b){
            return b;
        }

        if (rawProperty instanceof String s){
            return Boolean.parseBoolean(s);
        }
        logger.error("Unable to cast object to boolean: {}", rawProperty);
        return null;
    }

    public void countEvent(Boolean isPOI, Boolean isParkings, Job job) {
        if (job == null || JobStatus.PROCESSING.equals(job.getStatus())){
            return;
        }

        ExportType exportType;
        if (BooleanUtils.toBoolean(isPOI)) {
            exportType = ExportType.POI;
        } else if (BooleanUtils.toBoolean(isParkings)) {
            exportType = ExportType.PARKING;
        } else {
            exportType = ExportType.ARRET;
        }
        metrics.countExports(exportType,JobStatus.FINISHED.equals(job.getStatus()) ? "OK" : job.getStatus().name());
    }
}
