package no.rutebanken.marduk.rest;

import no.rutebanken.marduk.services.RestUploadService;
import org.junit.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.Map;

import static org.junit.Assert.*;

public class RestUploadServiceTest {

    private RestUploadService restUploadService = new RestUploadService();

    private static final String REST_IMPORT_URL = "https://iboo-preprod.enroute.mobi/api/v1/workbenches/60/imports.json";

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
        InputStream stream = new FileInputStream(new File("/tmp/export_gtfs_371.zip"));
        boolean uploaded = restUploadService.uploadStream(stream, REST_IMPORT_URL, "Testupload-export-file.zip", "60", "2c6f2c6b7aeba7f6f4d9dc667f0c58aa");
        assertTrue(uploaded);
    }
}
