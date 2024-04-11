package no.rutebanken.marduk.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Consumer {

    private Long id;
    private String name;
    private ConsumerType type;
    private String serviceUrl;
    private String s3Url;
    private String login;
    private byte[] secretKey;
    private byte[] password;
    private Integer port;
    private String destinationPath;
    private boolean notification = false;
    private List<String> notificationUrls = new ArrayList<>();
    private String datasetId;

    private String exportDate;

    private String description;

    private boolean appendDescription = false;


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

    public ConsumerType getType() {
        return type;
    }

    public void setType(ConsumerType type) {
        this.type = type;
    }

    public String getS3Url() {
        return s3Url;
    }

    public void setS3Url(String s3Url) {
        this.s3Url = s3Url;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public byte[] getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(byte[] secretKey) {
        this.secretKey = secretKey;
    }

    public byte[] getPassword() {
        return password;
    }

    public void setPassword(byte[] password) {
        this.password = password;
    }

    public Integer getPort() { return port; }

    public void setPort(Integer port) { this.port = port; }

    public String getDestinationPath() { return destinationPath; }

    public void setDestinationPath(String destinationPath) { this.destinationPath = destinationPath; }

    public String getServiceUrl() {
        return serviceUrl;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public String toString() {
        return String.format("%d / %s / %s", getId(), getName(), getServiceUrl());
    }

    public boolean isNotification() {
        return notification;
    }

    public void setNotification(boolean notification) {
        this.notification = notification;
    }

    public List<String> getNotificationUrls() { return notificationUrls; }

    public String getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public String getExportDate() {
        return exportDate;
    }

    public void setExportDate(String exportDate) {
        this.exportDate = exportDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isAppendDescription() {
        return appendDescription;
    }

    public void setAppendDescription(boolean appendDescription) {
        this.appendDescription = appendDescription;
    }
}
