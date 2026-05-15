package no.rutebanken.marduk.services.processors;

import no.rutebanken.marduk.routes.file.ZipFileUtils;
import no.rutebanken.marduk.utils.FileUtils;
import no.rutebanken.marduk.utils.GtfsUtils;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.collections4.CollectionUtils;
import org.onebusaway.gtfs.model.*;
import org.onebusaway.gtfs.serialization.GtfsReader;
import org.onebusaway.gtfs.serialization.GtfsWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static no.rutebanken.marduk.Constants.*;

@Component
public class MergeOfferAndFlexInGTFSProcessor implements Processor {

    private static final Logger LOGGER = LoggerFactory.getLogger(MergeOfferAndFlexInGTFSProcessor.class);
    public static final String UTTU_EXPORT_DIR = "gtfsExports";

    private final String uttuStoragePath;
    private final String chouetteStoragePath;

    public MergeOfferAndFlexInGTFSProcessor(@Value("${uttu.storage.path:/srv/docker-data/data/uttu}") String uttuStoragePath,
                                            @Value("${chouette.storage.path:/srv/docker-data/data/chouette}") String chouetteStoragePath) {
        this.uttuStoragePath = uttuStoragePath;
        this.chouetteStoragePath = chouetteStoragePath;
    }


    @Override
    public void process(Exchange exchange) throws Exception {
        LOGGER.info("Starting merge of offer file and flex file");


        GtfsReader flexReader = getFlexReader(exchange);
        GtfsReader offerReader = getOfferReader(exchange);
        GtfsWriter writer = createWriter(exchange);

        Map<String, Agency> agencyMap = writeAgencies(offerReader, writer);
        copyFlexFilesToOutput(flexReader, writer);
        copyOfferFilesToOutput(offerReader, writer);
        mergeServiceCalendars(offerReader,flexReader, writer);
        mergeServiceCalendarDates(offerReader, flexReader, writer);
        mergeStops(offerReader, flexReader, writer);
        mergeRoutes(offerReader, flexReader, writer, agencyMap);
        mergeTrips(offerReader, flexReader, writer);
        mergeStopTimes(offerReader, flexReader, writer);
        writer.close();

        generateNewZip(exchange);


        LOGGER.info("Merge of offer file and flex file completed successfully");
    }

    private void generateNewZip(Exchange exchange) throws IOException {
        String chouetteJobId = exchange.getIn().getHeader(JOB_ID, String.class);
        String referential = exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class);
        String chouetteJobDirectory = chouetteStoragePath + "/" + referential + "/data/" + chouetteJobId;
        String offerFileName = exchange.getIn().getHeader(EXPORTED_FILENAME, String.class);
        Path offerFilePath = Path.of(chouetteJobDirectory, offerFileName);
        offerFilePath.toFile().delete();

