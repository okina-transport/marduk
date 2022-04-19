package no.rutebanken.marduk.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ImportConfiguration implements Serializable {

    private Long id;
    private String name;
    private boolean activated;
    private List<ConfigurationFtp> configurationFtpList;
    private List<ConfigurationUrl> configurationUrlList;
    private List<Recipient> recipients;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isActivated() {
        return activated;
    }

    public void setActivated(boolean activated) {
        this.activated = activated;
    }

    public List<ConfigurationFtp> getConfigurationFtpList() {
        return configurationFtpList;
    }

    public void setConfigurationFtpList(List<ConfigurationFtp> configurationFtpList) {
        this.configurationFtpList = configurationFtpList;
    }

    public List<ConfigurationUrl> getConfigurationUrlList() {
        return configurationUrlList;
    }

    public void setConfigurationUrlList(List<ConfigurationUrl> configurationUrlList) {
        this.configurationUrlList = configurationUrlList;
    }

    public List<Recipient> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<Recipient> recipients) {
        this.recipients = recipients;
    }
}
