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

package no.rutebanken.marduk.geocoder.routes.pelias.mapper.kartverket;

import no.rutebanken.marduk.geocoder.routes.pelias.json.AddressParts;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Create "street" documents for Pelias from addresses.
 *
 * Streets are assumed to be contained fully in a single locality (kommune) and the names for streets are assumed to be unique within a single locality.
 *
 * Centerpoint and parent info for street is fetched from the median address (ordered by number + alpha) in the street.
 *
 * NB! Streets are stored in the "address" layer in pelias, as this is prioritized
 *
 */
@Service
public class AddressToStreetMapper {


    private final long popularity;

    public AddressToStreetMapper(@Value("${pelias.address.street.boost:2}") long popularity) {
        this.popularity = popularity;
    }

    public List<PeliasDocument> createStreetPeliasDocumentsFromAddresses(Collection<PeliasDocument> addresses) {
        Collection<List<PeliasDocument>> addressesPerStreet =
                addresses.stream().filter(a -> a.getAddressParts()!=null && !StringUtils.isEmpty(a.getAddressParts().getStreet()))
                        .collect(Collectors.groupingBy(a -> fromAddress(a), Collectors.mapping(Function.identity(), Collectors.toList()))).values();
        return addressesPerStreet.stream().map(addressesOnStreet -> createPeliasStreetDocFromAddresses(addressesOnStreet)).collect(Collectors.toList());
    }


    private PeliasDocument createPeliasStreetDocFromAddresses(List<PeliasDocument> addressesOnStreet) {
        PeliasDocument templateAddress = getAddressRepresentingStreet(addressesOnStreet);

        String streetName = templateAddress.getAddressParts().getStreet();
        String uniqueId = templateAddress.getParent().getLocalityId() + "-" + streetName;
        PeliasDocument streetDocument = new PeliasDocument("address", uniqueId);

        streetDocument.setDefaultNameAndPhrase(streetName);
        streetDocument.setParent(templateAddress.getParent());

        streetDocument.setCenterPoint(templateAddress.getCenterPoint());
        AddressParts addressParts = new AddressParts();
        addressParts.setName(streetName);
        addressParts.setStreet(streetName);
        streetDocument.setAddressParts(addressParts);

        streetDocument.setPopularity(popularity);

        return streetDocument;
    }

    /**
     * Use median address in street (ordered by number + alpha) as representative of the street.

     */
    private PeliasDocument getAddressRepresentingStreet(List<PeliasDocument> addressesOnStreet) {
        Collections.sort(addressesOnStreet,
                (o1, o2) -> o1.getAddressParts().getNumber().compareTo(o2.getAddressParts().getNumber()));

        return addressesOnStreet.get(addressesOnStreet.size() / 2);
    }


    private UniqueStreetKey fromAddress(PeliasDocument address) {

        return new UniqueStreetKey(address.getAddressParts().getStreet(), address.getParent().getLocalityId());
    }

    private class UniqueStreetKey {

        private String streetName;

        private String localityId;

        public UniqueStreetKey(String streetName, String localityId) {
            this.streetName = streetName;
            this.localityId = localityId;
        }


        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            UniqueStreetKey that = (UniqueStreetKey) o;

            if (streetName != null ? !streetName.equals(that.streetName) : that.streetName != null) return false;
            return localityId != null ? localityId.equals(that.localityId) : that.localityId == null;
        }

        @Override
        public int hashCode() {
            int result = streetName != null ? streetName.hashCode() : 0;
            result = 31 * result + (localityId != null ? localityId.hashCode() : 0);
            return result;
        }
    }
}
