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

package no.rutebanken.marduk.geocoder.routes.pelias.mapper;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import no.rutebanken.marduk.geocoder.GeoCoderConstants;
import no.rutebanken.marduk.geocoder.geojson.KartverketLocality;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceAdapter;
import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.rutebanken.marduk.geocoder.routes.pelias.json.GeoPoint;
import no.rutebanken.marduk.geocoder.routes.pelias.json.Parent;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import no.rutebanken.marduk.geocoder.services.AdminUnitRepository;
import org.apache.camel.Body;
import org.apache.camel.ExchangeProperty;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
public class PeliasIndexParentInfoEnricher {

	private GeometryFactory geometryFactory = new GeometryFactory();

	/**
	 * Enrich indexing commands with parent info if missing.
	 */
	public void addMissingParentInfo(@Body Collection<ElasticsearchCommand> commands,
			                                @ExchangeProperty(value = GeoCoderConstants.GEOCODER_ADMIN_UNIT_REPO) AdminUnitRepository adminUnitRepository) {
		commands.forEach(c -> addMissingParentInfo(c, adminUnitRepository));
	}

	void addMissingParentInfo(ElasticsearchCommand command, AdminUnitRepository adminUnitRepository) {
		if (!(command.getSource() instanceof PeliasDocument)) {
			return;
		}
		PeliasDocument peliasDocument = (PeliasDocument) command.getSource();
		addParentIdsByReverseGeoLookup(adminUnitRepository, peliasDocument);
		addAdminUnitNamesByIds(adminUnitRepository, peliasDocument);
	}

	private void addAdminUnitNamesByIds(AdminUnitRepository adminUnitRepository, PeliasDocument peliasDocument) {
		Parent parent = peliasDocument.getParent();
		if (parent != null) {
			if (parent.getCountyId() != null && parent.getCounty() == null) {
				parent.setCounty(adminUnitRepository.getAdminUnitName(parent.getCountyId()));
			}
			if (parent.getLocalityId() != null && parent.getLocality() == null) {
				parent.setLocality(adminUnitRepository.getAdminUnitName(parent.getLocalityId()));
			}
			if (parent.getBoroughId() != null && parent.getBorough() == null) {
				parent.setBorough(adminUnitRepository.getAdminUnitName(parent.getBoroughId()));
			}
		}
	}

	private Parent addParentIdsByReverseGeoLookup(AdminUnitRepository adminUnitRepository, PeliasDocument peliasDocument) {
		Parent parent = peliasDocument.getParent();

		GeoPoint centerPoint = peliasDocument.getCenterPoint();
		if (isLocalityMissing(parent) && centerPoint != null) {
			Point point = geometryFactory.createPoint(new Coordinate(centerPoint.getLon(), centerPoint.getLat()));
			TopographicPlaceAdapter locality = adminUnitRepository.getLocality(point);
			if (locality != null) {
				if (parent == null) {
					parent = new Parent();
					peliasDocument.setParent(parent);
				}
				parent.setLocalityId(locality.getId());
				parent.setCountyId(locality.getParentId());
			}
		}
		return parent;
	}

	private boolean isLocalityMissing(Parent parent) {
		return parent == null || parent.getLocalityId() == null;
	}

}
