package no.rutebanken.marduk.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.util.List;

import static java.util.stream.Collectors.toList;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExportTemplate implements Serializable {

    private Long id;
    private String name;
    private String description;
    private ExportType type;

    private Long startDate;
    private Long endDate;

    private String exportedFileName;

    private List<Line> lines;
    private List<Consumer> consumers;
    private List<String> referentials;

    private String stopIdPrefix;
    private String lineIdPrefix;
    private IdFormat idFormat;
    private String idSuffix;
    private String commercialPointIdPrefix;

    private Boolean commercialPointExport;

    private Boolean exportEnabled;
    private AttributionsExportModes attributionsExportModes;

    private String postProcess;

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExportedFileName() {
        return exportedFileName;
    }

    public void setExportedFileName(String exportedFileName) {
        this.exportedFileName = exportedFileName;
    }

    public ExportType getType() {
        return type;
    }

    public void setType(ExportType type) {
        this.type = type;
    }

    public List<Line> getLines() {
        return lines;
    }

    public void setLines(List<Line> lines) {
        this.lines = lines;
    }


    public Long getStartDate() {
        return startDate;
    }

    public void setStartDate(Long startDate) {
        this.startDate = startDate;
    }

    public Long getEndDate() {
        return endDate;
    }

    public void setEndDate(Long endDate) {
        this.endDate = endDate;
    }

    public List<Consumer> getConsumers() {
        return consumers;
    }

    public void setConsumers(List<Consumer> consumers) {
        this.consumers = consumers;
    }

    public String getStopIdPrefix() {
        return stopIdPrefix;
    }

    public void setStopIdPrefix(String stopIdPrefix) {
        this.stopIdPrefix = stopIdPrefix;
    }

    public String getLineIdPrefix() {
        return lineIdPrefix;
    }

    public void setLineIdPrefix(String lineIdPrefix) {
        this.lineIdPrefix = lineIdPrefix;
    }

    public IdFormat getIdFormat() {
        return idFormat;
    }

    public void setIdformat(IdFormat idFormat) {
        this.idFormat = idFormat;
    }

    public String getIdSuffix() {
        return idSuffix;
    }

    public void setIdSuffix(String idSuffix) {
        this.idSuffix = idSuffix;
    }

    public String getCommercialPointIdPrefix() {
        return commercialPointIdPrefix;
    }

    public void setCommercialPointIdPrefix(String commercialPointIdPrefix) {
        this.commercialPointIdPrefix = commercialPointIdPrefix;
    }

    public Boolean getCommercialPointExport() {
        return commercialPointExport;
    }

    public void setCommercialPointExport(Boolean commercialPointExport) {
        this.commercialPointExport = commercialPointExport;
    }

    public Boolean getExportEnabled() {
        return exportEnabled;
    }

    public void setExportEnabled(Boolean exportEnabled) {
        this.exportEnabled = exportEnabled;
    }

    public List<String> getReferentials() {
        return referentials;
    }

    public void setReferentials(List<String> referentials) {
        this.referentials = referentials;
    }

    public AttributionsExportModes getAttributionsExportModes() {
        return attributionsExportModes;
    }

    public void setAttributionsExportModes(AttributionsExportModes attributionsExportModes) {
        this.attributionsExportModes = attributionsExportModes;
    }

    public String getPostProcess() {
        return postProcess;
    }

    public void setPostProcess(String postProcess) {
        this.postProcess = postProcess;
    }
}
