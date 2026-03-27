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

import no.rutebanken.marduk.config.MardukPropertiesConfig;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.routes.file.beans.FileTypeClassifierBean;
import no.rutebanken.marduk.routes.file.beans.GtfsFileInputWithParameters;
import no.rutebanken.marduk.routes.file.onebusaway.FilterOneStopJourney;
import no.rutebanken.marduk.routes.file.onebusaway.NonStandardStopTransformer;
import no.rutebanken.marduk.services.FileSystemService;
import org.apache.camel.Exchange;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.Strings;
import org.onebusaway.gtfs_transformer.GtfsTransformer;
import org.onebusaway.gtfs_transformer.TransformSpecificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.FileSystem;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static no.rutebanken.marduk.Constants.*;

@Component
public class ZipFileUtils {
    private static final Logger logger = LoggerFactory.getLogger(ZipFileUtils.class);

    private final MardukPropertiesConfig mardukPropertiesConfig;

    public ZipFileUtils(MardukPropertiesConfig mardukPropertiesConfig) {
        this.mardukPropertiesConfig = mardukPropertiesConfig;
    }

    public static Set<String> listFilesInZip(File file) {
        try (ZipFile zipFile = new ZipFile(file)) {
            return zipFile.stream()
                    .filter(ze -> !ze.isDirectory())
                    // Extrait seulement le nom du fichier du chemin complet
                    .map(ze -> new File(ze.getName()).getName())
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            logger.error("Unable to list files in ZIP", e);
            return Collections.emptySet();
        }
    }

    public Set<String> listFilesInZip(byte[] data) {
        return listFilesInZip(new ByteArrayInputStream(data));
    }

