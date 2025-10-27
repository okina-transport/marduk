/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.domain.ImportGenerateMapMatching;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.BlobStoreRepository;
import no.rutebanken.marduk.utils.Utils;
import org.apache.camel.*;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static no.rutebanken.marduk.Constants.GENERATE_MAP_MATCHING;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.utils.Utils.parseProviderFromFileName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class AdminRestMardukRouteBuilderIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    @Autowired
    private BlobStoreRepository blobStoreRepository;

    @EndpointInject("mock:chouetteImportQueue")
    protected MockEndpoint importQueue;

    @EndpointInject("mock:chouetteExportNetexQueue")
    protected MockEndpoint exportQueue;

    @EndpointInject("mock:importLaunch")
    protected MockEndpoint importLaunch;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/2/import")
    protected ProducerTemplate importTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/2/export")
    protected ProducerTemplate exportTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/2/files")
    protected ProducerTemplate listFilesTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/2/files/existing_regtopp-file.zip")
    protected ProducerTemplate getFileTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/2/files/unknown-file.zip")
    protected ProducerTemplate getUnknownFileTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/export/files")
    protected ProducerTemplate listExportFilesTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/export/files/3")
    protected ProducerTemplate listExportFilesProviderTemplate;

    @Value("#{'${timetable.export.blob.prefixes:outbound/gtfs/,outbound/netex/}'.split(',')}")
    private List<String> exportFileStaticPrefixes;
    @Autowired
    private AdminRestRouteBuilder adminRestRouteBuilder;

    @BeforeEach
    void setUpProvider() {
        when(providerRepository.getProviders()).thenReturn(providers);
        when(providerRepository.getProvider(2L)).thenReturn(providers.get(0));
        when(providerRepository.getProvider(3L)).thenReturn(providers.get(1));
        when(providerRepository.getReferential(2L)).thenReturn("rut");
    }

    @Test
    void runImport() throws Exception {

        AdviceWith.adviceWith(context, "admin-chouette-import", adviceRouteBuilder -> {
            adviceRouteBuilder.weaveByToUri("jms:(.*):ProcessFileQueue")
                    .replace().to("mock:chouetteImportQueue");
        });

        // we must manually start when we are done with all the advice with
        context.start();

        BlobStoreFiles d = new BlobStoreFiles();
        d.add(new BlobStoreFiles.File("file1", null, null, null));
        d.add(new BlobStoreFiles.File("file2", null, null, null));

        ObjectMapper mapper = new ObjectMapper();
        StringWriter writer = new StringWriter();
        mapper.writeValue(writer, d);
        String importJson = writer.toString();

        // Do rest call

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Exchange.HTTP_METHOD, "POST");
        importTemplate.sendBodyAndHeaders(importJson, headers);

        // setup expectations on the mocks
        importQueue.expectedMessageCount(2);

        // assert that the test was okay
        importQueue.assertIsSatisfied();

        List<Exchange> exchanges = importQueue.getExchanges();
        String providerId = (String) exchanges.getFirst().getIn().getHeader(PROVIDER_ID);
        assertThat(providerId).isEqualTo("2");
    }

    @Test
    void runExport() throws Exception {
        AdviceWith.adviceWith(context, "admin-chouette-export", adviceRouteBuilder -> {
            adviceRouteBuilder.weaveByToUri("jms:(.*):ChouetteExportNetexQueue")
                    .replace().to("mock:chouetteExportNetexQueue");
        });

        // we must manually start when we are done with all the advice with
        context.start();

        // Do rest call
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Exchange.HTTP_METHOD, "POST");
        exportTemplate.sendBodyAndHeaders(null, headers);

        // setup expectations on the mocks
        exportQueue.expectedMessageCount(1);

        // assert that the test was okay
        exportQueue.assertIsSatisfied();

        List<Exchange> exchanges = exportQueue.getExchanges();
        String providerId = (String) exchanges.getFirst().getIn().getHeader(PROVIDER_ID);
        assertThat(providerId).isEqualTo("2");
    }

    @Test
    void getBlobStoreFiles() throws Exception {

        // Preparations
        String filename = "ruter_fake_data.zip";
        String fileStorePath = "5/imports/";
        String pathname = "src/test/resources/no/rutebanken/marduk/routes/file/beans/gtfs.zip";

        //populate fake blob repo
        blobStoreRepository.uploadBlob(fileStorePath + filename, new FileInputStream(pathname), false);

        context.start();

        // Do rest call
        Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.HTTP_METHOD, "GET");
        InputStream response = (InputStream) listFilesTemplate.requestBodyAndHeaders(null, headers);
        // Parse response

        String s = new String(IOUtils.toByteArray(response));

        ObjectMapper mapper = new ObjectMapper();
        BlobStoreFiles rsp = mapper.readValue(s, BlobStoreFiles.class);
        assertThat(rsp.getFiles()).hasSize(1);
        assertThat(rsp.getFiles().getFirst().getName()).isEqualTo(fileStorePath + filename);

    }


    @Test
    void getBlobStoreFile() throws Exception {
        // Preparations
        String filename = "existing_regtopp-file.zip";
        String fileStorePath = Constants.BLOBSTORE_PATH_INBOUND + "rut/";
        String pathname = "src/test/resources/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip";
        FileInputStream testFileStream = new FileInputStream(new File(pathname));
        //populate fake blob repo
        blobStoreRepository.uploadBlob(fileStorePath + filename, testFileStream, false);


        context.start();

        // Do rest call
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Exchange.HTTP_METHOD, "GET");
        InputStream response = (InputStream) getFileTemplate.requestBodyAndHeaders(null, headers);
        // Parse response
        Assertions.assertNotNull(response);
    }


    @Test
    void getBlobStoreFile_unknownFile() {
        context.start();
        assertThrows(CamelExecutionException.class, () -> {

            // Do rest call
            Map<String, Object> headers = new HashMap<String, Object>();
            headers.put(Exchange.HTTP_METHOD, "GET");
            getUnknownFileTemplate.requestBodyAndHeaders(null, headers);
        });

    }


    @Test
    void getBlobStoreExportFiles() throws Exception {
        String testFileName = "rut-testFile";
        //populate fake blob repo
        for (String prefix : exportFileStaticPrefixes) {
            blobStoreRepository.uploadBlob(prefix + testFileName, new FileInputStream(new File("src/test/resources/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip")), false);
        }
        context.start();

        // Do rest call
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Exchange.HTTP_METHOD, "GET");
        InputStream response = (InputStream) listExportFilesTemplate.requestBodyAndHeaders(null, headers);
        // Parse response

        String s = new String(IOUtils.toByteArray(response));

        ObjectMapper mapper = new ObjectMapper();
        BlobStoreFiles rsp = mapper.readValue(s, BlobStoreFiles.class);
        assertThat(exportFileStaticPrefixes).hasSameSizeAs(rsp.getFiles());
        exportFileStaticPrefixes.forEach(prefix -> rsp.getFiles().stream().anyMatch(file -> (prefix + testFileName).equals(file.getName())));

        Provider provider = parseProviderFromFileName(providerRepository, testFileName);
        assertThat(provider.chouetteInfo.referential).isEqualTo("rut");
        assertThat(provider.id).isEqualTo(2L);

        // Clean up files for further tests
        for (String prefix : exportFileStaticPrefixes) {
            blobStoreRepository.delete(prefix + testFileName);
        }
    }


    @Test
    void getBlobStoreExportFilesForProvider() throws Exception {
        String testFileName = "rut3-testFile";
        //populate fake blob repo
        for (String prefix : exportFileStaticPrefixes) {
            blobStoreRepository.uploadBlob(prefix + testFileName, new FileInputStream(new File("src/test/resources/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip")), false);
        }
        context.start();

        // Do rest call
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Exchange.HTTP_METHOD, "GET");
        headers.put(Constants.PROVIDER_ID, 3);
        InputStream response = (InputStream) listExportFilesProviderTemplate.requestBodyAndHeaders(null, headers);
        // Parse response

        String s = new String(IOUtils.toByteArray(response));

        ObjectMapper mapper = new ObjectMapper();
        BlobStoreFiles rsp = mapper.readValue(s, BlobStoreFiles.class);
        assertThat(exportFileStaticPrefixes).hasSameSizeAs(rsp.getFiles());
        exportFileStaticPrefixes.forEach(prefix -> rsp.getFiles().stream().anyMatch(file -> (prefix + testFileName).equals(file.getName())));

        rsp.getFiles().forEach(f -> {
            Provider provider = Utils.parseProviderFromFileName(providerRepository, f.getFileNameOnly());
            assertThat(provider.chouetteInfo.referential).isEqualTo("rut3");
            assertThat(provider.id).isEqualTo(3L);
        });

        // Clean up files for further tests
        for (String prefix : exportFileStaticPrefixes) {
            blobStoreRepository.delete(prefix + testFileName);
        }
    }

    @Test
    void getGenerateMapMatchingHeadersNoHeaderValueFoundTest() {
        Exchange build = ExchangeBuilder.anExchange(context).withHeader("JOB_ID", "1").build();

        String result = adminRestRouteBuilder.getGenerateMapMatchingHeaders(build);

        assertThat(result).isEqualTo(ImportGenerateMapMatching.NONE.name());
    }

    @Test
    void getGenerateMapMatchingHeadersHeaderValueFoundTest() {
        Exchange build = ExchangeBuilder.anExchange(context).withHeader("JOB_ID", "1").withHeader(GENERATE_MAP_MATCHING, "CAR").build();

        String result = adminRestRouteBuilder.getGenerateMapMatchingHeaders(build);

        assertThat(result).isEqualTo(ImportGenerateMapMatching.CAR.name());
    }
}