        FileUtils.zipFilesInDirectory(chouetteJobDirectory, offerFileName);
        ZipFileUtils.deleteFilesByExtension(chouetteJobDirectory, ".txt");
        ZipFileUtils.deleteFilesByExtension(chouetteJobDirectory, ".geojson");

    }

    private void mergeStopTimes(GtfsReader offerReader, GtfsReader flexReader, GtfsWriter writer) {
        Collection<StopTime> stopTimesFromOffer = offerReader.getEntityStore().getAllEntitiesForType(StopTime.class);
        Set<String> alreadySeenStopTimes = new HashSet<>();
        if (CollectionUtils.isNotEmpty(stopTimesFromOffer)) {
            for (StopTime stopTime : stopTimesFromOffer) {
                alreadySeenStopTimes.add(stopTime.getTrip().getId().getId());
                writer.handleEntity(stopTime);
            }
        }

        Collection<StopTime> stopTimesFromFlex = flexReader.getEntityStore().getAllEntitiesForType(StopTime.class);
        if (CollectionUtils.isNotEmpty(stopTimesFromFlex)) {
            stopTimesFromFlex.stream()
                    .filter(stopTime -> !alreadySeenStopTimes.contains(stopTime.getTrip().getId().getId()))
                    .forEach(writer::handleEntity);
        }
    }

    private void mergeTrips(GtfsReader offerReader, GtfsReader flexReader, GtfsWriter writer) {
        Collection<Trip> tripsFromOffer = offerReader.getEntityStore().getAllEntitiesForType(Trip.class);
        Set<String> alreadySeenTrips = new HashSet<>();
        if (CollectionUtils.isNotEmpty(tripsFromOffer)) {
            for (Trip trip : tripsFromOffer) {
                alreadySeenTrips.add(trip.getId().getId());
                writer.handleEntity(trip);
            }
        }

        Collection<Trip> tripsFromFlex = flexReader.getEntityStore().getAllEntitiesForType(Trip.class);
        if (CollectionUtils.isNotEmpty(tripsFromFlex)) {
            tripsFromFlex.stream()
                    .filter(cal -> !alreadySeenTrips.contains(cal.getId().getId()))
                    .forEach(writer::handleEntity);
        }
    }

    private void mergeRoutes(GtfsReader offerReader, GtfsReader flexReader, GtfsWriter writer, Map<String, Agency> agencyMap) {
        Collection<Route> routesFromOffer = offerReader.getEntityStore().getAllEntitiesForType(Route.class);
        Set<String> alreadySeenRoutes = new HashSet<>();
        if (CollectionUtils.isNotEmpty(routesFromOffer)) {
            for (Route route : routesFromOffer) {
                alreadySeenRoutes.add(route.getId().getId());
                writer.handleEntity(route);
            }
        }

        Collection<Route> routesFromFlex = flexReader.getEntityStore().getAllEntitiesForType(Route.class);
        if (CollectionUtils.isNotEmpty(routesFromFlex)) {

            for (Route routeFromFlex : routesFromFlex) {
                if (alreadySeenRoutes.contains(routeFromFlex.getId().getId())){
                    continue;
                }
                String agencyName = routeFromFlex.getAgency().getName();
                if (agencyMap.containsKey(agencyName)){
                    routeFromFlex.setAgency(agencyMap.get(agencyName));
                }

                writer.handleEntity(routeFromFlex);
            }
        }

    }

    private void mergeStops(GtfsReader offerReader, GtfsReader flexReader, GtfsWriter writer) {
        Collection<Stop> stopsFromOffer = offerReader.getEntityStore().getAllEntitiesForType(Stop.class);
        Set<String> alreadySeenStops = new HashSet<>();
        if (CollectionUtils.isNotEmpty(stopsFromOffer)) {
            for (Stop stop : stopsFromOffer) {
                alreadySeenStops.add(stop.getId().getId());
                writer.handleEntity(stop);
            }
        }

        Collection<Stop> stopsFromFlex = flexReader.getEntityStore().getAllEntitiesForType(Stop.class);
        if (CollectionUtils.isNotEmpty(stopsFromFlex)) {
            stopsFromFlex.stream()
                    .filter(cal -> !alreadySeenStops.contains(cal.getId().getId()))
                    .forEach(writer::handleEntity);
        }
    }

    private void mergeServiceCalendarDates(GtfsReader offerReader, GtfsReader flexReader, GtfsWriter writer) {
        Collection<ServiceCalendarDate> serviceCalendarsDatesFromOffer = offerReader.getEntityStore().getAllEntitiesForType(ServiceCalendarDate.class);
        Set<String> alreadySeenCalendarsDates = new HashSet<>();
        if (CollectionUtils.isNotEmpty(serviceCalendarsDatesFromOffer)) {
            for (ServiceCalendarDate serviceCalendarDate : serviceCalendarsDatesFromOffer) {
                alreadySeenCalendarsDates.add(serviceCalendarDate.getServiceId().getId());
                writer.handleEntity(serviceCalendarDate);
            }
        }

        Collection<ServiceCalendarDate> serviceCalendarDatesFromFlex = flexReader.getEntityStore().getAllEntitiesForType(ServiceCalendarDate.class);
        if (CollectionUtils.isNotEmpty(serviceCalendarDatesFromFlex)) {
            serviceCalendarDatesFromFlex.stream()
                    .filter(cal -> !alreadySeenCalendarsDates.contains(cal.getServiceId().getId()))
                    .forEach(writer::handleEntity);
        }

    }

    private void mergeServiceCalendars(GtfsReader offerReader, GtfsReader flexReader, GtfsWriter writer) {
        Collection<ServiceCalendar> serviceCalendarsFromOffer = offerReader.getEntityStore().getAllEntitiesForType(ServiceCalendar.class);
        Set<String> alreadySeenCalendars = new HashSet<>();
        if (CollectionUtils.isNotEmpty(serviceCalendarsFromOffer)) {
            for (ServiceCalendar serviceCalendar : serviceCalendarsFromOffer) {
                alreadySeenCalendars.add(serviceCalendar.getServiceId().getId());
                writer.handleEntity(serviceCalendar);
            }
        }

        Collection<ServiceCalendar> serviceCalendarsFromFlex = flexReader.getEntityStore().getAllEntitiesForType(ServiceCalendar.class);
        if (CollectionUtils.isNotEmpty(serviceCalendarsFromFlex)) {
            serviceCalendarsFromFlex.stream()
                                    .filter(cal -> !alreadySeenCalendars.contains(cal.getServiceId().getId()))
                                    .forEach(writer::handleEntity);
        }
    }

    /**
     * Copy files ONLY existing in offer (network, shapes, fare files, etc) to output
     * @param offerReader
     *  GtfsReader that read offer file
     * @param writer
     *  output file writer
     */
    private void copyOfferFilesToOutput(GtfsReader offerReader, GtfsWriter writer) {
        Collection<Network> networks = offerReader.getEntityStore().getAllEntitiesForType(Network.class);
        if (CollectionUtils.isNotEmpty(networks)) {
            networks.forEach(writer::handleEntity);
        }

        Collection<ShapePoint> shapes = offerReader.getEntityStore().getAllEntitiesForType(ShapePoint.class);
        if (CollectionUtils.isNotEmpty(shapes)) {
            shapes.forEach(writer::handleEntity);
        }

        Collection<Transfer> transfers = offerReader.getEntityStore().getAllEntitiesForType(Transfer.class);
        if (CollectionUtils.isNotEmpty(transfers)) {
            transfers.forEach(writer::handleEntity);
        }

        Collection<FeedInfo> feedInfos = offerReader.getEntityStore().getAllEntitiesForType(FeedInfo.class);
        if (CollectionUtils.isNotEmpty(feedInfos)) {
            feedInfos.forEach(writer::handleEntity);
        }

        Collection<FareAttribute> fareAttributes = offerReader.getEntityStore().getAllEntitiesForType(FareAttribute.class);
        if (CollectionUtils.isNotEmpty(fareAttributes)) {
            fareAttributes.forEach(writer::handleEntity);
        }

        Collection<FareLegRule> fareLegRules = offerReader.getEntityStore().getAllEntitiesForType(FareLegRule.class);
        if (CollectionUtils.isNotEmpty(fareLegRules)) {
            fareLegRules.forEach(writer::handleEntity);
        }

        Collection<FareProduct> fareProducts = offerReader.getEntityStore().getAllEntitiesForType(FareProduct.class);
        if (CollectionUtils.isNotEmpty(fareProducts)) {
            fareProducts.forEach(writer::handleEntity);
        }

        Collection<FareMedium> fareMediums = offerReader.getEntityStore().getAllEntitiesForType(FareMedium.class);
        if (CollectionUtils.isNotEmpty(fareMediums)) {
            fareMediums.forEach(writer::handleEntity);
        }

        Collection<RiderCategory> riderCategories = offerReader.getEntityStore().getAllEntitiesForType(RiderCategory.class);
        if (CollectionUtils.isNotEmpty(riderCategories)) {
            riderCategories.forEach(writer::handleEntity);
        }
    }

    /**
     * Copy files ONLY existing in flex (location, booKingRule, etc) to output
     * @param flexReader
     *  GtfsReader that read flex file
     * @param writer
     *  output file writer
     */
    private void copyFlexFilesToOutput(GtfsReader flexReader, GtfsWriter writer) {

        Collection<LocationGroupElement> locationGroupsElements = flexReader.getEntityStore().getAllEntitiesForType(LocationGroupElement.class);
        if (CollectionUtils.isNotEmpty(locationGroupsElements)) {
            locationGroupsElements.forEach(writer::handleEntity);
        }

        Collection<LocationGroup> locationGroups = flexReader.getEntityStore().getAllEntitiesForType(LocationGroup.class);
        if (CollectionUtils.isNotEmpty(locationGroups)) {
            locationGroups.forEach(writer::handleEntity);
        }

        Collection<Location> locations = flexReader.getEntityStore().getAllEntitiesForType(Location.class);
        if (CollectionUtils.isNotEmpty(locations)) {
            locations.forEach(writer::handleEntity);
        }

        Collection<BookingRule> bookingRules = flexReader.getEntityStore().getAllEntitiesForType(BookingRule.class);
        if (CollectionUtils.isNotEmpty(bookingRules)) {
            bookingRules.forEach(writer::handleEntity);
        }
    }

    private Map<String, Agency> writeAgencies(GtfsReader offerReader, GtfsWriter writer) {
        Map<String, Agency> agencyMap = new HashMap<>();
        Collection<Agency> agencies = offerReader.getEntityStore().getAllEntitiesForType(Agency.class);
        if (CollectionUtils.isNotEmpty(agencies)) {
            for (Agency agency : agencies) {
                agencyMap.put(agency.getName(), agency);
                writer.handleEntity(agency);
            }
        }
        return agencyMap;
    }

    private GtfsWriter createWriter(Exchange exchange) {
        GtfsWriter writer = new GtfsWriter();
        String chouetteJobId = exchange.getIn().getHeader(JOB_ID, String.class);
        String referential = exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class);
        String chouetteJobDirectory = chouetteStoragePath + "/" + referential + "/data/" + chouetteJobId;
        Path jobPath = Path.of(chouetteJobDirectory);
        File outputDir = new File(jobPath.toUri());
        outputDir.getParentFile().mkdirs();
        writer.setOutputLocation(outputDir);
        return writer;
    }

    private GtfsReader getFlexReader(Exchange exchange) throws IOException {
        String uttuJobId = exchange.getIn().getHeader(UTTU_JOB_ID, String.class);
        String flexFileName = uttuJobId + ".zip";
        Path source = Path.of(uttuStoragePath, UTTU_EXPORT_DIR, uttuJobId, flexFileName);
        return GtfsUtils.readGtfsFlexEntitiesFromGtfsZip(source.toFile());
    }

    private GtfsReader getOfferReader(Exchange exchange) throws IOException {
        String chouetteJobId = exchange.getIn().getHeader(JOB_ID, String.class);
        String referential = exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class);
        String chouetteJobDirectory = chouetteStoragePath + "/" + referential + "/data/" + chouetteJobId;
        String offerFileName = exchange.getIn().getHeader(EXPORTED_FILENAME, String.class);
        Path source = Path.of(chouetteJobDirectory, offerFileName);
        return GtfsUtils.readGtfsOfferEntitiesFromGtfsZip(source.toFile());
    }

}
