package no.rutebanken.marduk.routes.chouette.json.importer;

import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.routes.chouette.json.IdParameters;


/**
 * Simple POJO to store parameters comming from web application
 */
public class RawImportParameters {

    private String fileName;
    private String fileType;
    private Long providerId;
    private String user;
    private String description;
    private boolean routeMerge;
    private String splitCharacter;
    private IdParameters idParameters;
    private boolean ignoreCommercialPoints;
    private boolean isAnalyzeJob;
    private boolean keepBoardingAlighting;
    private boolean keepStopGeolocalisation;
    private boolean keepStopNames;
    private ImportMode importMode;
    private Provider provider;
    private String cleanMode;
    private boolean removeParentStations;
    private boolean importShapesFile;

    private boolean updateStopAccess;

    private boolean railUICprocessing;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isRouteMerge() {
        return routeMerge;
    }

    public void setRouteMerge(boolean routeMerge) {
        this.routeMerge = routeMerge;
    }

    public String getSplitCharacter() {
        return splitCharacter;
    }

    public void setSplitCharacter(String splitCharacter) {
        this.splitCharacter = splitCharacter;
    }

    public IdParameters getIdParameters() {
        return idParameters;
    }

    public void setIdParameters(IdParameters idParameters) {
        this.idParameters = idParameters;
    }


    public boolean isIgnoreCommercialPoints() {
        return ignoreCommercialPoints;
    }

    public void setIgnoreCommercialPoints(boolean ignoreCommercialPoints) {
        this.ignoreCommercialPoints = ignoreCommercialPoints;
    }

    public boolean isAnalyzeJob() {
        return isAnalyzeJob;
    }

    public void setAnalyzeJob(boolean analyzeJob) {
        isAnalyzeJob = analyzeJob;
    }

    public boolean isKeepBoardingAlighting() {
        return keepBoardingAlighting;
    }

    public void setKeepBoardingAlighting(boolean keepBoardingAligh) {
        this.keepBoardingAlighting = keepBoardingAligh;
    }

    public void setKeepStopGeolocalisation(boolean keepStopGeolocalisation) {
        this.keepStopGeolocalisation = keepStopGeolocalisation;
    }

    public boolean isKeepStopGeolocalisation() {
        return keepStopGeolocalisation;
    }

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public ImportMode getImportMode() {
        return importMode;
    }

    public void setImportMode(ImportMode importMode) {
        this.importMode = importMode;
    }

    public String getCleanMode() {
        return cleanMode;
    }

    public void setCleanMode(String cleanMode) {
        this.cleanMode = cleanMode;
    }

    public boolean isKeepStopNames() {
        return keepStopNames;
    }

    public void setKeepStopNames(boolean keepStopNames) {
        this.keepStopNames = keepStopNames;
    }

    public boolean isRemoveParentStations() {
        return removeParentStations;
    }

    public void setRemoveParentStations(boolean removeParentStations) {
        this.removeParentStations = removeParentStations;
    }

    public boolean isUpdateStopAccess() {
        return updateStopAccess;
    }

    public void setUpdateStopAccess(boolean updateStopAccess) {
        this.updateStopAccess = updateStopAccess;
    }

    public boolean isImportShapesFile() {
        return importShapesFile;
    }

    public void setImportShapesFile(boolean importShapesFile) {
        this.importShapesFile = importShapesFile;
    }

    public boolean isRailUICprocessing() {
        return railUICprocessing;
    }

    public void setRailUICprocessing(boolean railUICprocessing) {
        this.railUICprocessing = railUICprocessing;
    }
}
