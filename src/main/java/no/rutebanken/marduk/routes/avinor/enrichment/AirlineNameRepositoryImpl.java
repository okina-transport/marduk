package no.rutebanken.marduk.routes.avinor.enrichment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AirlineNameRepositoryImpl {

    private final Logger logger = LoggerFactory.getLogger(AirlineNameRepositoryImpl.class);

    private Map<String, String> codeToNameMap = new HashMap<>();

    public void add(List<AirlineName> airlineNameList){
        if (airlineNameList.get(0) != null && airlineNameList.get(0).getCode() == null) {
            airlineNameList.remove(0);
        }
        airlineNameList.forEach(an -> codeToNameMap.putIfAbsent(an.getCode(), an.getName()));   //TODO should we overwrite?
        codeToNameMap.forEach((k, v) -> logger.debug(k + " -> " + v));
    }

    public String getName(String code) {
        return codeToNameMap.getOrDefault(code, "Unknown");
    }

}
