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

package no.rutebanken.marduk.routes.nri;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.services.IdempotentRepositoryService;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.DirectoryEntry;
import org.mockftpserver.fake.filesystem.FileEntry;
import org.mockftpserver.fake.filesystem.FileSystem;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

import static no.rutebanken.marduk.utils.constants.RouteDeclarationConstants.ROUTE_IMPORT_LAUNCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class NRIFtpReceiverRouteTest extends MardukRouteBuilderIntegrationTestBase {

    @Value("${nri.ftp.file.age.filter.months:3}")
    private int fileAgeFilterMonths;

    @Autowired
    private DataSource dataSource;

    @Autowired
    IdempotentRepositoryService idempotentRepositoryService;

    @EndpointInject("mock:processFileMock")
    protected MockEndpoint processFileMock;

    @BeforeEach()
    void beforeEach() throws SQLException {
        when(providerRepository.getProviders()).thenReturn(providers);
        when(providerRepository.getProvider(2L)).thenReturn(providers.get(0));
        when(providerRepository.getProvider(3L)).thenReturn(providers.get(1));
        ScriptUtils.executeSqlScript(dataSource.getConnection(), new ClassPathResource("schema.sql"));
    }

    void setUp(boolean autoImport, long id) throws IOException {
        //wipe idempotent stores
        idempotentRepositoryService.cleanUniqueFileNameAndDigestRepo();
        Provider provider = Provider.create(IOUtils.toString(
                new FileReader("src/test/resources/no/rutebanken/marduk/providerRepository/provider2.json")));
        provider.chouetteInfo.enableAutoImport = autoImport;
        when(providerRepository.getProvider(id)).thenReturn(provider);

        processFileMock.reset();
    }

    @Test
    void testFetchFilesFromFTPWithAutoImport() throws Exception {
        setUp(true,4);
        testFetchFilesFromFTP(true,"Jotunheimen og Valdresruten Bilselskap");
    }

    @Test
    void testFetchFilesFromFTPWithoutAutoImport() throws Exception {
        setUp(false,5);
        testFetchFilesFromFTP(false,"Brakar (Buskerud fylke)");
    }


    public void testFetchFilesFromFTP(boolean autoImport, String providerFolder) throws Exception {

        FakeFtpServer fakeFtpServer = new FakeFtpServer();
        fakeFtpServer.setSystemName("UNIX");

        fakeFtpServer.addUserAccount(new UserAccount("username", "password", "/"));

        FileSystem fileSystem = new UnixFakeFileSystem();
        fileSystem.add(new DirectoryEntry("/rutedata"));
        fileSystem.add(new DirectoryEntry("/rutedata/" + providerFolder));

        fileSystem.add(new DirectoryEntry("/rutedata/" + providerFolder + "/1585"));
        fileSystem.add(new DirectoryEntry("/rutedata/" + providerFolder + "/1585/Hovedsett 2016_unzipped"));
        fileSystem.add(new DirectoryEntry("/rutedata/" + providerFolder + "/985"));
        fileSystem.add(new DirectoryEntry("/rutedata/" + providerFolder + "/984"));
        fileSystem.add(new DirectoryEntry("/rutedata/" + providerFolder + "/985/Hovedsett 2016_unzipped"));
        FileEntry file1585 = new FileEntry("/rutedata/" + providerFolder + "/1585/Hovedsett 2017.zip");
        file1585.setLastModified(DateUtils.addMonths(new Date(), -(fileAgeFilterMonths - 1)));
        file1585.setContents(new byte[]{12, 11, 10});//IOUtils.toByteArray(getClass().getResourceAsStream("/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip")));
        fileSystem.add(file1585);
        FileEntry file984 = new FileEntry("/rutedata/" + providerFolder + "/984/Hovedsett 2016.zip");
        file984.setContents(new byte[]{11, 10, 12});//IOUtils.toByteArray(getClass().getResourceAsStream("/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip")));
        fileSystem.add(file984);
        FileEntry file985_1 = new FileEntry("/rutedata/" + providerFolder + "/985/Hovedsett 2016_v2.zip");
        file985_1.setContents(new byte[]{10, 11, 12});//IOUtils.toByteArray(getClass().getResourceAsStream("/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip")));
        fileSystem.add(file985_1);
        //idempotent filter should take care of this, since content is the same as above
        FileEntry file985_2 = new FileEntry("/rutedata/" + providerFolder + "/985/ShouldBeFiltered.zip");
        file985_2.setContents(new byte[]{10, 11, 12});//IOUtils.toByteArray(getClass().getResourceAsStream("/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip")));
        fileSystem.add(file985_2);

        FileEntry toOld = new FileEntry("/rutedata/" + providerFolder + "/985/TooOldShouldBeFiltered.zip");
        toOld.setContents(new byte[]{10, 11, 12, 13});
        toOld.setLastModified(DateUtils.addMonths(new Date(), -(fileAgeFilterMonths + 1)));
        fileSystem.add(toOld);


        fakeFtpServer.setFileSystem(fileSystem);
        fakeFtpServer.setServerControlPort(32220);

        fakeFtpServer.start();

        AdviceWith.adviceWith(context, "nri-ftp-activemq", a -> {
            a.interceptSendToEndpoint(ROUTE_IMPORT_LAUNCH).skipSendToOriginalEndpoint()
                .to("mock:processFileMock");
        });

        context.start();


        // setup expectations on the mocks
        processFileMock.expectedMessageCount(autoImport? 3 : 0);

        // assert that the test was okay
        processFileMock.assertIsSatisfied();

        if (autoImport) {
            List<Exchange> exchanges = processFileMock.getExchanges();
            assertThat(exchanges.getFirst().getIn().getHeader(Constants.PROVIDER_ID)).isEqualTo(2L);
            assertThat(exchanges.getFirst().getIn().getHeader(Constants.CORRELATION_ID)).isNotNull();
            assertThat(exchanges.getFirst().getIn().getHeader(Constants.FILE_NAME)).isEqualTo("984_Hovedsett_2016.zip");
            assertThat(exchanges.get(1).getIn().getHeader(Constants.FILE_NAME)).isEqualTo("985_Hovedsett_2016_v2.zip");
            assertThat(exchanges.get(2).getIn().getHeader(Constants.FILE_NAME)).isEqualTo("1585_Hovedsett_2017.zip");
        }

        fakeFtpServer.stop();
    }

}