    public static Set<String> listFilesInZip(InputStream inputStream) {
        Set<String> fileNames = new HashSet<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry zipEntry = zipInputStream.getNextEntry();
            while (zipEntry != null) {
                fileNames.add(zipEntry.getName());
                zipEntry = zipInputStream.getNextEntry();
            }
            return fileNames;
        } catch (IOException e) {
            logger.error("Unable to list files in ZIP", e);
            return Collections.emptySet();
        }
    }

    private File transformGtfsFiles(GtfsFileInputWithParameters gtfsFileInputWithParameter) throws Exception {
        logger.info("Transforming GTFS-file");
        long time = System.currentTimeMillis();
        GtfsTransformer transformer = new GtfsTransformer();
        File outputFile = File.createTempFile("marduk-cleanup", ".zip");
        transformer.setGtfsInputDirectories(Collections.singletonList(gtfsFileInputWithParameter.getInputFile()));
        transformer.setOutputDirectory(outputFile);
        addLocationTypeFilter(transformer);
        if (gtfsFileInputWithParameter.isAllowNonStandardGtfs()) {
            transformer.getReader()
                    .addEntityHandler(new NonStandardStopTransformer(
                            gtfsFileInputWithParameter.getFillMissingStopName(),
                            gtfsFileInputWithParameter.getDefaultLatitude(),
                            gtfsFileInputWithParameter.getDefaultLongitude()
                            )
                    );
        }
        executeTransformations(transformer, time);

        return outputFile;
    }

    public static boolean zipFileContainsSingleFolder(byte[] data) {

        try {
            return zipFileContainsSingleFolder(getFile(data));
        } catch (IOException e) {
            logger.error(e.getMessage());
            return false;
        }
    }

    private static boolean zipFileContainsSingleFolder(File inputFile) {
        try {
            return getZipFileIfSingleFolder(inputFile) != null;
        } catch (IOException e) {
            logger.error(e.getMessage());
            return false;
        }
    }

    public static File rePackZipFile(byte[] data) throws IOException {

        logger.info("Repacking zipfile");
        File inputFile = getFile(data);

        ZipFile zipFile = getZipFileIfSingleFolder(inputFile);

        if (zipFile == null) {
            logger.debug("Single folder not detected");
            return inputFile;
        }

        File tmpFile = File.createTempFile("marduk-output", ".zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(tmpFile))) {

            String directoryName = "";

            ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(data));

            ZipEntry zipEntry = zipInputStream.getNextEntry();
            while (zipEntry != null) {
                if (!zipEntry.isDirectory()) {
                    InputStream inputStream = zipFile.getInputStream(zipEntry);
                    ZipEntry outEntry = new ZipEntry(zipEntry.getName().replace(directoryName, ""));
                    out.putNextEntry(outEntry);
                    byte[] buf = new byte[inputStream.available()];
                    IOUtils.readFully(inputStream, buf);
                    out.write(buf);
                } else {
                    directoryName = zipEntry.getName();
                }
                zipEntry = zipInputStream.getNextEntry();
            }
        }
        logger.info("File written to : {}", tmpFile.getAbsolutePath());

        return tmpFile;
    }


    public static File zipFilesInFolder(String folder, String targetFilePath) {
        try {

            FileOutputStream out = new FileOutputStream(targetFilePath);
            ZipOutputStream outZip = new ZipOutputStream(out);

            FileUtils.listFiles(new File(folder), null, true).forEach(file -> addToZipFile(file, outZip));

            outZip.close();
            out.close();

            return new File(targetFilePath);
        } catch (IOException ioe) {
            throw new MardukException("Failed to zip files in folder: " + ioe.getMessage(), ioe);
        }
    }

    public static File zipFilesWithExtensionInFolder(String folder, String targetFilePath, String extension) {
        try {

            FileOutputStream out = new FileOutputStream(targetFilePath);
            ZipOutputStream outZip = new ZipOutputStream(out);

            List<String> extensions = List.of(extension);
            FileUtils.listFiles(new File(folder), extensions.toArray(new String[0]), true).forEach(file -> addToZipFile(file, outZip));

            outZip.close();
            out.close();

            return new File(targetFilePath);
        } catch (IOException ioe) {
            throw new MardukException("Failed to zip files in folder: " + ioe.getMessage(), ioe);
        }
    }

    public static void addToZipFile(File file, ZipOutputStream zos) {
        try {
            try (FileInputStream fis = new FileInputStream(file)) {
                ZipEntry zipEntry = new ZipEntry(file.getName());
                zos.putNextEntry(zipEntry);

                byte[] bytes = new byte[1024];
                int length;
                while ((length = fis.read(bytes)) >= 0) {
                    zos.write(bytes, 0, length);
                }

                zos.closeEntry();
            }
        } catch (IOException ioe) {
            throw new MardukException("Failed to add file to zip: " + ioe.getMessage(), ioe);
        }
    }


    public static ByteArrayOutputStream extractFileFromZipFile(InputStream inputStream, String extractFileName) {
        try {
            byte[] buffer = new byte[1024];
            ZipInputStream zis = new ZipInputStream(inputStream);
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                String fileName = zipEntry.getName();

                if (extractFileName.equals(fileName)) {

                    ByteArrayOutputStream fos = new ByteArrayOutputStream();
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                    return fos;
                }
                zipEntry = zis.getNextEntry();
            }

        } catch (IOException ioE) {
            throw new RuntimeException("Unzipping archive failed: " + ioE.getMessage(), ioE);
        }
        return null;
    }

    public static void replaceFileInZipFile(File zipFile, String replaceFileName, ByteArrayOutputStream replaceFileContent) {
        try (FileSystem fs = FileSystems.newFileSystem(Paths.get(zipFile.getPath()), (ClassLoader) null)) {
            Path fileInsideZipPath = fs.getPath(replaceFileName);
            File tmp = File.createTempFile(replaceFileName, ".tmp");
            replaceFileContent.writeTo(new FileOutputStream(tmp));
            Files.delete(fileInsideZipPath);
            Files.copy(Paths.get(tmp.getPath()), fileInsideZipPath);
            tmp.delete();
        } catch (IOException e) {
            throw new RuntimeException("Failed to replace file in archive: " + e.getMessage(), e);
        }
    }

    public static void unzip(String zipFilePath) throws IOException {
        File zipFile = new File(zipFilePath);
        String destDir = zipFile.getParent();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {

                File outFile =  Path.of(destDir, entry.getName()).toFile();
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    byte[] bytes = new byte[1024];
                    int length;
                    while ((length = zis.read(bytes)) >= 0) {
                        fos.write(bytes, 0, length);
                    }
                }
            }
        }
    }

    public static void unzipAndDeleteAll(String directory) throws IOException {
        File[] files = new File(directory).listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".zip")) {
                    unzip(file.getAbsolutePath());
                    file.delete();
                }
            }
        }
    }

    public static void unzipFile(InputStream inputStream, String targetFolder) {
        try {
            byte[] buffer = new byte[1024];
            ZipInputStream zis = new ZipInputStream(inputStream);
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                String fileName = zipEntry.getName();
                logger.info("unzipping file: {}", fileName);

                File newFile = new File(targetFolder + "/" + fileName);
                if (fileName.endsWith("/")) {
                    if(!newFile.exists()){
                        Files.createDirectories(newFile.toPath());
                    }
                    zipEntry = zis.getNextEntry();
                    continue;
                }

                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
            zis.close();
        } catch (IOException ioE) {
            throw new RuntimeException("Unzipping archive failed: " + ioE.getMessage(), ioE);
        }
    }

    public static void copyZipFileWithoutUnwantedFiles(Path inputFile, OutputStream outputStream, String fileExtensionToKeep) {
        try (org.apache.commons.compress.archivers.zip.ZipFile zip = org.apache.commons.compress.archivers.zip.ZipFile.builder()
                .setFile(inputFile.toFile())
                .get()) {
            try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(outputStream)) {

                Enumeration<? extends ZipArchiveEntry> entries = zip.getEntries();
                while (entries.hasMoreElements()) {
                    ZipArchiveEntry zipEntry = entries.nextElement();
                    if (Strings.CI.endsWith(zipEntry.getName(), fileExtensionToKeep)) {
                        ZipArchiveEntry outEntry = copyZipArchiveEntry(zipEntry);
                        out.addRawArchiveEntry(outEntry, zip.getRawInputStream(zipEntry));
                    }
                }
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    public static void copyGtfsZipFileWithoutFareFiles(File file) {
        List<String> fareFilesToRemove = Arrays.asList(
                "fare_attributes.txt",
                "fare_rules.txt",
                "fare_products.txt",
                "fare_media.txt",
                "fare_leg_rules.txt",
                "fare_transfer_rules.txt",
                "fare_leg_join_rules.txt"
        );

        Map<String, String> env = new HashMap<>();
        env.put("create", "false");

        try (FileSystem zipfs = FileSystems.newFileSystem(file.toPath(), env, null)) {
            for (String fileName : fareFilesToRemove) {
                Path pathInZip = zipfs.getPath(fileName);
                if (Files.exists(pathInZip)) {
                    Files.delete(pathInZip);
                    logger.info("File removed from ZIP: {}", fileName);
                }
            }
        } catch (IOException e) {
            logger.error("Error while modifying ZIP: {}", file.getName(), e);
            throw new RuntimeException("Error while removing fare files from ZIP", e);
        }

    }

    private static ZipArchiveEntry copyZipArchiveEntry(ZipArchiveEntry zipEntry) {
        ZipArchiveEntry outEntry = new ZipArchiveEntry(zipEntry.getName());
        outEntry.setCompressedSize(zipEntry.getCompressedSize());
        outEntry.setCrc(zipEntry.getCrc());
        outEntry.setExternalAttributes(zipEntry.getExternalAttributes());
        outEntry.setExtra(zipEntry.getExtra());
        outEntry.setExtraFields(zipEntry.getExtraFields());
        outEntry.setGeneralPurposeBit(zipEntry.getGeneralPurposeBit());
        outEntry.setInternalAttributes(zipEntry.getInternalAttributes());
        outEntry.setMethod(zipEntry.getMethod());
        outEntry.setRawFlag(zipEntry.getRawFlag());
        outEntry.setSize(zipEntry.getSize());
        return outEntry;
    }

    private static File getFile(byte[] data) throws IOException {
        File inputFile = File.createTempFile("marduk-input", ".zip");

        try (FileOutputStream fos = new FileOutputStream(inputFile)) {
            fos.write(data);
        }
        return inputFile;
    }

    public File transformGtfsFile(byte[] data, Exchange exchange) throws IOException {
        File file = getFile(data);

        try {
            file = repackZipToFlatStructureIfNeeded(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to recompress ZIP file", e);
        }

        Object headerRoute = exchange.getIn().getHeader("importtargetroutes");
        Set<String> routeIds = headerRoute != null ?
                Arrays.stream(headerRoute.toString().split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet()) : new HashSet<>();

        boolean allowNonStandardGtfs = BooleanUtils.toBoolean(exchange.getIn().getHeader(ALLOW_NON_STANDARD_GTFS, String.class));
        String fillMissingStopName = exchange.getIn().getHeader(FILL_MISSING_STOP_NAME, String.class);
        String fillMissingCoordinates = exchange.getIn().getHeader(FILL_MISSING_COORDINATES, String.class);
        Boolean importFareFiles = exchange.getIn().getHeader(IMPORT_FARE_FILES, Boolean.class);
        Boolean importGtfsFlex = exchange.getIn().getHeader(ALLOW_GTFS_FLEX, Boolean.class);
        String originalFileName = exchange.getIn().getHeader(FILE_NAME, String.class);

        if (file.exists() && file.length() > 0) {
            Set<String> filenamesInZip = listFilesInZip(file);
            if (FileTypeClassifierBean.isGtfsZip(filenamesInZip)) {
                try {
                    if (Boolean.TRUE.equals(importGtfsFlex)) {
                        String referential = exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class);
                        String tempDir = System.getProperty("java.io.tmpdir");

                        String cleanFileName = new File(originalFileName).getName();
                        File sourceFileForFlex = new File(tempDir, cleanFileName);
                        File flexFinalFile = File.createTempFile("marduk-flex-"+referential, ".zip");

                        if (sourceFileForFlex.exists()) {
                            logger.info("Analyse : Création du fichier source Flex : {}", sourceFileForFlex.getAbsolutePath());
                            Files.copy(file.toPath(), sourceFileForFlex.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            processFlexVersion(sourceFileForFlex);
                            Files.copy(sourceFileForFlex.toPath(), flexFinalFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        }
                        exchange.getIn().setHeader("GTFS_FLEX_FILE_PATH", flexFinalFile.getAbsolutePath());
                        logger.info("Fichier Flex unique généré : {}", flexFinalFile.getAbsolutePath());
                    }

                    processStandardVersion(file);

                    if (Boolean.FALSE.equals(importFareFiles)) {
                        copyGtfsZipFileWithoutFareFiles(file);
                    }

                    GtfsFileInputWithParameters params = new GtfsFileInputWithParameters(file, routeIds,
                            allowNonStandardGtfs, fillMissingStopName, fillMissingCoordinates, importGtfsFlex);

                    if (!routeIds.isEmpty()) {
                        file = filterGtfsByRouteIds(params);
                    } else {
                        file = transformGtfsFiles(params);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("GTFS conversion failed", e);
                }
            } else {
                logger.warn("The ZIP file does not appear to be a valid GTFS file. Files found : {}", filenamesInZip);
            }
        }
        return file;
    }

    private static File repackZipToFlatStructureIfNeeded(File inputFile) throws IOException {
        String commonPathPrefix = null;
        try (ZipFile zipFile = new ZipFile(inputFile)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while(entries.hasMoreElements()){
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;

                String path = new File(entry.getName()).getParent();
                if (path == null) {
                    logger.debug("File found at the root, the ZIP file is already flat.");
                    return inputFile;
                }

                String currentPrefix = path + File.separator;
                if(commonPathPrefix == null){
                    commonPathPrefix = currentPrefix;
                } else if(!commonPathPrefix.equals(currentPrefix)){
                    logger.debug("Several paths found, ZIP is already flat.");
                    return inputFile;
                }
            }
        }

        if (commonPathPrefix == null) {
            logger.debug("The ZIP file is empty or contains only empty folders.");
            return inputFile;
        }

        File flatZip = File.createTempFile("marduk-repacked", ".zip");

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(flatZip));
             ZipInputStream zis = new ZipInputStream(new FileInputStream(inputFile))) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                String newName = entry.getName().substring(commonPathPrefix.length());
                if(newName.isEmpty()) continue; // Eviter les entrées vides

                zos.putNextEntry(new ZipEntry(newName));

                byte[] buffer = new byte[1024];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
                zos.closeEntry();
            }
        }

        logger.info("Recompression complete : {}", flatZip.getAbsolutePath());
        return flatZip;
    }

    private static ZipFile getZipFileIfSingleFolder(File inputFile) throws IOException {

        if (inputFile == null || inputFile.length() == 0) {
            return null;
        }

        ZipFile zipFile = new ZipFile(inputFile);
        boolean allFilesInSingleDirectory = false;
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        String directoryName = "";
        while (entries.hasMoreElements()) {
            ZipEntry zipEntry = entries.nextElement();
            if (zipEntry.isDirectory()) {
                allFilesInSingleDirectory = true;
                directoryName = zipEntry.getName();
            } else {
                if (!zipEntry.getName().startsWith(directoryName)) {
                    allFilesInSingleDirectory = false;
                    break;
                }
            }
        }

        if (allFilesInSingleDirectory) {
            return zipFile;
        }
        return null;

    }


    public static InputStream addFilesToZip(InputStream source, File... files) {
        try {
            String name = UUID.randomUUID().toString();
            File tmpZip = File.createTempFile(name, null);
            tmpZip.delete();
            byte[] buffer = new byte[1024 * 32];
            ZipInputStream zin = new ZipInputStream(source);
            ZipOutputStream out = new ZipOutputStream(new FileOutputStream(tmpZip));

            for (File file : files) {
                try (FileInputStream fileInputStream = new FileInputStream(file)) {

                    out.putNextEntry(new ZipEntry(file.getName()));
                    for (int read = fileInputStream.read(buffer); read > -1; read = (fileInputStream).read(buffer)) {
                        out.write(buffer, 0, read);
                    }
                    out.closeEntry();
                }
            }

            for (ZipEntry ze = zin.getNextEntry(); ze != null; ze = zin.getNextEntry()) {
                out.putNextEntry(ze);
                for (int read = zin.read(buffer); read > -1; read = zin.read(buffer)) {
                    out.write(buffer, 0, read);
                }
                out.closeEntry();
            }

            out.close();
            return new AutoDeleteOnCloseFileInputStream(tmpZip);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }

    private File filterGtfsByRouteIds(GtfsFileInputWithParameters gtfsFileInputWithParameters) throws Exception {
        logger.info("Filtrage du GTFS pour les route IDs: {}", gtfsFileInputWithParameters.getRouteIds());
        long time = System.currentTimeMillis();
        GtfsTransformer transformer = new GtfsTransformer();
        File outputFile = File.createTempFile("marduk-filtered", ".zip");
        transformer.setGtfsInputDirectories(Collections.singletonList(gtfsFileInputWithParameters.getInputFile()));
        transformer.setOutputDirectory(outputFile);
        transformer.addTransform(new FilterByRouteIdsStrategy(gtfsFileInputWithParameters.getRouteIds()));
        addLocationTypeFilter(transformer);
        if (gtfsFileInputWithParameters.isAllowNonStandardGtfs()) {
            transformer.getReader()
                    .addEntityHandler(new NonStandardStopTransformer(
                            gtfsFileInputWithParameters.getFillMissingStopName(),
                            gtfsFileInputWithParameters.getDefaultLatitude(),
                            gtfsFileInputWithParameters.getDefaultLongitude()
                        )
                    );
        }
        executeTransformations(transformer, time);

        return outputFile;
    }

    private void addLocationTypeFilter(GtfsTransformer transformer) throws IOException, TransformSpecificationException {
        if (mardukPropertiesConfig.isGtfsImportFilterLocationTypeEnabled()) {
            transformer.getTransformFactory().addModificationsFromString("{'op':'remove', 'match':{'file':'stops.txt', 'location_type':'3'}}");
            transformer.getTransformFactory().addModificationsFromString("{'op':'remove', 'match':{'file':'stops.txt', 'location_type':'4'}}");
            transformer.addTransform(new FilterOneStopJourney());
        }
    }

    private void executeTransformations(GtfsTransformer transformer, long time) throws Exception {
        transformer.getReader().setOverwriteDuplicates(true);
        transformer.run();

        logger.info("Filtrage GTFS par route IDs terminé en {} ms", (System.currentTimeMillis() - time));
    }

    public static void deleteFilesByExtension(String directory, String extension) {
        File[] files = new File(directory).listFiles();
        if (files != null) {
            Stream.of(files)
                    .filter(file -> !file.isDirectory() && file.getName().endsWith(extension))
                    .forEach(File::delete);
        }
    }

    /**
     * Prépare la version STANDARD : Supprime les fichiers Flex et les colonnes Flex de stop_times.
     */
    private void processStandardVersion(File file) throws IOException {
        List<String> flexFiles = Arrays.asList("location_group_stops.txt", "location_groups.txt", "locations.geojson", "booking_rules.txt");
        List<String> flexCols = Arrays.asList("start_pickup_drop_off_window", "end_pickup_drop_off_window", "pickup_booking_rule_id", "drop_off_booking_rule_id");

        Map<String, String> env = new HashMap<>();
        env.put("create", "false");

        try (FileSystem zipfs = FileSystems.newFileSystem(file.toPath(), env, null)) {
            for (String f : flexFiles) {
                Path p = zipfs.getPath(f);
                if (Files.exists(p)) Files.delete(p);
            }

            Path stopTimesPath = zipfs.getPath("stop_times.txt");
            if (Files.exists(stopTimesPath)) {
                byte[] bytes = Files.readAllBytes(stopTimesPath);
                String content = new String(bytes, StandardCharsets.UTF_8);
                CSVFormat format = createBaseFormat(content);
                File tmpFile = File.createTempFile("st_std", ".tmp");

                try (CSVParser parser = format.parse(new StringReader(content));
                     CSVPrinter printer = getPrinterForFile(tmpFile, format)) {

                    List<String> headers = parser.getHeaderNames();
                    List<String> filteredHeaders = headers.stream()
                            .filter(h -> !flexCols.contains(h))
                            .collect(Collectors.toList());

                    printer.printRecord(filteredHeaders);

                    for (CSVRecord record : parser) {
                        List<String> values = new ArrayList<>();
                        for (String h : filteredHeaders) values.add(record.get(h));
                        printer.printRecord(values);
                    }
                }
                Files.copy(tmpFile.toPath(), stopTimesPath, StandardCopyOption.REPLACE_EXISTING);
                tmpFile.delete();
            }
        }
    }

    /**
     * Prépare la version FLEX : Ne garde QUE les lignes Flex et synchronise Trips/Stops.
     */
    private void processFlexVersion(File file) throws IOException {
        Map<String, String> env = new HashMap<>();
        env.put("create", "false");

        try (FileSystem zipfs = FileSystems.newFileSystem(file.toPath(), env, null)) {
            Path locGroupStopsPath = zipfs.getPath("location_group_stops.txt");
            if (!Files.exists(locGroupStopsPath)) return;

            Set<String> flexStops = new HashSet<>();
            Set<String> flexGroupIds = new HashSet<>();

            try (CSVParser parser = getParserForPath(locGroupStopsPath)) {
                for (CSVRecord record : parser) {
                    flexStops.add(record.get("stop_id").trim());
                    flexGroupIds.add(record.get("location_group_id").trim());
                }
            }

            Path stopTimesPath = zipfs.getPath("stop_times.txt");
            byte[] stBytes = Files.readAllBytes(stopTimesPath);
            String stContent = new String(stBytes, StandardCharsets.UTF_8);
            CSVFormat stFormat = createBaseFormat(stContent);

            File stTmp = File.createTempFile("st_flex", ".tmp");
            Set<String> activeTrips = new HashSet<>();
            Set<String> stopsToKeep = new HashSet<>();

            try (CSVParser parser = stFormat.parse(new StringReader(stContent));
                 CSVPrinter printer = getPrinterForFile(stTmp, stFormat)) {

                printer.printRecord(parser.getHeaderNames());
                boolean hasLocCol = parser.getHeaderMap().containsKey("location_group_id");

                for (CSVRecord record : parser) {
                    String stopId = record.get("stop_id").trim();
                    String locGroupId = hasLocCol ? record.get("location_group_id").trim() : "";

                    if (flexStops.contains(stopId) && !locGroupId.isEmpty()) {
                        printer.printRecord(record);
                        activeTrips.add(record.get("trip_id").trim());
                        stopsToKeep.add(stopId);
                    }
                }
            }
            Files.copy(stTmp.toPath(), stopTimesPath, StandardCopyOption.REPLACE_EXISTING);
            stTmp.delete();

            Set<String> activeRoutes = new HashSet<>();
            Set<String> activeServices = new HashSet<>();
            Set<String> activeShapes = new HashSet<>();

            filterTripsAndCollect(zipfs, activeTrips, activeRoutes, activeServices, activeShapes);

            filterFileByIds(zipfs, "routes.txt", "route_id", activeRoutes);
            filterFileByIds(zipfs, "calendar.txt", "service_id", activeServices);
            filterFileByIds(zipfs, "calendar_dates.txt", "service_id", activeServices);
            filterFileByIds(zipfs, "shapes.txt", "shape_id", activeShapes);
            filterFileByIds(zipfs, "location_groups.txt", "location_group_id", flexGroupIds);

            syncStopsWithParents(zipfs, stopsToKeep);
        }
    }

    private void filterTripsAndCollect(FileSystem zipfs, Set<String> activeTrips, Set<String> activeRoutes, Set<String> activeServices, Set<String> activeShapes) throws IOException {
        Path path = zipfs.getPath("trips.txt");
        if (!Files.exists(path)) return;

        byte[] bytes = Files.readAllBytes(path);
        String content = new String(bytes, StandardCharsets.UTF_8);
        CSVFormat format = createBaseFormat(content);
        File tmp = File.createTempFile("trips_flex", ".tmp");

        try (CSVParser parser = format.parse(new StringReader(content));
             CSVPrinter printer = getPrinterForFile(tmp, format)) {

            printer.printRecord(parser.getHeaderNames());
            boolean hasShape = parser.getHeaderMap().containsKey("shape_id");

            for (CSVRecord record : parser) {
                if (activeTrips.contains(record.get("trip_id").trim())) {
                    printer.printRecord(record);
                    activeRoutes.add(record.get("route_id").trim());
                    activeServices.add(record.get("service_id").trim());
                    if (hasShape) activeShapes.add(record.get("shape_id").trim());
                }
            }
        }
        Files.copy(tmp.toPath(), path, StandardCopyOption.REPLACE_EXISTING);
        tmp.delete();
    }

    private void filterFileByIds(FileSystem zipfs, String fileName, String idColumnName, Set<String> allowedIds) throws IOException {
        Path path = zipfs.getPath(fileName);
        if (!Files.exists(path)) return;

        byte[] bytes = Files.readAllBytes(path);
        String content = new String(bytes, StandardCharsets.UTF_8);
        CSVFormat format = createBaseFormat(content);
        File tmp = File.createTempFile("filter_" + fileName, ".tmp");

        try (CSVParser parser = format.parse(new StringReader(content));
             CSVPrinter printer = getPrinterForFile(tmp, format)) {

            printer.printRecord(parser.getHeaderNames());
            for (CSVRecord record : parser) {
                if (allowedIds.contains(record.get(idColumnName).trim())) {
                    printer.printRecord(record);
                }
            }
        }
        Files.copy(tmp.toPath(), path, StandardCopyOption.REPLACE_EXISTING);
        tmp.delete();
    }

    private void syncStopsWithParents(FileSystem zipfs, Set<String> activeStops) throws IOException {
        Path path = zipfs.getPath("stops.txt");
        Set<String> allRequired = new HashSet<>(activeStops);

        try (CSVParser parser = getParserForPath(path)) {
            boolean hasParent = parser.getHeaderMap().containsKey("parent_station");
            for (CSVRecord record : parser) {
                String sid = record.get("stop_id").trim();
                if (activeStops.contains(sid) && hasParent) {
                    String parent = record.get("parent_station").trim();
                    if (!parent.isEmpty()) allRequired.add(parent);
                }
            }
        }
        filterFileByIds(zipfs, "stops.txt", "stop_id", allRequired);
    }

    private static CSVParser getParserForPath(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        String sample = new String(bytes, StandardCharsets.UTF_8);
        String delimiter = FileSystemService.guessDelimiter(sample);

        return CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setHeader()
                .setSkipHeaderRecord(false)
                .setTrim(true)
                .build()
                .parse(new StringReader(sample));
    }

    private static CSVFormat createBaseFormat(String sample) {
        String delimiter = FileSystemService.guessDelimiter(sample);
        return CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setHeader()
                .setSkipHeaderRecord(false)
                .setTrim(true)
                .build();
    }

    private static CSVPrinter getPrinterForFile(File file, CSVFormat format) throws IOException {
        return new CSVPrinter(
                Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8),
                format
        );
    }
}
