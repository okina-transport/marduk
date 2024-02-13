package no.rutebanken.marduk.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.services.opendatasoft.DataSet;
import no.rutebanken.marduk.services.opendatasoft.DatasetMetadata;
import no.rutebanken.marduk.services.opendatasoft.FileInfos;
import no.rutebanken.marduk.services.opendatasoft.FileMetadata;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class OpendatasoftService {

    private static Logger logger = LoggerFactory.getLogger(OpendatasoftService.class);

    /**
     * Main method to upload file to opendatasoft and update metadata
     * @param fileToSend
     *      file that must be uploaded to opendatasoft
     * @param opendatasoftURL
     *      base url of the opendatasoft server
     * @param datasetId
     *      dataset on which data must be uploaded
     * @param secretKey
     *      secret key to access dataset
     * @param exportDate
     *      temporal period written in the metadata
     * @param description
     *      description written in the metadata
     * @param fileName
     *      name of the file
     * @throws IOException
     *      Exception
     */
    public void sendToOpendatasoft(InputStream fileToSend, String opendatasoftURL, String datasetId, String secretKey, String exportDate, String description, String fileName) throws IOException {


        // GET DATASET UID
        Optional<String> datasetInfosOpt = getDatasetInfos(opendatasoftURL, datasetId, secretKey);

        if (!datasetInfosOpt.isPresent()) {
            logger.error("unable to get dataset infos");
            return;
        }

        String rawDatasetInfos = datasetInfosOpt.get();
        Optional<DataSet> datasetOpt = convertToObjectType(rawDatasetInfos, DataSet.class);

        DataSet dataset = datasetOpt.get();
        String datasetUID = dataset.getResults().get(0).getUid();

        if (StringUtils.isEmpty(datasetUID)) {
            logger.error("cannot find UID");
            return;
        }

        logger.info("Opendatasoft export - found dataset UID  : " + datasetUID);


        String newFileUID = uploadFileToOpendataSoft(opendatasoftURL, datasetUID, fileName, secretKey, fileToSend);
        createOrUpdateResource(opendatasoftURL, datasetUID, secretKey, fileName, newFileUID);
        updateMetadata(opendatasoftURL, datasetUID, secretKey, description, exportDate);
    }

    /**
     * Update description and temporal metadata
     * @param opendatasoftURL
     *      base url of the opendatasoft server
     * @param datasetUID
     *      dataset on which action must be performed
     * @param secretKey
     *      secret key to access dataset
     * @param description
     *      description written in the metadata
     * @param period
     *      temporal period written in the metadata
     */
    private void updateMetadata(String opendatasoftURL, String datasetUID, String secretKey, String description, String period) {
        DatasetMetadata descriptionMetadata = new DatasetMetadata();
        descriptionMetadata.setValue(description);
        String descriptionMetadataURL = buildDescriptionMetaDataURL(opendatasoftURL, datasetUID);
        launchConnectionOnURL(descriptionMetadataURL, secretKey, "PUT", MediaType.APPLICATION_JSON, convertToJSON(descriptionMetadata));

        DatasetMetadata temporalPeriodMetadata = new DatasetMetadata();
        temporalPeriodMetadata.setValue(period);
        String tempPeriodURL = buildTempPeriodMetaDataURL(opendatasoftURL, datasetUID);
        launchConnectionOnURL(tempPeriodURL, secretKey, "PUT", MediaType.APPLICATION_JSON, convertToJSON(temporalPeriodMetadata));


    }


    /**
     * Create or update a resource (a reseource is a link between a dataset and a stored file
     * @param opendatasoftURL
     *      base url of the opendatasoft server
     * @param datasetUID
     *      dataset on which action must be performed
     * @param secretKey
     *      secret key to access dataset
     * @param fileName
     *      name of the file
     * @param newFileUID
     *      UID of the stored file, previously updated
     */
    private void createOrUpdateResource(String opendatasoftURL, String datasetUID, String secretKey, String fileName, String newFileUID){

        Optional<String> rawResourcesInfosOpt = getResourcesInfos(opendatasoftURL, datasetUID, secretKey);

        if (!rawResourcesInfosOpt.isPresent()) {
            logger.error("unable to get resources infos");
            return;
        }

        String rawResourcesInfos = rawResourcesInfosOpt.get();
        Optional<FileInfos> resourcesInfosOpt = convertToObjectType(rawResourcesInfos, FileInfos.class);
        Optional<String> resourceUIDOpt = getResourceUID(resourcesInfosOpt.get(), fileName);

        FileInfos.Result requestParameters = createRequestParameters(fileName, newFileUID);
        String resourceCreationURL = buildResourcesURL(opendatasoftURL, datasetUID);
        String method;
        String updateURL;

        if (resourceUIDOpt.isPresent()){
            method = "PUT";
            updateURL = resourceCreationURL + resourceUIDOpt.get();
        }else{
            method = "POST";
            updateURL = resourceCreationURL;
        }

        launchConnectionOnURL(updateURL, secretKey, method, MediaType.APPLICATION_JSON, convertToJSON(requestParameters));

    }


    /**
     *
     * @param fileName
     *      name of the file
     * @param fileUID     *
     *      UID of the stored file, previously updated
     * @return
     *      an object containing data that will be sent in request body
     */
    private FileInfos.Result createRequestParameters(String fileName, String fileUID){
        // Building parameters that will be sent on POST request as a JSON body
        FileInfos.Result requestParameters = new FileInfos.Result();
        requestParameters.setType("file");
        requestParameters.setTitle(fileName);
        FileInfos.Datasource requestDatasource = new FileInfos.Datasource();
        requestDatasource.setType("uploaded_file");
        FileInfos.File requestFile = new FileInfos.File();
        requestFile.setUid(fileUID);
        requestDatasource.setFile(requestFile);
        requestParameters.setDatasource(requestDatasource);
        return requestParameters;
    }


    /**
     *
     * @param opendatasoftURL
     *      base url of the opendatasoft server
     * @param datasetUID
     *      dataset on which action must be performed
     * @param fileName
     *      name of the file
     * @param secretKey
     *      secret key to access dataset
     * @param fileToSend
     *      file that must be send to server
     * @return
     *      UID of the stored filed that have been sent to opendata server
     */
    private String uploadFileToOpendataSoft(String opendatasoftURL, String datasetUID, String fileName, String secretKey, InputStream fileToSend) {
        String uploadURL = buildUploadURL(opendatasoftURL,datasetUID);
        Optional<String> createdUIDOpt = postFileOnURL(uploadURL, secretKey, fileName, fileToSend);
        String newFileUID = createdUIDOpt.get();
        logger.info("New file uploaded. UID : " + newFileUID);
        return newFileUID;
    }


    /**
     * Builds a URL to upload a file on the specified dataset
     * @param opendatasoftURL
     *      base url of the opendatasoft server
     * @param datasetUID
     *      dataset on which action must be performed
     * @return
     *      the url
     */
    private String buildUploadURL(String opendatasoftURL, String datasetUID) {
        if (!opendatasoftURL.endsWith("/")) {
            opendatasoftURL = opendatasoftURL + "/";
        }
        return opendatasoftURL + "automation/v1.0/datasets/" + datasetUID + "/resources/files/";
    }

    /**
     * Search into results for a resource that matches fileName
     * @param fileInfos
     *      informations of all resources of the dataset
     * @param fileName
     *      the name that must be searched
     * @return
     *      the UID of the resource that matches fileName
     */
    private Optional<String> getResourceUID(FileInfos fileInfos, String fileName) {
        for (FileInfos.Result result : fileInfos.getResults()) {
            if (fileName.equals(result.getTitle())){
                return Optional.of(result.getUid());
            }
        }
        return Optional.empty();
    }


    /**
     * Recover informations about resources of a specific dataset
     * @param opendatasoftURL
     *      base url of the opendatasoft server
     * @param datasetUID
     *      dataset on which action must be performed
     * @param secretKey
     *      secret key to access dataset
     * @return
     *      the json result containing informations about resources of a dataset
     *
     */
    private Optional<String> getResourcesInfos(String opendatasoftURL, String datasetUID, String secretKey) {
        String connectionURL = buildResourcesURL(opendatasoftURL, datasetUID);
        return launchConnectionOnURL(connectionURL, secretKey, "GET",MediaType.APPLICATION_JSON);
    }

    /**
     * Launch a request on a URL
     *
     * @param opendatasoftURL
     *      base url of the opendatasoft server
     * @param secretKey
     *      secret key to access dataset
     * @param requestMethod
     *      request method (PUT/POST/GET, etc)
     * @param mediaType
     *      Content-type of the request
     * @return
     *      result of the request
     */
    private Optional<String> launchConnectionOnURL(String opendatasoftURL, String secretKey, String requestMethod, MediaType mediaType) {
        return launchConnectionOnURL(opendatasoftURL, secretKey, requestMethod, mediaType, Optional.empty());
    }


    /**
     * Launch a request on a URL
     *
     * @param opendatasoftURL
     *      base url of the opendatasoft server
     * @param secretKey
     *      secret key to access dataset
     * @param requestMethod
     *      request method (PUT/POST/GET, etc)
     * @param mediaType
     *      Content-type of the request
     * @param jsonBody
     *      json body of the request
     * @return
     *      the result of the request
     */
    private Optional<String> launchConnectionOnURL(String opendatasoftURL, String secretKey, String requestMethod, MediaType mediaType, Optional<String> jsonBody) {

        try {
            URL url = new URL(opendatasoftURL);
            HttpURLConnection connection;
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(requestMethod);
            connection.setRequestProperty("Content-type", mediaType.toString());
            connection.setRequestProperty("Authorization", "apikey " + secretKey);
            connection.setDoOutput(true);

            if (jsonBody.isPresent()){
                try(OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonBody.get().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
            }


            InputStream inputStream = connection.getInputStream();
            String rawResult = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
            connection.disconnect();
            return Optional.of(rawResult);
        } catch (IOException e) {
            logger.error("Error while launching command for URL : " + opendatasoftURL,e);
        }
        return Optional.empty();

    }


    /**
     * Upload a file on opendatasoft server
     * @param opendatasoftURL
     *      base url of the opendatasoft server
     * @param secretKey
     *      secret key to access dataset
     * @param fileName
     *      name of the file
     * @param fileToSend
     *      file that must be sent to the server
     * @return
     *      the UID of the stored file that have been uploaded
     */
    private Optional<String> postFileOnURL(String opendatasoftURL, String secretKey, String fileName, InputStream fileToSend) {
        String boundary = Long.toHexString(System.currentTimeMillis());

        try {
            URL url = new URL(opendatasoftURL);
            HttpURLConnection connection;
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-type", "multipart/form-data; boundary=" + boundary);
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "apikey " + secretKey);

            OutputStream output = connection.getOutputStream();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true);

            writer.append("--" + boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"").append("\r\n");
            writer.append("Content-Type: " + HttpURLConnection.guessContentTypeFromName(fileName)).append("\r\n");
            writer.append("\r\n");
            writer.flush();


            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileToSend.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            output.flush();

            writer.append("\r\n").append("--" + boundary + "--").append("\r\n");
            writer.flush();


            Optional<String> response = getResponse(connection);
            if (!response.isPresent()){
                logger.error("No response from server. Stopping export");
                return Optional.empty();
            }

            Optional<FileMetadata> fileMetadataOpt = convertToObjectType(response.get(), FileMetadata.class);
            FileMetadata fileMetadata = fileMetadataOpt.get();

            connection.disconnect();


            return Optional.of(fileMetadata.getUid());      
        }  catch (IOException e) {
            logger.error("Error while launching POST command for URL : " + opendatasoftURL);
            logger.error(e.getMessage());
        }
        return Optional.empty();

    }

    /**
     * Return the response of the connection
     * @param connection
     *      connection for which response must be read
     * @return
     *      the response
     */
    
    private Optional<String> getResponse(HttpURLConnection connection){
        try (InputStream responseStream = connection.getInputStream()) {
            String rawResult = new BufferedReader(new InputStreamReader(responseStream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));
            return Optional.of(rawResult);
        } catch (IOException e) {
            logger.error("Error while reading response", e);
            return Optional.empty();
        } finally {
            connection.disconnect();
        }
    }


    /**
     * Build URL to access resources of a dataset
     * @param opendatasoftURL
     *      base url of the opendatasoft server
     * @param datasetUID
     *      dataset on which action must be performed
     * @return
     *      the built url
     */
    private String buildResourcesURL(String opendatasoftURL, String datasetUID) {
        return buildDatasetBaseURL(opendatasoftURL) + "automation/v1.0/datasets/" + datasetUID + "/resources/";
    }

    /**
     * Builds a URL to access description metadata
     * @param opendatasoftURL
     *      base url of the opendatasoft server
     * @param datasetUID
     *      dataset on which action must be performed
     * @return
     *      the url
     */
    private String buildDescriptionMetaDataURL(String opendatasoftURL, String datasetUID) {
        return buildDatasetBaseURL(opendatasoftURL) + "automation/v1.0/datasets/" + datasetUID + "/metadata/default/description";
    }

    /**
     * Builds a url to access temporal metadata
     * @param opendatasoftURL
     *      base url of the opendatasoft server
     * @param datasetUID
     *      dataset on which action must be performed
     * @return
     *      the url
     */
    private String buildTempPeriodMetaDataURL(String opendatasoftURL, String datasetUID) {
        return buildDatasetBaseURL(opendatasoftURL) + "automation/v1.0/datasets/" + datasetUID + "/metadata/dcat/temporal";
    }


    /**
     * Adds a slash at the end of the URL, if not already existing
     * @param opendatasoftURL
     *      base url of the opendatasoft server
     * @return
     *      the url
     */
    private String buildDatasetBaseURL(String opendatasoftURL) {
        if (!opendatasoftURL.endsWith("/")) {
            opendatasoftURL = opendatasoftURL + "/";
        }
        return opendatasoftURL;
    }


    /**
     * Converts a json response to a java object
     * @param rawJson
     *      json containing data
     * @param objectType
     *      the type of object that must be built
     * @return
     *      the object
     *
     */
    public static <T> Optional<T> convertToObjectType(String rawJson, Class<T> objectType) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            T obj = objectMapper.readValue(rawJson, objectType);
            return Optional.of(obj);
        } catch (IOException e) {
            logger.error("Error while deserializing", e);
            return Optional.empty();
        }
    }

    /**
     * Converts a java object to a json
     * @param objectToConvert
     *      java object to convert
     * @return
     *      the json containing data
     */
    public Optional<String> convertToJSON(Object objectToConvert){
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        try {
            return Optional.of(mapper.writeValueAsString(objectToConvert));
        } catch (Exception e) {
            logger.error("Error while converting to json", e);
            return Optional.empty();
        }
    }


    /**
     * Gets information about a dataset
     * @param opendatasoftURL
     *      base url of the opendatasoft server
     * @param datasetUID
     *      dataset on which action must be performed
     * @param secretKey
     *      secret key to access dataset
     * @return
     *      the dataset informations
     */
    public Optional<String> getDatasetInfos(String opendatasoftURL, String datasetUID, String secretKey) {

        if (StringUtils.isEmpty(opendatasoftURL)) {
            logger.error("opendatasoft URL is empty");
            return Optional.empty();
        }
        String connectionURL = buildDatasetInfoURL(opendatasoftURL, datasetUID);
        return launchConnectionOnURL(connectionURL, secretKey,"GET", MediaType.APPLICATION_JSON);

    }

    /**
     * builds a URL to access dataset informations
     * @param opendatasoftURL
     *      base url of the opendatasoft server
     * @param datasetUID
     *      dataset on which action must be performed
     * @return
     *      the url
     */
    private String buildDatasetInfoURL(String opendatasoftURL, String datasetUID) {
        if (!opendatasoftURL.endsWith("/")) {
            opendatasoftURL = opendatasoftURL + "/";
        }

        return opendatasoftURL + "automation/v1.0/datasets?dataset_id=" + datasetUID;
    }
}
