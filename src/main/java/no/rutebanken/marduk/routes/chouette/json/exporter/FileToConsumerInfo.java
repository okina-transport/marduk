package no.rutebanken.marduk.routes.chouette.json.exporter;


public class FileToConsumerInfo {

    private final String type;
    private final String state;
    private final String consumerName;

    public FileToConsumerInfo(String type, String state, String consumerName) {
        this.type = type;
        this.state = state;
        this.consumerName = consumerName;
    }

    @Override
    public String toString() {
        return type + "," + state + "," + consumerName;
    }
}
