package no.rutebanken.marduk.geocoder.geojson;


import no.rutebanken.marduk.geocoder.netex.TopographicPlaceAdapter;
import org.apache.commons.lang3.StringUtils;
import org.opengis.feature.simple.SimpleFeature;

public abstract class AbstractKartverketGeojsonAdapter extends AbstractGeojsonAdapter implements TopographicPlaceAdapter {

	public String getIsoCode() {
		return null;
	}

	public String getParentId() {
		return null;
	}

	public String getName() {
		return getProperty("navn");
	}

	public AbstractKartverketGeojsonAdapter(SimpleFeature feature) {
		super(feature);
	}


	protected String pad(long val, int length) {
		return StringUtils.leftPad("" + val, length, "0");
	}

}
