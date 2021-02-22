package no.rutebanken.marduk.repository;

import no.rutebanken.marduk.domain.Consumer;
import no.rutebanken.marduk.domain.ExportTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConsumerDAO extends RestDAO<Consumer>{
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${consumers.api.url}")
    private String consumersUrl;


    public List<Consumer> getAllConsumersConcertoFromAdmin(String providerReferential) {
        return super.getEntities(this.consumersUrl, providerReferential, Consumer.class);
    }
}
