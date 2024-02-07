package no.rutebanken.marduk.services.opendatasoft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DataSet {
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
        private String dataset_id;
        private boolean is_published;
        private boolean is_restricted;
        private DefaultSecurity default_security;
        private String created_at;
        private String updated_at;
        private Metadata metadata;

        public String getUid() {
            return uid;
        }

        public void setUid(String uid) {
            this.uid = uid;
        }

        public String getDataset_id() {
            return dataset_id;
        }

        public void setDataset_id(String dataset_id) {
            this.dataset_id = dataset_id;
        }

        public boolean isIs_published() {
            return is_published;
        }

        public void setIs_published(boolean is_published) {
            this.is_published = is_published;
        }

        public boolean isIs_restricted() {
            return is_restricted;
        }

        public void setIs_restricted(boolean is_restricted) {
            this.is_restricted = is_restricted;
        }

        public DefaultSecurity getDefault_security() {
            return default_security;
        }

        public void setDefault_security(DefaultSecurity default_security) {
            this.default_security = default_security;
        }

        public String getCreated_at() {
            return created_at;
        }

        public void setCreated_at(String created_at) {
            this.created_at = created_at;
        }

        public String getUpdated_at() {
            return updated_at;
        }

        public void setUpdated_at(String updated_at) {
            this.updated_at = updated_at;
        }

        public Metadata getMetadata() {
            return metadata;
        }

        public void setMetadata(Metadata metadata) {
            this.metadata = metadata;
        }
    }


    public static class DefaultSecurity {
        private boolean is_data_visible;
        private Object visible_fields;
        private String filter_query;
        private Object api_calls_quota;

        public boolean isIs_data_visible() {
            return is_data_visible;
        }

        public void setIs_data_visible(boolean is_data_visible) {
            this.is_data_visible = is_data_visible;
        }

        public Object getVisible_fields() {
            return visible_fields;
        }

        public void setVisible_fields(Object visible_fields) {
            this.visible_fields = visible_fields;
        }

        public String getFilter_query() {
            return filter_query;
        }

        public void setFilter_query(String filter_query) {
            this.filter_query = filter_query;
        }

        public Object getApi_calls_quota() {
            return api_calls_quota;
        }

        public void setApi_calls_quota(Object api_calls_quota) {
            this.api_calls_quota = api_calls_quota;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Metadata {
        private MetadataDetails defaultDetails;

        private Internal internal;

        public MetadataDetails getDefaultDetails() {
            return defaultDetails;
        }

        public void setDefaultDetails(MetadataDetails defaultDetails) {
            this.defaultDetails = defaultDetails;
        }


        public Internal getInternal() {
            return internal;
        }

        public void setInternal(Internal internal) {
            this.internal = internal;
        }
    }

    public static class MetadataDetails {
        private Title title;
        private Modified modified;
        private ModifiedUpdates modified_updates_on_metadata_change;
        private ModifiedUpdates modified_updates_on_data_change;
        private GeographicReference geographic_reference;
        private boolean geographic_reference_auto;
        private Language language;
        private Description description;
        private Keyword keyword;
        private Publisher publisher;

        public Title getTitle() {
            return title;
        }

        public void setTitle(Title title) {
            this.title = title;
        }

        public Modified getModified() {
            return modified;
        }

        public void setModified(Modified modified) {
            this.modified = modified;
        }

        public ModifiedUpdates getModified_updates_on_metadata_change() {
            return modified_updates_on_metadata_change;
        }

        public void setModified_updates_on_metadata_change(ModifiedUpdates modified_updates_on_metadata_change) {
            this.modified_updates_on_metadata_change = modified_updates_on_metadata_change;
        }

        public ModifiedUpdates getModified_updates_on_data_change() {
            return modified_updates_on_data_change;
        }

        public void setModified_updates_on_data_change(ModifiedUpdates modified_updates_on_data_change) {
            this.modified_updates_on_data_change = modified_updates_on_data_change;
        }

        public GeographicReference getGeographic_reference() {
            return geographic_reference;
        }

        public void setGeographic_reference(GeographicReference geographic_reference) {
            this.geographic_reference = geographic_reference;
        }

        public boolean isGeographic_reference_auto() {
            return geographic_reference_auto;
        }

        public void setGeographic_reference_auto(boolean geographic_reference_auto) {
            this.geographic_reference_auto = geographic_reference_auto;
        }

        public Language getLanguage() {
            return language;
        }

        public void setLanguage(Language language) {
            this.language = language;
        }

        public Description getDescription() {
            return description;
        }

        public void setDescription(Description description) {
            this.description = description;
        }

        public Keyword getKeyword() {
            return keyword;
        }

        public void setKeyword(Keyword keyword) {
            this.keyword = keyword;
        }

        public Publisher getPublisher() {
            return publisher;
        }

        public void setPublisher(Publisher publisher) {
            this.publisher = publisher;
        }
    }

    public static class Title {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class Modified {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class ModifiedUpdates {
        private boolean value;

        
       
    }

    public static class GeographicReference {
        private List<String> value;

        public List<String> getValue() {
            return value;
        }

        public void setValue(List<String> value) {
            this.value = value;
        }
    }

    public static class Language {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class Description {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class Keyword {
        private List<String> value;

        public List<String> getValue() {
            return value;
        }

        public void setValue(List<String> value) {
            this.value = value;
        }
    }

    public static class Publisher {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }


    public static class Internal {
        private License license_id;
        private Theme theme_id;
        private Draft draft;

        public License getLicense_id() {
            return license_id;
        }

        public void setLicense_id(License license_id) {
            this.license_id = license_id;
        }

        public Theme getTheme_id() {
            return theme_id;
        }

        public void setTheme_id(Theme theme_id) {
            this.theme_id = theme_id;
        }

        public Draft getDraft() {
            return draft;
        }

        public void setDraft(Draft draft) {
            this.draft = draft;
        }
    }

    public static class License {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class Theme {
        private List<String> value;

        public List<String> getValue() {
            return value;
        }

        public void setValue(List<String> value) {
            this.value = value;
        }
    }

    public static class Draft {
        private boolean value;

        public boolean isValue() {
            return value;
        }

        public void setValue(boolean value) {
            this.value = value;
        }
    }
}
