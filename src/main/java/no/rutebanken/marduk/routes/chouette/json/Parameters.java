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
import no.rutebanken.marduk.routes.chouette.json.exporter.NeptuneExportParameters;
import no.rutebanken.marduk.routes.chouette.json.exporter.NetexExportParameters;
import no.rutebanken.marduk.routes.chouette.json.exporter.TransferExportParameters;
import no.rutebanken.marduk.routes.chouette.json.importer.GtfsImportParameters;
import no.rutebanken.marduk.routes.chouette.json.importer.NeptuneImportParameters;
import no.rutebanken.marduk.routes.chouette.json.importer.NetexImportParameters;
import no.rutebanken.marduk.routes.chouette.json.importer.RawImportParameters;
import no.rutebanken.marduk.routes.chouette.json.importer.RegtoppImportParameters;
import no.rutebanken.marduk.routes.file.FileType;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

public class Parameters {

    public static String createStringImportParameters(RawImportParameters rawImportParameters) {
        String fileType = rawImportParameters.getFileType();

        if (FileType.REGTOPP.name().equals(fileType)) {
            return getRegtoppImportParameters(rawImportParameters);
        } else if (FileType.GTFS.name().equals(fileType)) {
            return getGtfsImportParameters(rawImportParameters);
        } else if (FileType.NETEXPROFILE.name().equals(fileType)) {
            return getNetexImportParameters(rawImportParameters);
        } else if (FileType.NEPTUNE.name().equals(fileType)) {
            return getNeptuneImportParameters(rawImportParameters);
        } else {
            throw new IllegalArgumentException("Cannot create import parameters from file type '" + fileType + "'");
        }
    }

    static String getRegtoppImportParameters(RawImportParameters rawImportParameters) {

        String importName = rawImportParameters.getFileName();
        Provider provider = rawImportParameters.getProvider();


        ChouetteInfo chouetteInfo = provider.chouetteInfo;
        if (!chouetteInfo.usesRegtopp()) {
            throw new IllegalArgumentException("Could not get regtopp information about provider '" + provider.id + "'.");
        }
        RegtoppImportParameters regtoppImportParameters = RegtoppImportParameters.create(importName, chouetteInfo.xmlns,
                chouetteInfo.referential, chouetteInfo.organisation, chouetteInfo.user, chouetteInfo.regtoppVersion,
                chouetteInfo.regtoppCoordinateProjection, chouetteInfo.regtoppCalendarStrategy, chouetteInfo.enableCleanImport,
                chouetteInfo.enableValidation, chouetteInfo.allowCreateMissingStopPlace,
                chouetteInfo.enableStopPlaceIdMapping, false, true, chouetteInfo.generateMissingServiceLinksForModes, rawImportParameters.getCleanMode());
        return regtoppImportParameters.toJsonString();
    }

    static String getNeptuneImportParameters(RawImportParameters rawImportParameters) {
        NeptuneImportParameters neptuneImportParameters = NeptuneImportParameters.create(rawImportParameters);
        return neptuneImportParameters.toJsonString();
    }

    static String getGtfsImportParameters(RawImportParameters rawImportParameters) {
        GtfsImportParameters gtfsImportParameters = GtfsImportParameters.create(rawImportParameters);
        return gtfsImportParameters.toJsonString();
    }

    static String getNetexImportParameters(RawImportParameters rawImportParameters) {
        NetexImportParameters netexImportParameters = NetexImportParameters.create(rawImportParameters);
        return netexImportParameters.toJsonString();
    }

    public static String getNeptuneExportParameters(Provider provider, String exportName, String user, List<Long> linesIds, Date startDate, Date endDate, String exportedFilename) {
        try {
            ChouetteInfo chouetteInfo = provider.chouetteInfo;
            NeptuneExportParameters.NeptuneExport neptuneExport = new NeptuneExportParameters.NeptuneExport(exportName == null ? "offre" : exportName, chouetteInfo.xmlns, chouetteInfo.referential, chouetteInfo.organisation, user, startDate, endDate, exportedFilename);


            neptuneExport.ids = linesIds;
            if (linesIds != null && !linesIds.isEmpty()) {
                neptuneExport.referencesType = "line";
            }
            NeptuneExportParameters.Parameters parameters = new NeptuneExportParameters.Parameters(neptuneExport);
            NeptuneExportParameters importParameters = new NeptuneExportParameters(parameters);
            ObjectMapper mapper = new ObjectMapper();
            StringWriter writer = new StringWriter();
            mapper.writeValue(writer, importParameters);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getGtfsExportParameters(Provider provider, String exportName, String user, boolean keepOriginalId, List<Long> linesIds, Date startDate, Date endDate, String exportedFilename, IdParameters idParams, boolean mappingLinesIds, Boolean commercialPointExport) {
        try {
            ChouetteInfo chouetteInfo = provider.chouetteInfo;

            GtfsExportParameters.GtfsExport gtfsExport = new GtfsExportParameters.GtfsExport(exportName == null ? "offre" : exportName, chouetteInfo.xmlns,
                    chouetteInfo.referential, chouetteInfo.organisation, user, keepOriginalId, startDate, endDate, exportedFilename, idParams, mappingLinesIds, commercialPointExport);
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

    public static String getGtfsExportParameters(Provider provider, String user, boolean keepOriginalId, String exportedFilename, Boolean commercialPointExport) {
        return getGtfsExportParameters(provider, null, user, keepOriginalId, null, null, null, exportedFilename, new IdParameters(), false, commercialPointExport);
    }


    public static String getNetexExportProvider(Provider provider, boolean exportStops, String user, String exportedFilename) {
        try {
            ChouetteInfo chouetteInfo = provider.chouetteInfo;
            String projectionType = null;
            String defaultCodespacePrefix = chouetteInfo.xmlns;
            if (StringUtils.isNotBlank(chouetteInfo.getNameNetexOffreIdfm())) {
                defaultCodespacePrefix = chouetteInfo.getNameNetexOffreIdfm();
            }
            NetexExportParameters.NetexExport netexExport = new NetexExportParameters.NetexExport("offre", chouetteInfo.referential, chouetteInfo.organisation, user, projectionType, exportStops, defaultCodespacePrefix, exportedFilename);
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

    public static String getConcertoExportParameters(Provider provider, String user) {
        try {
            LocalDateTime localDateTime = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
            ChouetteInfo chouetteInfo = provider.chouetteInfo;
            ConcertoExportParameters.ConcertoExport concertoExport = new ConcertoExportParameters.ConcertoExport("offre",
                    chouetteInfo.referential, chouetteInfo.organisation, user);
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
