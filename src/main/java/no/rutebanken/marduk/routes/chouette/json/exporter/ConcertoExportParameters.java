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

package no.rutebanken.marduk.routes.chouette.json.exporter;


import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

public class ConcertoExportParameters {

    public Parameters parameters;

    public ConcertoExportParameters(Parameters parameters) {
        this.parameters = parameters;
    }

    public static class Parameters {

        @JsonProperty("concerto-export")
        public ConcertoExport concertoExport;

        public Parameters(ConcertoExport concertoExport) {
            this.concertoExport = concertoExport;
        }

    }

    public static class ConcertoExport extends AbstractExportParameters {

        @JsonProperty("object_type_concerto")
        public String objectTypeConcerto;

        public ConcertoExport(String name, String referentialName, String organisationName, String userName, Date startDate, Date endDate, String objectTypeConcerto) {
            this.name = name;
            this.referentialName = referentialName;
            this.organisationName = organisationName;
            this.userName = userName;
            this.startDate = (startDate != null) ? startDate : DateUtils.startDateFor(2L);
            this.endDate = (endDate != null) ? endDate : DateUtils.endDateFor(365);
            this.objectTypeConcerto = objectTypeConcerto;
        }

    }


}
