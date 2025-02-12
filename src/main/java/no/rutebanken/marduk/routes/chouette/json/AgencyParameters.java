package no.rutebanken.marduk.routes.chouette.json;

public class AgencyParameters {

    private String agencyId;
    private String agencyName;
    private String agencyURL;
    private String agencyTimezone;

    public String getAgencyId() {
        return agencyId;
    }

    public void setAgencyId(String agencyId) {
        this.agencyId = agencyId;
    }

    public String getAgencyName() {
        return agencyName;
    }

    public void setAgencyName(String agencyName) {
        this.agencyName = agencyName;
    }

    public String getAgencyURL() {
        return agencyURL;
    }

    public void setAgencyURL(String agencyURL) {
        this.agencyURL = agencyURL;
    }

    public String getAgencyTimezone() {
        return agencyTimezone;
    }

    public void setAgencyTimezone(String agencyTimezone) {
        this.agencyTimezone = agencyTimezone;
    }
}
