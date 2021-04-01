package no.rutebanken.marduk.routes.chouette.json;

import no.rutebanken.marduk.domain.IdFormat;

public class IdParameters {

    private String stopIdPrefix;
    private String lineIdPrefix;
    private IdFormat idFormat;
    private String idSuffix;
    private String commercialPointIdPrefix;
    private String stopAreaPrefixToRemove;
    private String areaCentroidPrefixToRemove;
    private String commercialPointIdPrefixToRemove;
    private String quayIdPrefixToRemove;
    private String linePrefixToRemove;


    public IdParameters() {
    }

    public IdParameters(String stopIdPrefix, IdFormat idFormat, String idSuffix, String lineIdPrefix,String commercialPointIdPrefix) {
        this.stopIdPrefix = stopIdPrefix;
        this.idFormat = idFormat;
        this.idSuffix = idSuffix;
        this.lineIdPrefix = lineIdPrefix;
        this.commercialPointIdPrefix = commercialPointIdPrefix;
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

    public void setIdFormat(IdFormat idFormat) {
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

    public String getStopAreaPrefixToRemove() {
        return stopAreaPrefixToRemove;
    }

    public void setStopAreaPrefixToRemove(String stopAreaPrefixToRemove) {
        this.stopAreaPrefixToRemove = stopAreaPrefixToRemove;
    }

    public String getAreaCentroidPrefixToRemove() {
        return areaCentroidPrefixToRemove;
    }

    public void setAreaCentroidPrefixToRemove(String areaCentroidPrefixToRemove) {
        this.areaCentroidPrefixToRemove = areaCentroidPrefixToRemove;
    }

    public String getCommercialPointIdPrefixToRemove() {
        return commercialPointIdPrefixToRemove;
    }

    public void setCommercialPointIdPrefixToRemove(String commercialPointIdPrefixToRemove) {
        this.commercialPointIdPrefixToRemove = commercialPointIdPrefixToRemove;
    }

    public String getQuayIdPrefixToRemove() {
        return quayIdPrefixToRemove;
    }

    public void setQuayIdPrefixToRemove(String quayIdPrefixToRemove) {
        this.quayIdPrefixToRemove = quayIdPrefixToRemove;
    }

    public String getLinePrefixToRemove() {
        return linePrefixToRemove;
    }

    public void setLinePrefixToRemove(String linePrefixToRemove) {
        this.linePrefixToRemove = linePrefixToRemove;
    }
}
