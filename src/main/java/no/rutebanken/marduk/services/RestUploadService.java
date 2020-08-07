package no.rutebanken.marduk.services;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Map;

@Component
public class RestUploadService {

    private static final String REST_IMPORT_URL_PATTERN = "https://iboo-preprod.enroute.mobi/api/v1/workbenches/%s/imports.json";

    public boolean uploadStream(InputStream stream, String filename, String key, String secret) {
//        String authStr = "60:2c6f2c6b7aeba7f6f4d9dc667f0c58aa";
//        String base64Creds = Base64.getEncoder().encodeToString(authStr.getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
//        headers.add("Authorization", "Basic " + base64Creds);
        headers.add("Authorization", "Bearer " + secret);

        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap();
        body.add("workbench_import[name]", filename);
        body.add("workbench_import[file]", new MultipartFileResource(stream, filename));
//        body.add("workbench_import[file]", new FileSystemResource(new File("/tmp/export_gtfs_371.zip")));

        RestTemplate rest = new RestTemplate();
        HttpEntity<Map> request = new HttpEntity<>(body, headers);
        String uploadUrl = String.format(REST_IMPORT_URL_PATTERN, key);
        ResponseEntity<Map> uploadResp = rest.postForEntity(uploadUrl, request, Map.class);
        return HttpStatus.CREATED.equals(uploadResp.getStatusCode());
    }

    /**
     * Useful to upload directly InputStream rather than FileSystemResource
     */
    private class MultipartFileResource extends InputStreamResource {

        private String filename;

        public MultipartFileResource(InputStream inputStream, String filename) {
            super(inputStream);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return this.filename;
        }

        @Override
        public long contentLength() throws IOException {
            return -1; // we do not want to generally read the whole stream into memory ...
        }
    }
}
