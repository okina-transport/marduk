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

package no.rutebanken.marduk.routes.status;

import no.rutebanken.marduk.domain.ExportType;
import no.rutebanken.marduk.metrics.PrometheusMetricsService;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.EXPORT_TO_CONSUMER_STATUS;

@Component
public class StatusRouteBuilder extends RouteBuilder {


    @Autowired
    private PrometheusMetricsService metrics;


    @Override
    public void configure() throws Exception {
        from("direct:updateStatus")
                .log(LoggingLevel.INFO, getClass().getName(), "Sending off job status event: ${body}")
                .process(e -> {

                    String body = e.getIn().getBody(String.class);
                    if (StringUtils.isNotEmpty(body)){
                        JobEvent jobEvent = JobEvent.fromString(e.getIn().getBody(String.class));
                        countEvent(jobEvent);
                    }

                })
                .to("activemq:queue:JobEventQueue")
                .routeId("update-status").startupOrder(1);


        from("direct:updateExportToConsumerStatus")
                .process(e -> {
                    String exportToConsumerStatus = (String) e.getIn().getHeader(EXPORT_TO_CONSUMER_STATUS);
                    if (exportToConsumerStatus != null) {
                        if (exportToConsumerStatus.equals("OK")) {
                            JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_TO_CONSUMER).state(JobEvent.State.OK).build();
                        } else {
                            JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_TO_CONSUMER).state(JobEvent.State.FAILED).build();
                        }
                    }
                })
                .choice()
                .when(simple("${header.exportToConsumerStatus} != null"))
                .to("direct:updateStatus")
                .end()
                .routeId("update-export-to-consumer-status");
    }

    private void countEvent(JobEvent jobEvent) {

        // "started" and "pending" states are ignored because we only want to know the result : ok/ko
        if (jobEvent == null || JobEvent.State.PENDING.equals(jobEvent.state) ||  JobEvent.State.STARTED.equals(jobEvent.state)) {
            return;
        }

        if ("EXPORT".equals(jobEvent.action) || "EXPORT_NETEX".equals(jobEvent.action) || "EXPORT_NETEX_MERGED".equals(jobEvent.action)) {
            ExportType exportType;



            if ("EXPORT_NETEX_MERGED".equals(jobEvent.action) || "netex".equals(jobEvent.type.toLowerCase())  ){
                exportType = ExportType.NETEX;
            }else if ("neptune".equals(jobEvent.type.toLowerCase())){
                exportType = ExportType.NEPTUNE;
            }else{
                exportType = ExportType.GTFS;
            }
            metrics.countExports(exportType, jobEvent.state.toString());
        }
    }


}
