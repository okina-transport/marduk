package no.rutebanken.marduk.Utils;

import no.rutebanken.marduk.domain.ExportType;
import no.rutebanken.marduk.metrics.PrometheusMetricsService;
import no.rutebanken.marduk.routes.chouette.json.Job;
import no.rutebanken.marduk.routes.chouette.json.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PollJobStatusRoute {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private PrometheusMetricsService metrics;

    public Boolean convertToBoolean(Object rawProperty){
        if (rawProperty instanceof Boolean){
            return (Boolean) rawProperty;
        }

        if (rawProperty instanceof String){
            return Boolean.parseBoolean((String)rawProperty);
        }
        logger.error("Unable to cast object to boolean:" + rawProperty);
        return null;
    }

    public void countEvent(Boolean isPOI, Boolean isParkings, Job job) {
        if (job == null || JobStatus.PROCESSING.equals(job.getStatus())){
            return;
        }

        ExportType exportType;
        if (isPOI) {
            exportType = ExportType.POI;
        } else if (isParkings) {
            exportType = ExportType.PARKING;
        } else {
            exportType = ExportType.ARRET;
        }
        metrics.countExports(exportType,JobStatus.FINISHED.equals(job.getStatus()) ? "OK" : job.getStatus().name());
    }
}
