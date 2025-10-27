package no.rutebanken.marduk.utils.constants;

public class EndpointDeclarationConstants {

    private EndpointDeclarationConstants() {
        throw new IllegalArgumentException();
    }

    public static final String CAMEL_ENTRYPOINT = "/services";
    public static final String JOBS_ENDPOINT = "/jobs";
    public static final String SWAGGER_ENDPOINT = "/openapi.json";


}
