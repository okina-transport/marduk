package no.rutebanken.marduk.domain;

public class ImportParameters {
    private Long id;
    private String importMode;
    private String importType;
    private Boolean routeMerge;
    private String splitCharacter;
    private String linePrefixToRemove;
    private String commercialPointPrefixToRemove;
    private String quayIdPrefixToRemove;
    private Boolean keepBoardingAlighting;
    private Boolean keepStopGeolocalisation;
    private Boolean ignoreCommercialPoints;
    private String stopAreaPrefixToRemove;
    private String areaCentroidPrefixToRemove;
    private String objectIdPrefix;
    private Boolean keepStopNames;
    private Boolean importShapesFile;
    private Long importConfigurationId;
    private Boolean removeParentStations;

    private Boolean updateStopAccessibility;

    private Boolean railUICprocessing;

    private Boolean generateMapMatching;

    private Boolean routesReorganization;

    private Long distanceGeolocation;

    private String description;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getImportMode() {
        return importMode;
    }

    public void setImportMode(String importMode) {
        this.importMode = importMode;
    }

    public String getImportType() {
        return importType;
    }

    public void setImportType(String importType) {
        this.importType = importType;
    }

    public Boolean getRouteMerge() {
        return routeMerge;
    }

    public void setRouteMerge(Boolean routeMerge) {
        this.routeMerge = routeMerge;
    }

    public String getSplitCharacter() {
        return splitCharacter;
    }

    public void setSplitCharacter(String splitCharacter) {
        this.splitCharacter = splitCharacter;
    }

    public String getLinePrefixToRemove() {
        return linePrefixToRemove;
    }

    public void setLinePrefixToRemove(String linePrefixToRemove) {
        this.linePrefixToRemove = linePrefixToRemove;
    }

    public String getCommercialPointPrefixToRemove() {
        return commercialPointPrefixToRemove;
    }

    public void setCommercialPointPrefixToRemove(String commercialPointPrefixToRemove) {
        this.commercialPointPrefixToRemove = commercialPointPrefixToRemove;
    }

    public Boolean getRemoveParentStations() {
        return removeParentStations;
    }

    public void setRemoveParentStations(Boolean removeParentStations) {
        this.removeParentStations = removeParentStations;
    }

    public Boolean getRailUICprocessing() {
        return railUICprocessing;
    }

    public void setRailUICprocessing(Boolean railUICprocessing) {
        this.railUICprocessing = railUICprocessing;
    }

    public String getQuayIdPrefixToRemove() {
        return quayIdPrefixToRemove;
    }

    public void setQuayIdPrefixToRemove(String quayIdPrefixToRemove) {
        this.quayIdPrefixToRemove = quayIdPrefixToRemove;
    }

    public Boolean getKeepBoardingAlighting() {
        return keepBoardingAlighting;
    }

    public void setKeepBoardingAlighting(Boolean keepBoardingAlighting) {
        this.keepBoardingAlighting = keepBoardingAlighting;
    }

    public Boolean getKeepStopGeolocalisation() {
        return keepStopGeolocalisation;
    }

    public void setKeepStopGeolocalisation(Boolean keepStopGeolocalisation) {
        this.keepStopGeolocalisation = keepStopGeolocalisation;
    }

    public Boolean getIgnoreCommercialPoints() {
        return ignoreCommercialPoints;
    }

    public void setIgnoreCommercialPoints(Boolean ignoreCommercialPoints) {
        this.ignoreCommercialPoints = ignoreCommercialPoints;
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

    public String getObjectIdPrefix() {
        return objectIdPrefix;
    }

    public void setObjectIdPrefix(String objectIdPrefix) {
        this.objectIdPrefix = objectIdPrefix;
    }

    public Boolean getKeepStopNames() {
        return keepStopNames;
    }

    public void setKeepStopNames(Boolean keepStopNames) {
        this.keepStopNames = keepStopNames;
    }

    public Long getImportConfigurationId() {
        return importConfigurationId;
    }

    public void setImportConfigurationId(Long importConfigurationId) {
        this.importConfigurationId = importConfigurationId;
    }

    public Boolean getImportShapesFile() {
        return importShapesFile;
    }

    public void setImportShapesFile(Boolean importShapesFile) {
        this.importShapesFile = importShapesFile;
    }

    public Boolean getUpdateStopAccessibility() {
        return updateStopAccessibility;
    }

    public void setUpdateStopAccessibility(Boolean updateStopAccessibility) {
        this.updateStopAccessibility = updateStopAccessibility;
    }

    public Boolean getGenerateMapMatching() {
        return generateMapMatching;
    }

    public void setGenerateMapMatching(Boolean generateMapMatching) {
        this.generateMapMatching = generateMapMatching;
    }

    public Long getDistanceGeolocation(){return distanceGeolocation;}

    public void setDistanceGeolocation(Long distanceGeolocation){this.distanceGeolocation = distanceGeolocation;}

    public  String getDescription(){ return description;}

    public void setDescription(String description){this.description = description;}

    public Boolean getRoutesReorganization() {
        return routesReorganization;
    }

    public void setRoutesReorganization(Boolean routesReorganization) {
        this.routesReorganization = routesReorganization;
    }
}
