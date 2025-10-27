package no.rutebanken.marduk.routes.file.onebusaway;

import org.apache.commons.lang3.StringUtils;
import org.onebusaway.csv_entities.EntityHandler;
import org.onebusaway.gtfs.model.Stop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NonStandardStopTransformer implements EntityHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NonStandardStopTransformer.class);

    private static final double ONE_BUS_AWAY_DEFAULT_VALUE = -999;

    private final String defaultName;

    private final double defaultLatitude;

    private final double defaultLongitude;

    public NonStandardStopTransformer(String defaultName,
                                      double defaultLatitude,
                                      double defaultLongitude) {
        this.defaultName = defaultName;
        this.defaultLatitude = defaultLatitude;
        this.defaultLongitude = defaultLongitude;
    }

    @Override
    public void handleEntity(Object o) {
        if (o instanceof Stop stop) {
            if (StringUtils.isBlank(stop.getName())) {
                LOGGER.info("Found stop without name in GTFS input - filling with default value {}", defaultName);
                stop.setName(defaultName);
            }
            if (stop.getLat() == ONE_BUS_AWAY_DEFAULT_VALUE) {
                LOGGER.info("Found stop without latitude - filling with default value {}", defaultLatitude);
                stop.setLat(defaultLatitude);
            }
            if (stop.getLon() == ONE_BUS_AWAY_DEFAULT_VALUE) {
                LOGGER.info("Found stop without longitude - filling with default value {}", defaultLongitude);
                stop.setLon(defaultLongitude);
            }
        }
    }
}
