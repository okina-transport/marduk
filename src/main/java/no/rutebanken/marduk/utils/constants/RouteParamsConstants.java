package no.rutebanken.marduk.utils.constants;

public class RouteParamsConstants {

    private RouteParamsConstants() {
        throw new IllegalArgumentException();
    }

    public static final String STATUS = "status";
    public static final String ACTION = "action";
    public static final String PROVIDER = "providerId";
    public static final String FILENAME = "fileName";
    public static final String JOB_ID = "jobId";
    public static final String HEADERS = "headers";

    public static class QueryParams {

        private QueryParams() {
            throw new IllegalArgumentException();
        }

        public static final String IMPORTER = "importer";
        public static final String EXPORTER = "exporter";
        public static final String VALIDATOR = "validator";
    }

    public static class ParamTypes {

        private ParamTypes() {
            throw new IllegalArgumentException();
        }

        public static final String INTEGER = "integer";
    }

    public static class Headers {

        private Headers() {
            throw new IllegalArgumentException();
        }

        public static final String ALL_CAMEL_HTTP = "CamelHttp*";
        public static final String ALL_CAMEL_HEADERS = "Camel*";
        public static final String UPLOAD_INPUT_FILENAME = "uploadInputFilename";
        public static final String UPLOAD_INPUT_CONTENT_TYPE = "uploadInputContentType";
        public static final String UPLOAD_INPUT_FILEPATH = "uploadInputFilePath";
        public static final String FILTER = "filter";
        public static final String LOCATION = "Location";
        public static final String JOB_ID = "RutebankenJobId";
    }

    public static class Description {

        private Description() {
            throw new IllegalArgumentException();
        }

        public static final String PROVIDER_DESCRIPTION = "Provider id as obtained from the nabu service";
    }


    public static class CamelProperty {

        private CamelProperty() {
            throw new IllegalArgumentException();
        }

        public static final String CHOUETTE_URL = "chouette_url";
    }

    public static class SimpleExpression {

        private SimpleExpression() {
            throw new IllegalArgumentException();
        }

        public static final String BODY_IS_NULL = "${body} == null";

    }

}
