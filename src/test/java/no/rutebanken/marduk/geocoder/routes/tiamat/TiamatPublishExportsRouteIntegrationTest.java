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

package no.rutebanken.marduk.geocoder.routes.tiamat;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.geocoder.routes.control.GeoCoderTaskType;
import no.rutebanken.marduk.geocoder.routes.tiamat.model.TiamatExportTask;
import no.rutebanken.marduk.geocoder.routes.tiamat.model.TiamatExportTaskType;
import no.rutebanken.marduk.geocoder.routes.tiamat.model.TiamatExportTasks;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

import static no.rutebanken.marduk.Constants.LOOP_COUNTER;
import static no.rutebanken.marduk.geocoder.GeoCoderConstants.GEOCODER_RESCHEDULE_TASK;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TiamatPublishExportsRouteBuilder.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
public class TiamatPublishExportsRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {


    @Autowired
    private ModelCamelContext context;

    @Value("${tiamat.max.retries:3000}")
    private int maxRetries;

    @Value("${tiamat.retry.delay:15000}")
    private long retryDelay;

    @EndpointInject(uri = "mock:updateStatus")
    protected MockEndpoint statusQueueMock;


    @EndpointInject(uri = "mock:tiamatExport")
    protected MockEndpoint tiamatStartExportMock;

    @EndpointInject(uri = "mock:tiamatPollJobStatus")
    protected MockEndpoint tiamatPollMock;

    @EndpointInject(uri = "mock:TiamatExportQueue")
    protected MockEndpoint rescheduleMock;

    @EndpointInject(uri = "mock:changeLogExportMock")
    protected MockEndpoint changeLogExportMock;


    @Produce(uri = "activemq:queue:TiamatExportQueue")
    protected ProducerTemplate input;

    @Before
    public void setUp() {
        tiamatStartExportMock.reset();
        rescheduleMock.reset();
        statusQueueMock.reset();
        tiamatPollMock.reset();
        try {

            replaceEndpoint("tiamat-publish-export-poll-status", "direct:tiamatPollJobStatus", "mock:tiamatPollJobStatus");
            replaceEndpoint("tiamat-publish-export-start-new", "direct:tiamatExport", "mock:tiamatExport");

            replaceEndpoint("tiamat-publish-export", "activemq:TiamatExportQueue", "mock:TiamatExportQueue");
            replaceEndpoint("tiamat-publish-export-start-new", "direct:updateStatus", "mock:updateStatus");
            replaceEndpoint("tiamat-publish-export-poll-status", "direct:updateStatus", "mock:updateStatus");

            replaceEndpoint("tiamat-publish-export-start-new", "direct:processTiamatChangeLogExportTask", "mock:changeLogExportMock");

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void newExportIsStarted() throws Exception {
        tiamatStartExportMock.expectedMessageCount(1);
        statusQueueMock.expectedMessageCount(1);
        rescheduleMock.expectedMessageCount(1);
        input.sendBody(new TiamatExportTasks(new TiamatExportTask("TaskName", "?query=something")).toString());

        tiamatStartExportMock.assertIsSatisfied();
        statusQueueMock.assertIsSatisfied();
        rescheduleMock.assertIsSatisfied();
    }

    @Test
    public void newChangeLogExportCompletedSynchronously() throws Exception {
        tiamatStartExportMock.expectedMessageCount(0);
        rescheduleMock.expectedMessageCount(0);
        changeLogExportMock.expectedMessageCount(1);
        input.sendBody(new TiamatExportTasks(new TiamatExportTask("TaskName", "?query=something", TiamatExportTaskType.CHANGE_LOG)).toString());

        tiamatStartExportMock.assertIsSatisfied();
        changeLogExportMock.assertIsSatisfied();
        rescheduleMock.assertIsSatisfied();
    }

    @Test
    public void incompleteTaskIsRescheduled() throws Exception {
        tiamatPollMock.expectedMessageCount(1);
        statusQueueMock.expectedMessageCount(0);
        rescheduleMock.expectedMessageCount(1);

        tiamatPollMock.whenAnyExchangeReceived(e -> e.setProperty(GEOCODER_RESCHEDULE_TASK, true));

        input.sendBodyAndHeader(new TiamatExportTasks(new TiamatExportTask("TaskName", "?query=something")).toString(), LOOP_COUNTER, 1);

        tiamatPollMock.assertIsSatisfied();
        statusQueueMock.assertIsSatisfied();
        rescheduleMock.assertIsSatisfied();
    }

    @Test
    public void incompleteTaskBeyondRetryLimitIsFailed() throws Exception {
        tiamatPollMock.expectedMessageCount(1);
        statusQueueMock.expectedMessageCount(1);
        rescheduleMock.expectedMessageCount(0);

        tiamatPollMock.whenAnyExchangeReceived(e -> e.setProperty(GEOCODER_RESCHEDULE_TASK, true));

        Map<String, Object> headers = new HashMap<>();
        headers.put(Constants.SYSTEM_STATUS, status().toString());
        headers.put(LOOP_COUNTER, maxRetries);

        input.sendBodyAndHeaders(new TiamatExportTasks(new TiamatExportTask("TaskName", "?query=something")).toString(), headers);

        tiamatPollMock.assertIsSatisfied();
        statusQueueMock.assertIsSatisfied();
        rescheduleMock.assertIsSatisfied();
    }

    @Test
    public void completeTaskGivesRescheduledMessageIfMoreTasksAreWaiting() throws Exception {
        tiamatPollMock.expectedMessageCount(1);
        statusQueueMock.expectedMessageCount(0);
        rescheduleMock.expectedMessageCount(1);

        tiamatPollMock.whenAnyExchangeReceived(e -> e.setProperty(GEOCODER_RESCHEDULE_TASK, false));

        input.sendBodyAndHeader(new TiamatExportTasks(new TiamatExportTask("TaskName", "?query=something"), new TiamatExportTask("AnotherTask", "?anotherQuery=xx")).toString(), LOOP_COUNTER, 1);

        tiamatPollMock.assertIsSatisfied();
        statusQueueMock.assertIsSatisfied();
        rescheduleMock.assertIsSatisfied();
    }

    @Test
    public void completeTaskGivesNoRescheduledMessageIfNoTasksAreWaiting() throws Exception {
        tiamatPollMock.expectedMessageCount(1);
        statusQueueMock.expectedMessageCount(0);
        rescheduleMock.expectedMessageCount(0);

        tiamatPollMock.whenAnyExchangeReceived(e -> e.setProperty(GEOCODER_RESCHEDULE_TASK, false));

        input.sendBodyAndHeader(new TiamatExportTasks(new TiamatExportTask("TaskName", "?query=something")).toString(), LOOP_COUNTER, 1);

        tiamatPollMock.assertIsSatisfied();
        statusQueueMock.assertIsSatisfied();
        rescheduleMock.assertIsSatisfied();
    }


    private JobEvent status() {
        return JobEvent.builder().jobDomain(JobEvent.JobDomain.TIAMAT).action("EXPORT").correlationId("corrId").state(JobEvent.State.STARTED).build();
    }
}
