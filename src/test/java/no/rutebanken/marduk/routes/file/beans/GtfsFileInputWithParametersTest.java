package no.rutebanken.marduk.routes.file.beans;

import org.junit.Test;

import java.util.HashSet;

import static org.junit.Assert.*;

public class GtfsFileInputWithParametersTest {

    private static final int DEFAULT_VALUE = -999;
    private static final double DELTA = 0.1;

    @Test
    public void initFileWithoutParameters() {
        GtfsFileInputWithParameters test = new GtfsFileInputWithParameters(
                null,
                new HashSet<>(0),
                false,
                "",
                "");

        assertNotNull(test);
        assertFalse(test.isAllowNonStandardGtfs());
        assertEquals("", test.getFillMissingStopName());
        assertEquals(DEFAULT_VALUE, test.getDefaultLatitude(), DELTA);
        assertEquals(DEFAULT_VALUE, test.getDefaultLongitude(), DELTA);
    }

    @Test
    public void initFileWithWrongCoordinatesParameters() {
        GtfsFileInputWithParameters test = new GtfsFileInputWithParameters(
                null,
                new HashSet<>(0),
                true,
                "stop",
                "");

        assertNotNull(test);
        assertTrue(test.isAllowNonStandardGtfs());
        assertEquals("stop", test.getFillMissingStopName());
        assertEquals(DEFAULT_VALUE, test.getDefaultLatitude(), DELTA);
        assertEquals(DEFAULT_VALUE, test.getDefaultLongitude(), DELTA);
    }

    @Test
    public void initFileWithValidParameters() {
        GtfsFileInputWithParameters test = new GtfsFileInputWithParameters(
                null,
                new HashSet<>(0),
                true,
                "stop",
                "43.481402,-1.514699");

        assertNotNull(test);
        assertTrue(test.isAllowNonStandardGtfs());
        assertEquals("stop", test.getFillMissingStopName());
        assertEquals(43.481402, test.getDefaultLatitude(), DELTA);
        assertEquals(-1.514699, test.getDefaultLongitude(), DELTA);
    }

}