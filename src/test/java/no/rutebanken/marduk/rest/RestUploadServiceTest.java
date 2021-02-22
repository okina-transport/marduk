package no.rutebanken.marduk.rest;

import io.fabric8.jolokia.assertions.Assertions;
import no.rutebanken.marduk.services.RestUploadService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;

import static org.junit.Assert.*;

public class RestUploadServiceTest {

    private Path workingDir;

    @Before
    public void init() {
        this.workingDir = Paths.get("src/test/resources");
    }


    private RestUploadService restUploadService = new RestUploadService();

//    private static final String REST_IMPORT_URL = "https://iboo-preprod.enroute.mobi/api/v1/workbenches/60/imports.json";
    private static final String REST_IMPORT_URL = "https://jsonplaceholder.typicode.com/posts";

    @Test
    public void restExportUpload() {
        String authStr = "60:2c6f2c6b7aeba7f6f4d9dc667f0c58aa";
        String base64Creds = Base64.getEncoder().encodeToString(authStr.getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
//        headers.add("Authorization", "Basic " + base64Creds);
        headers.add("Authorization", "Bearer 2c6f2c6b7aeba7f6f4d9dc667f0c58aa");

        LinkedMultiValueMap<String, Object> body =  new LinkedMultiValueMap();
        body.add("workbench_import[name]", "Testupload-export-file");
        body.add("workbench_import[file]", new FileSystemResource(new File("/tmp/export_gtfs_371.zip")));

        RestTemplate rest = new RestTemplate();
        HttpEntity<Map> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> uploadResp = rest.postForEntity(REST_IMPORT_URL, request, Map.class);

        assertNotNull(uploadResp);
    }

    @Test
    public void restStreamUpload() throws Exception {
        HttpStatus httpStatus = restUploadService.uploadStream(new FileInputStream(this.workingDir.resolve("NRI 20160219.rar").toFile()), REST_IMPORT_URL, "Testupload-export-file.zip", null);
        Assertions.assertThat(httpStatus.is2xxSuccessful()).isTrue();
    }
}
