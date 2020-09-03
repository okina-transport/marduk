package no.rutebanken.marduk.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Consumer {

    private Long id;
    private String name;
    private ConsumerType type;
    private String serviceUrl;
    private String s3Url;
    private String login;
    private String secretKey;
    private String password;
    private Integer port;
    private String destinationPath;

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

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
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
}
