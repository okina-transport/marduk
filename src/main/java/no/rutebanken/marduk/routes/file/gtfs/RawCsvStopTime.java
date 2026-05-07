package no.rutebanken.marduk.routes.file.gtfs;

public class RawCsvStopTime {
    private String tripId;
    private String arrivalTime;
    private String departureTime;
    private String stopId;
    private String locationGroupId;
    private String locationId;
    private String stopSequence;
    private String stopHeadsign;
    private String pickupType;
    private String dropOffType;
    private String continuousPickup;
    private String continuousDropOff;
    private String shapeDistTraveled;
    private String timepoint;
    private String startPickupDropOffWindow;
    private String endPickupDropOffWindow;
    private String meanDurationFactor;
    private String meanDurationOffset;
    private String pickupBookingRuleId;
    private String dropOffBookingRuleId;

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public String getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(String arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(String departureTime) {
        this.departureTime = departureTime;
    }

    public String getStopId() {
        return stopId;
    }

    public void setStopId(String stopId) {
        this.stopId = stopId;
    }

    public String getLocationGroupId() {
        return locationGroupId;
    }

    public void setLocationGroupId(String locationGroupId) {
        this.locationGroupId = locationGroupId;
    }

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    public String getStopSequence() {
        return stopSequence;
    }

    public void setStopSequence(String stopSequence) {
        this.stopSequence = stopSequence;
    }

    public String getStopHeadsign() {
        return stopHeadsign;
    }

    public void setStopHeadsign(String stopHeadsign) {
        this.stopHeadsign = stopHeadsign;
    }

    public String getPickupType() {
        return pickupType;
    }

    public void setPickupType(String pickupType) {
        this.pickupType = pickupType;
    }

    public String getDropOffType() {
        return dropOffType;
    }

    public void setDropOffType(String dropOffType) {
        this.dropOffType = dropOffType;
    }

    public String getContinuousPickup() {
        return continuousPickup;
    }

    public void setContinuousPickup(String continuousPickup) {
        this.continuousPickup = continuousPickup;
    }

    public String getContinuousDropOff() {
        return continuousDropOff;
    }

    public void setContinuousDropOff(String continuousDropOff) {
        this.continuousDropOff = continuousDropOff;
    }

    public String getShapeDistTraveled() {
        return shapeDistTraveled;
    }

    public void setShapeDistTraveled(String shapeDistTraveled) {
        this.shapeDistTraveled = shapeDistTraveled;
    }

    public String getTimepoint() {
        return timepoint;
    }

    public void setTimepoint(String timepoint) {
        this.timepoint = timepoint;
    }

    public String getStartPickupDropOffWindow() {
        return startPickupDropOffWindow;
    }

    public void setStartPickupDropOffWindow(String startPickupDropOffWindow) {
        this.startPickupDropOffWindow = startPickupDropOffWindow;
    }

    public String getEndPickupDropOffWindow() {
        return endPickupDropOffWindow;
    }

    public void setEndPickupDropOffWindow(String endPickupDropOffWindow) {
        this.endPickupDropOffWindow = endPickupDropOffWindow;
    }

    public String getMeanDurationFactor() {
        return meanDurationFactor;
    }

    public void setMeanDurationFactor(String meanDurationFactor) {
        this.meanDurationFactor = meanDurationFactor;
    }

    public String getMeanDurationOffset() {
        return meanDurationOffset;
    }

    public void setMeanDurationOffset(String meanDurationOffset) {
        this.meanDurationOffset = meanDurationOffset;
    }

    public String getPickupBookingRuleId() {
        return pickupBookingRuleId;
    }

    public void setPickupBookingRuleId(String pickupBookingRuleId) {
        this.pickupBookingRuleId = pickupBookingRuleId;
    }

    public String getDropOffBookingRuleId() {
        return dropOffBookingRuleId;
    }

    public void setDropOffBookingRuleId(String dropOffBookingRuleId) {
        this.dropOffBookingRuleId = dropOffBookingRuleId;
    }
}
