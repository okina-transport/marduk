package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.routes.chouette.json.ExportJob;
import org.apache.camel.converter.jaxb.JaxbDataFormat;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

/**
 * ExportJob xml parser
 */
public class ExportJobXmlParser extends JaxbDataFormat {

    private static ExportJobXmlParser singleton;

    public static ExportJobXmlParser newInstance() throws JAXBException {
        if (singleton == null) {
            singleton = new ExportJobXmlParser();
            JAXBContext con = JAXBContext.newInstance(ExportJob.class);
            singleton.setContext(con);
        }
        return singleton;
    }

    private ExportJobXmlParser() {}
}
