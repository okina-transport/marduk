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


import com.vividsolutions.jts.geom.CoordinateList;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import net.opengis.gml._3.PolygonType;
import org.rutebanken.netex.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

public class TopographicPlaceMapper {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private String participantRef;

    private TopographicPlaceAdapter feature;

    public TopographicPlaceMapper(TopographicPlaceAdapter adapter, String participantRef) {
        this.feature = adapter;
        this.participantRef = participantRef;
    }


    public TopographicPlace toTopographicPlace() {
        if (!feature.isValid()) {
            return null;
        }
        return new TopographicPlace()
                       .withVersion("any").withModification(ModificationEnumeration.NEW)
                       .withName(multilingualString(feature.getName()))
                       .withAlternativeDescriptors(getAlternativeDescriptors())
                       .withDescriptor(new TopographicPlaceDescriptor_VersionedChildStructure().withName(multilingualString(feature.getName())))
                       .withTopographicPlaceType(getType())
                       .withPolygon(getPolygon())
                       .withIsoCode(feature.getIsoCode())
                       .withCountryRef(new CountryRef().withRef(mapCountryRef(feature.getCountryRef())))
                       .withId(prefix(feature.getId()))
                       .withParentTopographicPlaceRef(toParentRef(feature.getParentId()));
    }


    protected TopographicPlace_VersionStructure.AlternativeDescriptors getAlternativeDescriptors() {
        List<TopographicPlaceDescriptor_VersionedChildStructure> alternativeNames = new ArrayList<>();
        feature.getAlternativeNames().forEach((k, v) -> alternativeNames.add(
                new TopographicPlaceDescriptor_VersionedChildStructure().withName(new MultilingualString().withLang(k).withValue(v))));

        if (CollectionUtils.isEmpty(alternativeNames)) {
            return null;
        }

        return new TopographicPlace_VersionStructure.AlternativeDescriptors().withTopographicPlaceDescriptor(alternativeNames);
    }

    protected String prefix(String id) {
        return participantRef + ":TopographicPlace:" + id;
    }

    protected TopographicPlaceRefStructure toParentRef(String id) {
        if (id == null) {
            return null;
        }
        return new TopographicPlaceRefStructure()
                       .withRef(prefix(feature.getParentId()));
    }

    protected TopographicPlaceTypeEnumeration getType() {
        switch (feature.getType()) {
            case COUNTRY:
                return TopographicPlaceTypeEnumeration.COUNTRY;
            case COUNTY:
                return TopographicPlaceTypeEnumeration.COUNTY;
            case LOCALITY:
                return TopographicPlaceTypeEnumeration.MUNICIPALITY;
            case BOROUGH:
                return TopographicPlaceTypeEnumeration.AREA;
        }
        return null;
    }


    private PolygonType getPolygon() {
        Geometry geometry = feature.getDefaultGeometry();

        if (geometry instanceof MultiPolygon) {
            CoordinateList coordinateList = new CoordinateList(geometry.getBoundary().getCoordinates());
            coordinateList.closeRing();
            geometry = geometry.getFactory().createPolygon(coordinateList.toCoordinateArray());
        }

        if (geometry instanceof Polygon) {
            return NetexGeoUtil.toNetexPolygon((Polygon) geometry).withId(participantRef + "-" + feature.getId());
        }
        return null;
    }


    protected IanaCountryTldEnumeration mapCountryRef(String countryRef) {
        if (countryRef == null) {
            return null;
        }
        return IanaCountryTldEnumeration.fromValue(countryRef.toLowerCase());
    }


    protected MultilingualString multilingualString(String val) {
        return new MultilingualString().withLang("no").withValue(val);
    }

}


