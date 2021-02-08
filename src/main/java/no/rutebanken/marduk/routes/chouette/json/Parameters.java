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

package no.rutebanken.marduk.routes.chouette.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.routes.chouette.json.exporter.ConcertoExportParameters;
import no.rutebanken.marduk.routes.chouette.json.exporter.GtfsExportParameters;
import no.rutebanken.marduk.routes.chouette.json.exporter.NetexExportParameters;
import no.rutebanken.marduk.routes.chouette.json.exporter.TransferExportParameters;
import no.rutebanken.marduk.routes.chouette.json.importer.GtfsImportParameters;
import no.rutebanken.marduk.routes.chouette.json.importer.NetexImportParameters;
import no.rutebanken.marduk.routes.chouette.json.importer.RegtoppImportParameters;
import no.rutebanken.marduk.routes.file.FileType;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

public class Parameters {

    public static String createImportParameters(String fileName, String fileType, Provider provider, String user, String description) {
        if (FileType.REGTOPP.name().equals(fileType)) {
            return getRegtoppImportParameters(fileName, provider);
        } else if (FileType.GTFS.name().equals(fileType)) {
            return getGtfsImportParameters(fileName, provider, user, description);
        } else if (FileType.NETEXPROFILE.name().equals(fileType)) {
            return getNetexImportParameters(fileName, provider);
        } else {
            throw new IllegalArgumentException("Cannot create import parameters from file type '" + fileType + "'");
        }
    }

    static String getRegtoppImportParameters(String importName, Provider provider) {
        ChouetteInfo chouetteInfo = provider.chouetteInfo;
        if (!chouetteInfo.usesRegtopp()) {
            throw new IllegalArgumentException("Could not get regtopp information about provider '" + provider.id + "'.");
        }
        RegtoppImportParameters regtoppImportParameters = RegtoppImportParameters.create(importName, chouetteInfo.xmlns,
                chouetteInfo.referential, chouetteInfo.organisation, chouetteInfo.user, chouetteInfo.regtoppVersion,
                chouetteInfo.regtoppCoordinateProjection, chouetteInfo.regtoppCalendarStrategy, chouetteInfo.enableCleanImport,
                chouetteInfo.enableValidation, chouetteInfo.allowCreateMissingStopPlace,
                chouetteInfo.enableStopPlaceIdMapping,false, true, chouetteInfo.generateMissingServiceLinksForModes);
        return regtoppImportParameters.toJsonString();
    }

    static String getGtfsImportParameters(String importName, Provider provider, String user, String description) {
        ChouetteInfo chouetteInfo = provider.chouetteInfo;
        GtfsImportParameters gtfsImportParameters = GtfsImportParameters.create(importName, chouetteInfo.xmlns,
                provider.name, chouetteInfo.organisation, user, chouetteInfo.enableCleanImport,
                chouetteInfo.enableValidation, chouetteInfo.allowCreateMissingStopPlace, chouetteInfo.enableStopPlaceIdMapping, chouetteInfo.generateMissingServiceLinksForModes, description);
        return gtfsImportParameters.toJsonString();
    }

    static String getNetexImportParameters(String importName, Provider provider) {
        ChouetteInfo chouetteInfo = provider.chouetteInfo;
        NetexImportParameters netexImportParameters = NetexImportParameters.create(importName, provider.name,
                chouetteInfo.organisation, chouetteInfo.user, chouetteInfo.enableCleanImport, chouetteInfo.enableValidation,
                chouetteInfo.allowCreateMissingStopPlace, chouetteInfo.enableStopPlaceIdMapping, chouetteInfo.xmlns, chouetteInfo.generateMissingServiceLinksForModes);
        return netexImportParameters.toJsonString();
    }

    public static String getGtfsExportParameters(Provider provider,String exportName, String user, List<Long> linesIds, Date startDate, Date endDate) {
        try {
            ChouetteInfo chouetteInfo = provider.chouetteInfo;

            GtfsExportParameters.GtfsExport gtfsExport = new GtfsExportParameters.GtfsExport(exportName==null ? "offre" : exportName,
                                                                                                    chouetteInfo.xmlns, chouetteInfo.referential, chouetteInfo.organisation, user, true, startDate, endDate);
            gtfsExport.ids = linesIds;
            if (linesIds != null && !linesIds.isEmpty()) {
                gtfsExport.referencesType = "line";
            }

            GtfsExportParameters.Parameters parameters = new GtfsExportParameters.Parameters(gtfsExport);
            GtfsExportParameters importParameters = new GtfsExportParameters(parameters);
            ObjectMapper mapper = new ObjectMapper();
            StringWriter writer = new StringWriter();
            mapper.writeValue(writer, importParameters);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getGtfsExportParameters(Provider provider, String user) {
        return getGtfsExportParameters(provider,null, user, null, null, null);
    }


    public static String getNetexExportProvider(Provider provider, boolean exportStops, String user) {
        try {
            ChouetteInfo chouetteInfo = provider.chouetteInfo;
            String projectionType = null;
            String defaultCodespacePrefix = chouetteInfo.xmlns;
            if(StringUtils.isNotBlank(chouetteInfo.getNameNetexOffreIdfm())){
                defaultCodespacePrefix = chouetteInfo.getNameNetexOffreIdfm();
            }
            NetexExportParameters.NetexExport netexExport = new NetexExportParameters.NetexExport("offre", chouetteInfo.referential, chouetteInfo.organisation, user, projectionType, exportStops, defaultCodespacePrefix);
            NetexExportParameters.Parameters parameters = new NetexExportParameters.Parameters(netexExport);
            NetexExportParameters exportParameters = new NetexExportParameters(parameters);
            ObjectMapper mapper = new ObjectMapper();
            StringWriter writer = new StringWriter();
            mapper.writeValue(writer, exportParameters);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getConcertoExportParameters(Provider provider, String user, Date startDate, Date endDate) {
        try {
            ChouetteInfo chouetteInfo = provider.chouetteInfo;
            ConcertoExportParameters.ConcertoExport concertoExport = new ConcertoExportParameters.ConcertoExport("offre",
                    chouetteInfo.referential, chouetteInfo.organisation, user, startDate, endDate);
            ConcertoExportParameters.Parameters parameters = new ConcertoExportParameters.Parameters(concertoExport);
            ConcertoExportParameters importParameters = new ConcertoExportParameters(parameters);
            ObjectMapper mapper = new ObjectMapper();
            StringWriter writer = new StringWriter();
            mapper.writeValue(writer, importParameters);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getTransferExportParameters(Provider provider, Provider destProvider) {
        try {
            TransferExportParameters.TransferExport transferExport = new TransferExportParameters.TransferExport("data transfer",
                                                                                                                        provider.name, provider.chouetteInfo.organisation, provider.chouetteInfo.user, destProvider.chouetteInfo.referential);
            TransferExportParameters.Parameters parameters = new TransferExportParameters.Parameters(transferExport);
            TransferExportParameters importParameters = new TransferExportParameters(parameters);
            ObjectMapper mapper = new ObjectMapper();
            StringWriter writer = new StringWriter();
            mapper.writeValue(writer, importParameters);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getValidationParameters(Provider provider, String user) {
        ChouetteInfo chouetteInfo = provider.chouetteInfo;

        ValidationParameters validationParameters = ValidationParameters.create("Automatique",
                chouetteInfo.referential, chouetteInfo.organisation, user);
        validationParameters.enableValidation = true;
        return validationParameters.toJsonString();
    }


}
