package no.rutebanken.marduk.services.opendatasoft;

public class DatasetMetadata {

    private String value;
    private boolean override_remote_value = true;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public boolean isOverride_remote_value() {
        return override_remote_value;
    }

    public void setOverride_remote_value(boolean override_remote_value) {
        this.override_remote_value = override_remote_value;
    }
}
