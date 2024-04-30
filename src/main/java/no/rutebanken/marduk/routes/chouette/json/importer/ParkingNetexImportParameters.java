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

public class ParkingNetexImportParameters extends ChouetteJobParameters {

    public Parking parameters;

    static class Parking extends AbstractImportParameters  {

    }

    public static ParkingNetexImportParameters create(RawImportParameters rawImportParameters) {
        Parking parking = new Parking();
        parking.name = rawImportParameters.getFileName();
        parking.userName = rawImportParameters.getUser();

        ParkingNetexImportParameters parkingNetexImportParameters = new ParkingNetexImportParameters();
        parkingNetexImportParameters.parameters = parking;

        return parkingNetexImportParameters;
    }

}
