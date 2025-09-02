package no.rutebanken.marduk.routes.file;

import org.onebusaway.gtfs.model.*;
import org.onebusaway.gtfs.services.GtfsMutableRelationalDao;
import org.onebusaway.gtfs_transformer.services.GtfsTransformStrategy;
import org.onebusaway.gtfs_transformer.services.TransformContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class FilterByRouteIdsStrategy implements GtfsTransformStrategy {

    private static final Logger logger = LoggerFactory.getLogger(FilterByRouteIdsStrategy.class);
    private final Set<String> routeIdsToKeep;

    public FilterByRouteIdsStrategy(Set<String> routeIdsToKeep) {
        this.routeIdsToKeep = routeIdsToKeep;
    }

    @Override
    public String getName() {
        return "FilterByRouteIdsStrategy";
    }

    @Override
    public void run(TransformContext context, GtfsMutableRelationalDao dao) {
        logger.info("Start of GTFS filtering to retain only roads : {}", routeIdsToKeep);

        Set<AgencyAndId> keptTrips = removeUnwantedRoutesAndTrips(dao);
        removeUnwantedStopTimes(dao, keptTrips);
        removeUnwantedFrequencies(dao, keptTrips);
        removeUnwantedFareRules(dao);

        collectGarbage(dao);

        logger.info("GTFS filtering complete.");
    }

    private Set<AgencyAndId> removeUnwantedRoutesAndTrips(GtfsMutableRelationalDao dao) {
        Set<Trip> tripsToRemove = new HashSet<>();
        Set<AgencyAndId> keptTripIds = new HashSet<>();

        // Supprimer les routes qui ne sont pas dans la liste et collecter leurs trips
        removeEntities(dao, dao.getAllRoutes(), route -> {
            boolean keep = routeIdsToKeep.contains(route.getId().getId());
            if (!keep) {
                tripsToRemove.addAll(dao.getTripsForRoute(route));
            }
            return !keep;
        });

        // Parmi les trips restants, on ne garde que ceux qui appartiennent aux routes conservées.
        for (Trip trip : dao.getAllTrips()) {
            if (routeIdsToKeep.contains(trip.getRoute().getId().getId())) {
                keptTripIds.add(trip.getId());
            } else {
                tripsToRemove.add(trip);
            }
        }

        for (Trip trip : tripsToRemove) {
            dao.removeEntity(trip);
        }

        logger.info("{} saved roads, {} saved trips.", dao.getAllRoutes().size(), keptTripIds.size());
        return keptTripIds;
    }

    private void removeUnwantedStopTimes(GtfsMutableRelationalDao dao, Set<AgencyAndId> keptTrips) {
        removeEntities(dao, dao.getAllStopTimes(), st -> !keptTrips.contains(st.getTrip().getId()));
        logger.info("{} saved stop_times.", dao.getAllStopTimes().size());
    }

    private void removeUnwantedFrequencies(GtfsMutableRelationalDao dao, Set<AgencyAndId> keptTripIds) {
        removeEntities(dao, dao.getAllFrequencies(), freq -> !keptTripIds.contains(freq.getTrip().getId()));
        logger.info("{} frequencies saved.", dao.getAllFrequencies().size());
    }

    private void removeUnwantedFareRules(GtfsMutableRelationalDao dao) {
        removeEntities(dao, dao.getAllFareRules(), rule -> rule.getRoute() != null && !routeIdsToKeep.contains(rule.getRoute().getId().getId()));
        logger.info("{} fare_rules saved.", dao.getAllFareRules().size());
    }

    private void collectGarbage(GtfsMutableRelationalDao dao) {
        // Collecter les IDs de toutes les entités encore référencées
        Set<AgencyAndId> referencedStops = dao.getAllStopTimes().stream()
                .map(st -> st.getStop().getId())
                .collect(Collectors.toSet());

        Set<AgencyAndId> referencedServices = dao.getAllTrips().stream()
                .map(Trip::getServiceId)
                .collect(Collectors.toSet());

        Set<String> referencedAgencies = dao.getAllRoutes().stream()
                .map(route -> route.getAgency().getId())
                .collect(Collectors.toSet());

        Set<AgencyAndId> referencedShapes = dao.getAllTrips().stream()
                .map(Trip::getShapeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<AgencyAndId> referencedFares = dao.getAllFareRules().stream()
                .map(fr -> fr.getFare().getId())
                .collect(Collectors.toSet());

        // Supprimer les entités qui ne sont plus référencées
        removeEntities(dao, dao.getAllStops(), stop -> !referencedStops.contains(stop.getId()));
        logger.info("{} stops retained after cleaning.", dao.getAllStops().size());

        removeEntities(dao, dao.getAllShapePoints(), shapePoint -> !referencedShapes.contains(shapePoint.getShapeId()));
        logger.info("{} shapes retained after cleaning.", dao.getAllShapePoints().size());

        removeEntities(dao, dao.getAllCalendars(), cal -> !referencedServices.contains(cal.getServiceId()));
        logger.info("{} calendars retained after cleaning.", dao.getAllCalendars().size());

        removeEntities(dao, dao.getAllCalendarDates(), cd -> !referencedServices.contains(cd.getServiceId()));
        logger.info("{} calendar dates retained after cleaning.", dao.getAllCalendarDates().size());

        removeEntities(dao, dao.getAllFareAttributes(), fa -> !referencedFares.contains(fa.getId()));
        logger.info("{} fare_atttributes retained after cleaning.", dao.getAllFareAttributes().size());

        removeEntities(dao, dao.getAllTransfers(), t ->
                !referencedStops.contains(t.getFromStop().getId()) || !referencedStops.contains(t.getToStop().getId())
        );
        logger.info("{} transfers retained after cleaning.", dao.getAllTransfers().size());

        removeEntities(dao, dao.getAllPathways(), p ->
                !referencedStops.contains(p.getFromStop().getId()) || !referencedStops.contains(p.getToStop().getId())
        );
        logger.info("{} pathways retained after cleaning.", dao.getAllPathways().size());

        removeEntities(dao, dao.getAllAgencies(), agency -> !referencedAgencies.contains(agency.getId()));
        logger.info("{} agencies retained after cleaning.", dao.getAllAgencies().size());

        if (!dao.getAllRoutes().isEmpty() && dao.getAllAgencies().isEmpty()) {
            throw new IllegalStateException("All agencies have been eliminated, even though there are still roads.");
        }
    }

    private <K extends Serializable, T extends IdentityBean<K>> void removeEntities(
            GtfsMutableRelationalDao dao, Collection<T> entities, Predicate<T> condition) {

        // On doit créer une copie de la liste pour éviter une ConcurrentModificationException
        Set<T> entitiesToRemove = new HashSet<>();
        for (T entity : entities) {
            if (condition.test(entity)) {
                entitiesToRemove.add(entity);
            }
        }

        for (T entity : entitiesToRemove) {
            dao.removeEntity(entity);
        }
    }
}