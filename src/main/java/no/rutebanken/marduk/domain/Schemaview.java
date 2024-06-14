package no.rutebanken.marduk.domain;

import java.util.Date;

public class Schemaview {

    private Date startDate;
    private Date endDate;
    private long nbOfLines;
    private long nbOfQuays;
    private long nbOfStopPlaces;
    private long nbOfVehicleJourneys;
    private long nbOfCalendars;
    private long nbOfJourneyPatterns;

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public long getNbOfLines() {
        return nbOfLines;
    }

    public void setNbOfLines(long nbOfLines) {
        this.nbOfLines = nbOfLines;
    }

    public long getNbOfQuays() {
        return nbOfQuays;
    }

    public void setNbOfQuays(long nbOfQuays) {
        this.nbOfQuays = nbOfQuays;
    }

    public long getNbOfStopPlaces() {
        return nbOfStopPlaces;
    }

    public void setNbOfStopPlaces(long nbOfStopPlaces) {
        this.nbOfStopPlaces = nbOfStopPlaces;
    }

    public long getNbOfVehicleJourneys() {
        return nbOfVehicleJourneys;
    }

    public void setNbOfVehicleJourneys(long nbOfVehicleJourneys) {
        this.nbOfVehicleJourneys = nbOfVehicleJourneys;
    }

    public long getNbOfCalendars() {
        return nbOfCalendars;
    }

    public void setNbOfCalendars(long nbOfCalendars) {
        this.nbOfCalendars = nbOfCalendars;
    }

    public long getNbOfJourneyPatterns() {
        return nbOfJourneyPatterns;
    }

    public void setNbOfJourneyPatterns(long nbOfJourneyPatterns) {
        this.nbOfJourneyPatterns = nbOfJourneyPatterns;
    }
}
