package no.rutebanken.marduk.services.opendatasoft;

import java.util.List;

public class FileInfos {
    private int total_count;
    private String next;
    private String previous;
    private List<Result> results;

    public int getTotal_count() {
        return total_count;
    }

    public void setTotal_count(int total_count) {
        this.total_count = total_count;
    }

    public String getNext() {
        return next;
    }

    public void setNext(String next) {
        this.next = next;
    }

    public String getPrevious() {
        return previous;
    }

    public void setPrevious(String previous) {
        this.previous = previous;
    }

    public List<Result> getResults() {
        return results;
    }

    public void setResults(List<Result> results) {
        this.results = results;
    }

    public static class Result {
        private String uid;
        private String title;
        private String type;
        private Datasource datasource;
        private Params params;
        private String updated_at;

        public String getUid() {
            return uid;
        }

        public void setUid(String uid) {
            this.uid = uid;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Datasource getDatasource() {
            return datasource;
        }

        public void setDatasource(Datasource datasource) {
            this.datasource = datasource;
        }

        public Params getParams() {
            return params;
        }

        public void setParams(Params params) {
            this.params = params;
        }

        public String getUpdated_at() {
            return updated_at;
        }

        public void setUpdated_at(String updated_at) {
            this.updated_at = updated_at;
        }
    }

    public static class Datasource {
        private String type;
        private File file;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public File getFile() {
            return file;
        }

        public void setFile(File file) {
            this.file = file;
        }
    }

    public static class File {
        private String uid;

        public String getUid() {
            return uid;
        }

        public void setUid(String uid) {
            this.uid = uid;
        }
    }

    public static class Params {
        private boolean doublequote;
        private String encoding;
        private int first_row_no;
        private boolean headers_first_row;
        private String separator;
        private boolean extract_meta;
        private boolean extract_geopoint;

        public boolean isDoublequote() {
            return doublequote;
        }

        public void setDoublequote(boolean doublequote) {
            this.doublequote = doublequote;
        }

        public String getEncoding() {
            return encoding;
        }

        public void setEncoding(String encoding) {
            this.encoding = encoding;
        }

        public int getFirst_row_no() {
            return first_row_no;
        }

        public void setFirst_row_no(int first_row_no) {
            this.first_row_no = first_row_no;
        }

        public boolean isHeaders_first_row() {
            return headers_first_row;
        }

        public void setHeaders_first_row(boolean headers_first_row) {
            this.headers_first_row = headers_first_row;
        }

        public String getSeparator() {
            return separator;
        }

        public void setSeparator(String separator) {
            this.separator = separator;
        }

        public boolean isExtract_meta() {
            return extract_meta;
        }

        public void setExtract_meta(boolean extract_meta) {
            this.extract_meta = extract_meta;
        }

        public boolean isExtract_geopoint() {
            return extract_geopoint;
        }

        public void setExtract_geopoint(boolean extract_geopoint) {
            this.extract_geopoint = extract_geopoint;
        }
    }
}
