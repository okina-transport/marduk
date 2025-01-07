package no.rutebanken.marduk.rest;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import no.rutebanken.marduk.services.RestUploadService;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.assertNotNull;

public class RestUploadServiceTest {

    private static final String REST_IMPORT_URL = "http://127.0.0.1:%d/upload";

    private static final String SECRET = "secret";

    private final RestUploadService restUploadService = new RestUploadService();

    @Rule
    public WireMockRule wireMockRuleUploadRandomPort = new WireMockRule(0);

    private Path workingDir;

    @Before
    public void init() {
        this.workingDir = Paths.get("src/test/resources");
    }

    @Test
    @Ignore("No marduk application code under test ?")
    public void restExportUpload() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
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
        String uploadUrl = String.format(REST_IMPORT_URL, wireMockRuleUploadRandomPort.port());
        stubFor(post(urlEqualTo("/upload"))
                .withHeader("Content-Type", containing("multipart/form-data"))
                .withHeader("Authorization", equalTo("Bearer secret"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));
        HttpStatus httpStatus = restUploadService.uploadStream(new FileInputStream(this.workingDir.resolve("NRI 20160219.rar").toFile()), uploadUrl, "Testupload-export-file.zip", null, SECRET);
        Assertions.assertThat(httpStatus.is2xxSuccessful()).isTrue();
    }
}
