package no.rutebanken.marduk.routes.file.gtfs;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@Component
public class StopTimesParser {

    private static final Object[] GTFS_STOP_TIMES_HEADERS = {"trip_id", "arrival_time", "departure_time", "stop_id",
            "location_group_id", "location_id", "stop_sequence",
            "stop_headsign", "pickup_type", "drop_off_type",
            "continuous_pickup", "continuous_drop_off",
            "shape_dist_traveled", "timepoint",
            "start_pickup_drop_off_window", "end_pickup_drop_off_window",
            "mean_duration_factor", "mean_duration_offset",
            "pickup_booking_rule_id", "drop_off_booking_rule_id"};

    public static final Predicate<RawCsvStopTime> IS_FLEX =
            (stopTime -> {
                if (StringUtils.isNotBlank(stopTime.getPickupBookingRuleId()) || StringUtils.isNotBlank(stopTime.getDropOffBookingRuleId())) {
                    return true;
                }

                if ("2".equals(stopTime.getContinuousPickup()) || "3".equals(stopTime.getContinuousPickup())) {
                    return true;
                }

                if ("2".equals(stopTime.getContinuousDropOff()) || "3".equals(stopTime.getContinuousDropOff())) {
                    return true;
                }

                if (StringUtils.isNotBlank(stopTime.getLocationGroupId())) {
                    return true;
                }

                return StringUtils.isNotBlank(stopTime.getLocationId());
            });

    public void cleanStopTimesFile(File gtfsZip) throws IOException {
        Path zipFile = Paths.get(gtfsZip.getAbsolutePath());
        Map<String, String> env = Map.of("create", "false");

        try (FileSystem zipFs = FileSystems.newFileSystem(zipFile, env)) {
            Path fileInZip = zipFs.getPath("stop_times.txt");

            try (InputStream is = Files.newInputStream(fileInZip)) {
                Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                CSVFormat format = CSVFormat.RFC4180.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setAllowMissingColumnNames(true)
                        .setIgnoreEmptyLines(true)
                        .build();

                List<RawCsvStopTime> nonFlexStops = new ArrayList<>();
                try (org.apache.commons.csv.CSVParser parser = format.parse(reader)) {
                    for (CSVRecord stopTimeLine : parser) {
                        RawCsvStopTime stopTime = mapRecordToStopTime(stopTimeLine);
                        if (!IS_FLEX.test(stopTime)) {
                            nonFlexStops.add(stopTime);
                        }
                    }
                }
                writeStopTimes(fileInZip, nonFlexStops);
            }

        }
    }

    public void writeStopTimes(Path filePath, List<RawCsvStopTime> stopTimes)
            throws IOException {

        try (Writer writer = Files.newBufferedWriter(filePath);
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.RFC4180)) {

            csvPrinter.printRecord(GTFS_STOP_TIMES_HEADERS);

            for (RawCsvStopTime stopTime : stopTimes) {
                csvPrinter.printRecord(
                        stopTime.getTripId(),
                        stopTime.getArrivalTime(),
                        stopTime.getDepartureTime(),
                        stopTime.getStopId(),
                        stopTime.getLocationGroupId(),
                        stopTime.getLocationId(),
                        stopTime.getStopSequence(),
                        stopTime.getStopHeadsign(),
                        stopTime.getPickupType(),
                        stopTime.getDropOffType(),
                        stopTime.getContinuousPickup(),
                        stopTime.getContinuousDropOff(),
                        stopTime.getShapeDistTraveled(),
                        stopTime.getTimepoint(),
                        stopTime.getStartPickupDropOffWindow(),
                        stopTime.getEndPickupDropOffWindow(),
                        stopTime.getMeanDurationFactor(),
                        stopTime.getMeanDurationOffset(),
                        stopTime.getPickupBookingRuleId(),
                        stopTime.getDropOffBookingRuleId()
                );
            }

            csvPrinter.flush();
        }
    }

    private RawCsvStopTime mapRecordToStopTime(CSVRecord csvRecord) {
        RawCsvStopTime stopTime = new RawCsvStopTime();

        stopTime.setTripId(getStringValue(csvRecord, "trip_id"));
        stopTime.setArrivalTime(getStringValue(csvRecord, "arrival_time"));
        stopTime.setDepartureTime(getStringValue(csvRecord, "departure_time"));
        stopTime.setStopId(getStringValue(csvRecord, "stop_id"));
        stopTime.setStopSequence(getStringValue(csvRecord, "stop_sequence"));
        stopTime.setStopHeadsign(getStringValue(csvRecord, "stop_headsign"));
        stopTime.setPickupType(getStringValue(csvRecord, "pickup_type"));
        stopTime.setDropOffType(getStringValue(csvRecord, "drop_off_type"));
        stopTime.setContinuousPickup(getStringValue(csvRecord, "continuous_pickup"));
        stopTime.setContinuousDropOff(getStringValue(csvRecord, "continuous_drop_off"));
        stopTime.setShapeDistTraveled(getStringValue(csvRecord, "shape_dist_traveled"));
        stopTime.setTimepoint(getStringValue(csvRecord, "timepoint"));
        stopTime.setStartPickupDropOffWindow(getStringValue(csvRecord, "start_pickup_drop_off_window"));
        stopTime.setEndPickupDropOffWindow(getStringValue(csvRecord, "end_pickup_drop_off_window"));
        stopTime.setMeanDurationFactor(getStringValue(csvRecord, "mean_duration_factor"));
        stopTime.setMeanDurationOffset(getStringValue(csvRecord, "mean_duration_offset"));
        stopTime.setPickupBookingRuleId(getStringValue(csvRecord, "pickup_booking_rule_id"));
        stopTime.setDropOffBookingRuleId(getStringValue(csvRecord, "drop_off_booking_rule_id"));

        return stopTime;
    }

    private String getStringValue(CSVRecord csvRecord, String columnName) {
        try {
            String value = csvRecord.get(columnName);
            return StringUtils.defaultIfEmpty(value, "");
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

}
