package no.rutebanken.marduk.routes.file.beans;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Set;
import java.util.regex.Pattern;

public class GtfsFileInputWithParameters {

    private static final Logger LOGGER = LoggerFactory.getLogger(GtfsFileInputWithParameters.class);

    private static final Pattern COORDINATES_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?$");

    private static final double DEFAULT_VALUE = -999.0d;

    private final File inputFile;

    private final Set<String> routeIds;

    private final boolean allowNonStandardGtfs;

    private final String fillMissingStopName;

    private final double defaultLongitude;

    private final double defaultLatitude;


    public GtfsFileInputWithParameters(File inputFile, Set<String> routeIds, boolean allowNonStandardGtfs, String fillMissingStopName, String fillMissingCoordinates) {
        this.inputFile = inputFile;
        this.allowNonStandardGtfs = allowNonStandardGtfs;
        this.fillMissingStopName = fillMissingStopName;
        this.routeIds = routeIds;
        double defaultLatitudeValue = DEFAULT_VALUE;
        double defaultLongitudeValue = DEFAULT_VALUE;
        if (allowNonStandardGtfs
                && StringUtils.isNotBlank(fillMissingCoordinates)
                && COORDINATES_PATTERN.matcher(fillMissingCoordinates).matches()) {
            String[] parts = fillMissingCoordinates.split(",");
            try {
                defaultLatitudeValue = Double.parseDouble(parts[0].trim());
                defaultLongitudeValue = Double.parseDouble(parts[1].trim());
            } catch (NumberFormatException e) {
                LOGGER.error("Invalid input {}", fillMissingCoordinates);
            }
        }
        this.defaultLatitude = defaultLatitudeValue;
        this.defaultLongitude = defaultLongitudeValue;
    }

    public File getInputFile() {
        return inputFile;
    }

    public Set<String> getRouteIds() {
        return routeIds;
    }

    public boolean isAllowNonStandardGtfs() {
        return allowNonStandardGtfs;
    }

    public String getFillMissingStopName() {
        return fillMissingStopName;
    }

    public double getDefaultLongitude() {
        return defaultLongitude;
    }

    public double getDefaultLatitude() {
        return defaultLatitude;
    }
}
