package no.rutebanken.marduk.routes.chouette.json;

public enum JobStatus {
    PROCESSING,
    FINISHED,
    FAILED;

    /**
     * Checks if the jobs is done/terminated
     * @return
     */
    public boolean isDone() {
        return this == FINISHED || this == FAILED;
    }
}
