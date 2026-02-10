package no.rutebanken.marduk.routes.file.onebusaway;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.onebusaway.gtfs.model.*;
import org.onebusaway.gtfs.services.GtfsMutableRelationalDao;
import org.onebusaway.gtfs_transformer.services.TransformContext;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FilterOneStopJourneyTest {

    private final FilterOneStopJourney filterOneStopJourney = new FilterOneStopJourney();

    @Test
    void runFilterOneStopJourneyTest() {
        GtfsMutableRelationalDao mockDao = Mockito.mock(GtfsMutableRelationalDao.class);
        TransformContext mockContext = Mockito.mock(TransformContext.class);

        Trip emptyTrip = new Trip();
        AgencyAndId id = new AgencyAndId();
        id.setId("empty");
        emptyTrip.setId(id);

        Trip oneStopTrip = new Trip();
        AgencyAndId idOneStop = new AgencyAndId();
        idOneStop.setId("oneStop");
        oneStopTrip.setId(idOneStop);

        Trip validTrip = new Trip();
        AgencyAndId validTripId = new AgencyAndId();
        validTripId.setId("valid");
        validTrip.setId(validTripId);

        List<Trip> trips = new ArrayList<>(3);
        trips.add(emptyTrip);
        trips.add(oneStopTrip);
        trips.add(validTrip);


        when(mockDao.getAllTrips()).thenReturn(trips);
        when(mockDao.getStopTimesForTrip(emptyTrip)).thenReturn(new ArrayList<>());

        StopTime stoptimeToRemove = new StopTime();
        List<StopTime> stopTime = new ArrayList<>(1);
        stopTime.add(stoptimeToRemove);
        when(mockDao.getStopTimesForTrip(oneStopTrip)).thenReturn(stopTime);

        Frequency frequencyToRemove = new Frequency();
        List<Frequency> frequencies = new ArrayList<>(1);
        frequencies.add(frequencyToRemove);
        when(mockDao.getFrequenciesForTrip(oneStopTrip)).thenReturn(frequencies);

        StopTime stopToKeep1 = new StopTime();
        StopTime stopToKeep2 = new StopTime();
        List<StopTime> stopTimeToKeep = new ArrayList<>(2);
        stopTimeToKeep.add(stopToKeep1);
        stopTimeToKeep.add(stopToKeep2);
        when(mockDao.getStopTimesForTrip(validTrip)).thenReturn(stopTimeToKeep);

        List<Transfer> transfers = new ArrayList<>(3);
        Transfer transferToKeep = new Transfer();
        transferToKeep.setFromTrip(validTrip);
        transferToKeep.setId(0);
        Transfer transferToRemove1 = new Transfer();
        transferToRemove1.setFromTrip(emptyTrip);
        transferToRemove1.setId(1);
        Transfer transferToRemove2 = new Transfer();
        transferToRemove2.setToTrip(oneStopTrip);
        transferToRemove2.setId(2);
        transfers.add(transferToRemove1);
        transfers.add(transferToRemove2);
        transfers.add(transferToKeep);
        when(mockDao.getAllTransfers()).thenReturn(transfers);

        filterOneStopJourney.run(mockContext, mockDao);

        verify(mockDao).getAllTrips();
        verify(mockDao).getStopTimesForTrip(emptyTrip);
        verify(mockDao).getStopTimesForTrip(oneStopTrip);
        verify(mockDao).getStopTimesForTrip(validTrip);
        verify(mockDao).removeEntity(emptyTrip);
        verify(mockDao).removeEntity(oneStopTrip);
        verify(mockDao).removeEntity(stoptimeToRemove);
        verify(mockDao).removeEntity(frequencyToRemove);
        verify(mockDao).removeEntity(transferToRemove1);
        verify(mockDao).removeEntity(transferToRemove2);
    }

}