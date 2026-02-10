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

package no.rutebanken.marduk.routes.file;

import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.routes.file.beans.CustomGtfsFileTransformer;
import no.rutebanken.marduk.services.FileSystemService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.IdentityBean;
import org.onebusaway.gtfs.model.ServiceCalendar;
import org.onebusaway.gtfs.model.ServiceCalendarDate;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.serialization.GtfsEntitySchemaFactory;
import org.onebusaway.gtfs.services.GtfsMutableRelationalDao;
import org.onebusaway.gtfs_merge.GtfsMerger;
import org.onebusaway.gtfs_merge.strategies.AbstractEntityMergeStrategy;
import org.onebusaway.gtfs_merge.strategies.EDuplicateDetectionStrategy;
import org.onebusaway.gtfs_merge.strategies.EntityMergeStrategy;
import org.onebusaway.gtfs_transformer.GtfsTransformer;
import org.onebusaway.gtfs_transformer.factory.EntitiesTransformStrategy;
import org.onebusaway.gtfs_transformer.match.AlwaysMatch;
import org.onebusaway.gtfs_transformer.match.TypedEntityMatch;
import org.onebusaway.gtfs_transformer.services.EntityTransformStrategy;
import org.onebusaway.gtfs_transformer.services.TransformContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GtfsFileUtils {
    private static final Logger logger = LoggerFactory.getLogger(GtfsFileUtils.class);

    public static final String FEED_INFO_FILE_NAME = "feed_info.txt";
    public static final String ATTRIBUTION_FILE_NAME = "attribution.txt";

    public static final String ATTRIBUTION_FIELD_NAME = "attribution_id";

    private static final CustomGtfsFileTransformer IDS_TOP_OTP_FORMAT_TRANSFORMER = new CustomGtfsFileTransformer() {

        @Override
        protected void addCustomTransformations(GtfsTransformer transformer) {
            GtfsEntitySchemaFactory.getEntityClasses()
                    .forEach(ec -> transformer.addTransform(createEntitiesTransformStrategy(ec, new IdSeparatorTransformer())));
        }
    };

    public static File mergeGtfsFilesInDirectory(String path) {
        logger.info("Merging files from path : " + path);
        return mergeGtfsFiles(FileUtils.listFiles(new File(path), new String[]{"zip"}, false));
    }

    public static void main(String[] args) {
        new GtfsFileUtils().mergeGtfsFilesInDirectory("/home/gfora/Téléchargements/Remi");
    }

    public static File mergeGtfsFiles(Collection<File> files) {

        try {
            long t1 = System.currentTimeMillis();
            logger.debug("Merging GTFS-files");

            File outputFile = File.createTempFile("marduk-merge", ".zip");
            buildGtfsMerger(EDuplicateDetectionStrategy.IDENTITY).run(new ArrayList<>(files), outputFile);

            addAttributionFile(files, outputFile);
            logger.debug("Merged GTFS-files - spent {} ms", (System.currentTimeMillis() - t1));
            return outputFile;
        } catch (IOException ioException) {
            throw new MardukException("Merging of GTFS files failed", ioException);
        }

    }



    /**
     * OTP requires ids with '.' as separator instead of ':'.
     * <p>
     * Create a copy of a GTFS file with all ids transformed to replace id separator chars.
     */
    public static File transformIdsToOTPFormat(File inputFile) throws Exception {

        logger.debug("Replacing id separator in inputfile: " + inputFile.getPath());
        long t1 = System.currentTimeMillis();


        File outputFile = IDS_TOP_OTP_FORMAT_TRANSFORMER.transform(inputFile);

        logger.debug("Replaced id separator in GTFS-file - spent {} ms", (System.currentTimeMillis() - t1));

        return outputFile;
    }


    public static EntitiesTransformStrategy createEntitiesTransformStrategy(Class<?> entityClass, EntityTransformStrategy strategy) {
        EntitiesTransformStrategy transformStrategy = new EntitiesTransformStrategy();
        transformStrategy.addModification(new TypedEntityMatch(entityClass, new AlwaysMatch()), strategy);
        return transformStrategy;
    }

    private static class IdSeparatorTransformer implements EntityTransformStrategy {

        protected static final String OTP_ID_SEPARATOR = "\\.";
        protected static final String EXTERNAL_ID_SEPARATOR = "\\:";

        @Override
        public void run(TransformContext context, GtfsMutableRelationalDao dao, Object entity) {
            if (entity instanceof ServiceCalendar) {
                transformAgencyAndId(((ServiceCalendar) entity).getServiceId());
            } else if (entity instanceof ServiceCalendarDate) {
                transformAgencyAndId(((ServiceCalendarDate) entity).getServiceId());
            } else if (entity instanceof Trip) {
                transformAgencyAndId(((Trip) entity).getServiceId());
            } else if (entity instanceof Stop) {
                Stop stop = (Stop) entity;
                stop.setParentStation(transform(stop.getParentStation()));
            }
            if (entity instanceof IdentityBean) {
                IdentityBean identityBean = (IdentityBean) entity;
                Serializable id = identityBean.getId();

                if (id instanceof AgencyAndId) {
                    transformAgencyAndId((AgencyAndId) id);
                } else if (id instanceof String) {
                    identityBean.setId(transform((String) id));
                }
            }
        }

        private void transformAgencyAndId(AgencyAndId agencyAndId) {
            agencyAndId.setId(transform(agencyAndId.getId()));
        }

        private String transform(String id) {
            if (id == null) {
                return null;
            }
            return id.replaceFirst(EXTERNAL_ID_SEPARATOR, OTP_ID_SEPARATOR).replaceFirst(EXTERNAL_ID_SEPARATOR, OTP_ID_SEPARATOR);
        }
    }

    private static void addFeedInfoFromFirstGtfsFile(Collection<File> files, File outputFile) throws IOException {
        ByteArrayOutputStream feedInfoStream = extractFeedInfoFile(files);
        addFileToArchive(outputFile, feedInfoStream,FEED_INFO_FILE_NAME);
    }

    private static void addAttributionFile(Collection<File> files, File outputFile) throws IOException {
        ByteArrayOutputStream attributionStream = generateAttributionFile(files);
        if (attributionStream != null){
            addFileToArchive(outputFile, attributionStream,ATTRIBUTION_FILE_NAME);
        }
    }

    private static ByteArrayOutputStream generateAttributionFile(Collection<File> files) throws IOException {
        ByteArrayOutputStream result = null;
        List<String[]> modifiedRecords = new ArrayList<>();
        
        int currentFileIndex = 1;
        for (File file : files) {
            if (ZipFileUtils.listFilesInZip(file).stream().anyMatch(f -> ATTRIBUTION_FILE_NAME.equals(f))) {
                try(FileInputStream inputStream = new FileInputStream(file)) {
                    ByteArrayOutputStream attributionStream = ZipFileUtils.extractFileFromZipFile(inputStream, ATTRIBUTION_FILE_NAME);
                    Iterable<CSVRecord> records = FileSystemService.getRecords(attributionStream);
                    for (CSVRecord record : records) {
                        if(record.isSet(ATTRIBUTION_FIELD_NAME)){
                            String modifiedAttributionId = currentFileIndex + "-" + record.get(ATTRIBUTION_FIELD_NAME);
                            String agencyId = record.get("agency_id");
                            String routeId = record.get("route_id");
                            String tripId = record.get("trip_id");
                            String organisationName = record.get("organization_name");
                            String isProducer = record.get("is_producer");
                            String isOperator = record.get("is_operator");
                            String isAuthority = record.get("is_authority");
                            String attributionUrl = record.get("attribution_url");
                            String attributionEmail = record.get("attribution_email");
                            String attributionPhone = record.get("attribution_phone");
                            modifiedRecords.add(new String[]{modifiedAttributionId,agencyId,routeId,tripId,organisationName,isProducer,isOperator,isAuthority,attributionUrl,attributionEmail,attributionPhone});
                        }
                    }
                }
            }
            currentFileIndex++;
        }


        if (!modifiedRecords.isEmpty()) {
            result = new ByteArrayOutputStream();
            String[] headers = {"attribution_id","agency_id","route_id","trip_id","organization_name","is_producer","is_operator","is_authority","attribution_url",
                    "attribution_email","attribution_phone"};

            try (OutputStreamWriter writer = new OutputStreamWriter(result, StandardCharsets.UTF_8);
                 CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(headers))) {
                for (String[] row : modifiedRecords) {
                    printer.printRecord((Object[]) row);
                }

            } catch (Exception e) {
                logger.error("Error while writing attributions", e);
            }


        }


        return result;
    }


    private static void addFileToArchive(File outputFile, ByteArrayOutputStream stream, String fileName) throws IOException {
        if (stream != null) {
            File tmp = new File(fileName);
            stream.writeTo(new FileOutputStream(tmp));
            try (FileInputStream source = new FileInputStream(outputFile)) {
                FileUtils.copyInputStreamToFile(ZipFileUtils.addFilesToZip(source, tmp), outputFile);
            }
            tmp.delete();
        }
    }


    private static GtfsMerger buildGtfsMerger(EDuplicateDetectionStrategy duplicateDetectionStrategy) {
        GtfsMerger merger = new GtfsMerger(false);

        merger.setTransferStrategy(new ExtendedTransferMergeStrategy());
        for (Class<?> entityClass : GtfsEntitySchemaFactory.getEntityClasses()) {
            EntityMergeStrategy entityMergeStrategy = merger.getEntityMergeStrategyForEntityType(entityClass);
            if (entityMergeStrategy instanceof AbstractEntityMergeStrategy) {
                ((AbstractEntityMergeStrategy) entityMergeStrategy).setDuplicateDetectionStrategy(duplicateDetectionStrategy);
            }
        }
        return merger;
    }


    private static ByteArrayOutputStream extractFeedInfoFile(Collection<File> files) throws IOException {
        for (File file : files) {
            if (ZipFileUtils.listFilesInZip(file).stream().anyMatch(f -> FEED_INFO_FILE_NAME.equals(f))) {
                try(FileInputStream inputStream = new FileInputStream(file)) {
                    return ZipFileUtils.extractFileFromZipFile(inputStream, FEED_INFO_FILE_NAME);
                }
            }

        }
        return null;
    }

}
