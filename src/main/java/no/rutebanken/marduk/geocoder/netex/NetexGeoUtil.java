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

package no.rutebanken.marduk.geocoder.netex;


import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Polygon;
import net.opengis.gml._3.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class NetexGeoUtil {


    public static final String SRS_NAME_WGS84 = "ESPG:4326";

    public static PolygonType toNetexPolygon(Polygon polygon) {
        LinearRingType linearRing = new LinearRingType();

        List<Double> values = new ArrayList<>();
        for (Coordinate coordinate : polygon.getExteriorRing().getCoordinates()) {
            values.add(coordinate.y); // lat
            values.add(coordinate.x); // lon
        }

        // Ignoring interior rings because the corresponding exclaves are not handled.

        DirectPositionListType positionList = new DirectPositionListType().withValue(values);
        linearRing.withPosList(positionList);

        return new PolygonType().withSrsDimension(BigInteger.valueOf(2)).withSrsName(SRS_NAME_WGS84)
                       .withExterior(new AbstractRingPropertyType().withAbstractRing(
                               new net.opengis.gml._3.ObjectFactory().createLinearRing(linearRing)));
    }
}
