package no.rutebanken.marduk.routes.file.onebusaway;

import org.junit.Test;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Stop;

import static org.junit.Assert.*;

public class NonStandardStopTransformerTest {

    private static final String DEFAULT_NAME = "DEFAULT_NAME";
    private static final double DEFAULT_LATITUDE = 43.481402;
    private static final double DEFAULT_LONGITUDE = -1.514699;
    private static final double DELTA = 0.001;

    private final NonStandardStopTransformer service =
            new NonStandardStopTransformer(DEFAULT_NAME, DEFAULT_LATITUDE, DEFAULT_LONGITUDE);

    @Test
    public void shouldSetDefaultValueForStopIfNotSetTest() {
        Stop stop = new Stop();

        service.handleEntity(stop);

        assertEquals(DEFAULT_NAME, stop.getName());
        assertEquals(DEFAULT_LATITUDE, stop.getLat(), DELTA);
        assertEquals(DEFAULT_LONGITUDE, stop.getLon(), DELTA);
    }

    @Test
    public void shouldNotSetDefaultValueForStopIfValueExistsTest() {
        Stop stop = new Stop();
        stop.setName("NAME");
        stop.setLat(48);
        stop.setLon(-1);

        service.handleEntity(stop);

        assertNotEquals(DEFAULT_NAME, stop.getName());
        assertNotEquals(DEFAULT_LATITUDE, stop.getLat(), DELTA);
        assertNotEquals(DEFAULT_LONGITUDE, stop.getLon(), DELTA);
    }

    @Test
    public void shouldNotMakeAnyTransformationForOtherTypesTest() {
        Route route = new Route();

        service.handleEntity(route);

        assertNull(route.getShortName());
        assertNull(route.getLongName());
    }
}