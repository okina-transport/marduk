package no.rutebanken.marduk.routes.file.onebusaway;

import org.apache.commons.collections4.CollectionUtils;
import org.onebusaway.gtfs.model.*;
import org.onebusaway.gtfs.services.GtfsMutableRelationalDao;
import org.onebusaway.gtfs_transformer.services.GtfsTransformStrategy;
import org.onebusaway.gtfs_transformer.services.TransformContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FilterOneStopJourney implements GtfsTransformStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(FilterOneStopJourney.class);

    @Override
    public String getName() {
        return "FilterOneStopJourney";
    }

    @Override
    public void run(TransformContext transformContext, GtfsMutableRelationalDao dao) {
        LOGGER.info("Start filtering one-stop vehicle journeys");
        Collection<Trip> allTrips = dao.getAllTrips();
        Set<Trip> tripToRemove = new HashSet<>();
        Set<StopTime> stoptimeToRemove = new HashSet<>();
        Set<Frequency> frequencyToRemove = new HashSet<>();
        if (CollectionUtils.isNotEmpty(allTrips)) {
            for (Trip trip : allTrips) {
                List<StopTime> stopTimesForTrip = dao.getStopTimesForTrip(trip);
                if (CollectionUtils.isEmpty(stopTimesForTrip) || stopTimesForTrip.size() == 1) {
                    tripToRemove.add(trip);
                    frequencyToRemove.addAll(dao.getFrequenciesForTrip(trip));
                    stoptimeToRemove.addAll(stopTimesForTrip);
                }
            }
        }
        for (Trip trip : tripToRemove) {
            LOGGER.info("Removing trip id {}", trip.getId().getId());
            dao.removeEntity(trip);
        }
        for (StopTime stopTime : stoptimeToRemove) {
            LOGGER.info("Removing orphan stop time {}", stopTime.getId());
            dao.removeEntity(stopTime);
        }
        for (Frequency frequency : frequencyToRemove) {
            LOGGER.info("Removing orphan frequency {}", frequency.getId());
            dao.removeEntity(frequency);
        }
        Collection<Transfer> allTransfers = dao.getAllTransfers();
        for (Transfer transfer : allTransfers) {
            if (tripToRemove.contains(transfer.getFromTrip()) || tripToRemove.contains(transfer.getToTrip())) {
                LOGGER.info("Removing orphan transfer {}", transfer.getId());
                dao.removeEntity(transfer);
            }
        }

        LOGGER.info("End one-stop vehicle journeys filtering");
    }

}
