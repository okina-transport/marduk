package no.rutebanken.marduk.domain;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Recipient {
    private Long id;
    private String email;
    private Long importConfigurationId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getImportConfigurationId() {
        return importConfigurationId;
    }

    public void setImportConfigurationId(Long importConfigurationId) {
        this.importConfigurationId = importConfigurationId;
    }
}
