package no.rutebanken.marduk.services;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Component
public class RestUploadService {


    public HttpStatus uploadStream(InputStream stream, String restUrl, String filename, String key, String secret) throws IOException {
        // on clone l'input stream, car celui qui sera envoyé au rest template Sprint sera fermé => pose problème
        // plus tard dans la route lorsque Camel essaie de le relire.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = stream.read(buffer)) > -1 ) {
            baos.write(buffer, 0, len);
        }
        baos.flush();
        InputStream inputStreamToSend = new ByteArrayInputStream(baos.toByteArray());


        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.add("Authorization", "Bearer " + secret);

        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("workbench_import[name]", filename);
        body.add("workbench_import[file]", new MultipartFileResource(inputStreamToSend, filename));

        RestTemplate rest = new RestTemplate();
        HttpEntity<Map> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> uploadResp = rest.postForEntity(restUrl, request, Map.class);
        return uploadResp.getStatusCode();
    }


    /**
     * Useful to upload directly InputStream rather than file based FileSystemResource
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
