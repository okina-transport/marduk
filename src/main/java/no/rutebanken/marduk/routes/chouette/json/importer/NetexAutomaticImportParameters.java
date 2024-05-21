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

package no.rutebanken.marduk.routes.chouette.json.importer;

import no.rutebanken.marduk.routes.chouette.json.ChouetteJobParameters;

public class NetexAutomaticImportParameters extends ChouetteJobParameters {

    public Parameters parameters;

    static class Parameters extends AbstractImportParameters  {

    }

    public static NetexAutomaticImportParameters create(RawImportParameters rawImportParameters) {
        Parameters parameters = new Parameters();
        parameters.name = rawImportParameters.getFileName();
        parameters.userName = rawImportParameters.getUser();

        NetexAutomaticImportParameters netexAutomaticImportParameters = new NetexAutomaticImportParameters();
        netexAutomaticImportParameters.parameters = parameters;

        return netexAutomaticImportParameters;
    }

}
