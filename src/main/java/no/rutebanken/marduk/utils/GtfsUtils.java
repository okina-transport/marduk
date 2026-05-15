package no.rutebanken.marduk.utils;


import org.onebusaway.gtfs.impl.GtfsDaoImpl;
import org.onebusaway.gtfs.model.*;
import org.onebusaway.gtfs.serialization.GtfsReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;



public class GtfsUtils {

    private static final Logger logger = LoggerFactory.getLogger(GtfsUtils.class);

    /**
     * Parse GTFS flex entities from a GTFS ZIP file
     *
     * @param gtfsZip Gtfs archive
     * @return gtfsReader with entities read
     * @throws IOException when reading entities fail
     */
    public static GtfsReader readGtfsEntitiesFromGtfsZip(File gtfsZip) throws IOException {
        GtfsReader gtfsReader = new GtfsReader();
        gtfsReader.setInputLocation(gtfsZip);
        gtfsReader.setDefaultAgencyId("1");
        GtfsDaoImpl store = new GtfsDaoImpl();
        gtfsReader.setEntityStore(store);
        gtfsReader.run();
        return gtfsReader;
    }

    public static GtfsReader readGtfsFlexEntitiesFromGtfsZip(File gtfsZip) throws IOException {
        logger.info("Starting reading flex entities from file:{}", gtfsZip.getAbsoluteFile());
        return readGtfsEntitiesFromGtfsZip(gtfsZip);
    }

    public static GtfsReader readGtfsOfferEntitiesFromGtfsZip(File gtfsZip) throws IOException {
        logger.info("Starting reading offer entities from file:{}", gtfsZip.getAbsoluteFile());
        return readGtfsEntitiesFromGtfsZip(gtfsZip);
    }

}
