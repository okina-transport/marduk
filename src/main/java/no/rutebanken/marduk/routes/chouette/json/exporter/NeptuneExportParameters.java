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


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.IdFormat;

import java.util.Date;

public class NeptuneExportParameters {

    public Parameters parameters;

    public NeptuneExportParameters(Parameters parameters) {
        this.parameters = parameters;
    }

    public static class Parameters {

        @JsonProperty("neptune-export")
        public NeptuneExport neptuneExport;

        public Parameters(NeptuneExport neptuneExport) {
            this.neptuneExport = neptuneExport;
        }

    }

    public static class NeptuneExport extends AbstractExportParameters {

        @JsonProperty("object_id_prefix")
        public String objectIdPrefix;

        @JsonProperty("exported_filename")
        public String exportedFilename;


        public NeptuneExport(String name, String objectIdPrefix, String referentialName, String organisationName, String userName,  Date startDate, Date endDate, String exportedFilename) {
            this.name = name;
            this.objectIdPrefix = objectIdPrefix;
            this.referentialName = referentialName;
            this.organisationName = organisationName;
            this.userName = userName;
            this.startDate = (startDate != null) ? startDate : DateUtils.startDateFor(2L);
            this.endDate = (endDate != null) ? endDate : DateUtils.endDateFor(365);
            this.exportedFilename = exportedFilename;

        }


    }


}
