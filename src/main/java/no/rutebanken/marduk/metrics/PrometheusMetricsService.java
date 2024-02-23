package no.rutebanken.marduk.metrics;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.Tag;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exemplars.ExemplarSampler;
import no.rutebanken.marduk.domain.ConsumerType;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

@Component
public class PrometheusMetricsService extends PrometheusMeterRegistry {

    private static final String METRICS_PREFIX = "app.marduk.";

    private static final String CONSUMER_CALLS_TOTAL = METRICS_PREFIX + "consumer.calls.total";

    private static final String CONSUMER_TAG_NAME = "consumerType";

    private static final String RESULT_TAG = "result";


    public PrometheusMetricsService() {
        super(PrometheusConfig.DEFAULT);
    }

    @PreDestroy
    public void shutdown() {
        this.close();
    }

    public void countConsumerCalls(ConsumerType consumerType, String result) {

        List<Tag> counterTags = new ArrayList<>();
        counterTags.add(new ImmutableTag(CONSUMER_TAG_NAME, consumerType.name()));
        counterTags.add(new ImmutableTag(RESULT_TAG, result));

        counter(CONSUMER_CALLS_TOTAL,   counterTags).increment();
    }



}
