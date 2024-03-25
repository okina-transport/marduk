package no.rutebanken.marduk.metrics;

import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.Tag;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import no.rutebanken.marduk.domain.ConsumerType;
import no.rutebanken.marduk.domain.ExportType;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

@Component
public class PrometheusMetricsService extends PrometheusMeterRegistry {

    private static final String METRICS_PREFIX = "app.marduk.";

    private static final String CONSUMER_CALLS_TOTAL = METRICS_PREFIX + "consumer.calls.total";

    private static final String EXPORTS_TOTAL = METRICS_PREFIX + "exports.total";

    private static final String STARTUP_TIME = METRICS_PREFIX + "startup.time";

    private static final String CONSUMER_TAG_NAME = "consumerType";

    private static final String RESULT_TAG = "result";

    private static final String EXPORT_TYPE_TAG_NAME = "exportType";


    public PrometheusMetricsService() {
        super(PrometheusConfig.DEFAULT);
        counter(STARTUP_TIME).increment(System.currentTimeMillis() /1000);
    }

    @PreDestroy
    public void shutdown() {
        this.close();
    }

    public void countConsumerCalls(ConsumerType consumerType, ExportType type, String result) {

        List<Tag> counterTags = new ArrayList<>();
        counterTags.add(new ImmutableTag(CONSUMER_TAG_NAME, consumerType.name()));
        counterTags.add(new ImmutableTag(EXPORT_TYPE_TAG_NAME, type.name()));
        counterTags.add(new ImmutableTag(RESULT_TAG, result));
        counter(CONSUMER_CALLS_TOTAL, counterTags).increment();
    }

    public void countExports(ExportType type, String result) {

        List<Tag> counterTags = new ArrayList<>();
        counterTags.add(new ImmutableTag(EXPORT_TYPE_TAG_NAME, type.name()));
        counterTags.add(new ImmutableTag(RESULT_TAG, result));
        counter(EXPORTS_TOTAL, counterTags).increment();
    }



}
