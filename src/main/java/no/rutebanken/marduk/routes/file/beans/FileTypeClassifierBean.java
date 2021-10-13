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

package no.rutebanken.marduk.routes.file.beans;

import no.rutebanken.marduk.exceptions.FileValidationException;
import no.rutebanken.marduk.routes.chouette.json.importer.ImportMode;
import no.rutebanken.marduk.routes.file.FileType;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import org.apache.camel.Exchange;
import org.apache.commons.codec.CharEncoding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.routes.file.FileType.GTFS;
import static no.rutebanken.marduk.routes.file.FileType.INVALID_FILE_NAME;
import static no.rutebanken.marduk.routes.file.FileType.NEPTUNE;
import static no.rutebanken.marduk.routes.file.FileType.NETEXPROFILE;
import static no.rutebanken.marduk.routes.file.FileType.RAR;
import static no.rutebanken.marduk.routes.file.FileType.REGTOPP;
import static no.rutebanken.marduk.routes.file.FileType.ZIP_WITH_SINGLE_FOLDER;
import static no.rutebanken.marduk.routes.file.beans.FileClassifierPredicates.firstElementQNameMatchesNetex;
import static no.rutebanken.marduk.routes.file.beans.FileClassifierPredicates.validateZipContent;

public class FileTypeClassifierBean {

    private static final Logger logger = LoggerFactory.getLogger(FileTypeClassifierBean.class);

    private static final String requiredRegtoppFilesExtensionsRegex = "(?i).+\\.tix|(?i).+\\.hpl|(?i).+\\.dko";
    private static final String requiredGtfsFilesRegex = "agency.txt|stops.txt|routes.txt|trips.txt|stop_times.txt";
    private static final String requiredNeptuneFilesRegex = "[A-Z]{1,}\\-Line\\-[0-9].*\\.xml";
    private static final String xmlFilesRegex = ".+\\.xml";    //TODO can we be more specific?

    public static final String NON_XML_FILE_XML = ".*\\.(?!XML$|xml$)[^.]+";

    private final static ZipFileUtils zipFileUtils = new ZipFileUtils();

    public boolean validateFile(byte[] data, Exchange exchange) {
        try {
            FileType fileType = classifyFile(exchange, data);
            logger.debug("File is classified as " + fileType);
            exchange.getIn().setHeader(FILE_TYPE, fileType.name());
            return true;
        } catch (RuntimeException e) {
            logger.warn("Exception while trying to classify file ", e);
            return false;
        }
    }

    boolean isValidFileName(String fileName) {
        return Charset.forName(CharEncoding.ISO_8859_1).newEncoder().canEncode(fileName);
    }

    public FileType classifyFile(Exchange exchange,byte[] data) {
        String relativePath = exchange.getIn().getHeader(FILE_HANDLE, String.class);
        logger.debug("Validating file with path '" + relativePath + "'.");

            if (relativePath == null || relativePath.trim().equals("")) {
                throw new IllegalArgumentException("Could not get file path from " + FILE_HANDLE + " header.");
            }


        String importType = exchange.getIn().getHeader(IMPORT_TYPE, String.class);

        if (relativePath.toUpperCase().endsWith(".ZIP")) {
            Set<String> filesNamesInZip = zipFileUtils.listFilesInZip(new ByteArrayInputStream(data));
            exchange.getIn().setHeader(IMPORT_MODE, getImportMode(filesNamesInZip).toString());
            if (!isValidFileName(relativePath)) {
                return INVALID_FILE_NAME;
            } else if (NEPTUNE.toString().equalsIgnoreCase(importType)) {
                return NEPTUNE;
            } else if (isRegtoppZip(filesNamesInZip)) {
                return REGTOPP;
            } else if (GTFS.toString().equalsIgnoreCase(importType) || isGtfsZip(filesNamesInZip)) {
                return GTFS;
            } else if (NETEXPROFILE.toString().equalsIgnoreCase(importType) || isNetexZip(filesNamesInZip, new ByteArrayInputStream(data))) {
                return NETEXPROFILE;
            } else if (ZipFileUtils.zipFileContainsSingleFolder(data)) {
                return ZIP_WITH_SINGLE_FOLDER;
            }
            throw new FileValidationException("Could not classify zip file '" + relativePath + "'.");
        } else if (relativePath.toUpperCase().endsWith(".RAR")) {
            return RAR;
        }
        throw new FileValidationException("Could not classify file '" + relativePath + "'.");
    }

    /**
     * Determine the import mode by reading file names
     * @param filesNamesInZip
     * @return
     * - LINE : at least one line file is existing in the zip
     * - STOPAREAS : only common files are in the zip
     */
    private ImportMode getImportMode(Set<String> filesNamesInZip){

        List<String> lineFiles = filesNamesInZip.stream()
                                                  .filter(filename -> ! isCommonFile(filename))
                                                  .collect(Collectors.toList());
        return lineFiles.isEmpty() ? ImportMode.STOPAREAS : ImportMode.LINE;
    }

    /**
     * Define if a file is a common file (calendar/stops/common) or a line file.
     * (currently defined by name but later, replaced by an intelligent process that will read the file to understand its type)
     * @param fileName
     *     The name of the file
     * @return
     *     true : the file is common
     *     false : the file is not common (it is a line file)
     */
    public static boolean isCommonFile(String fileName){

        String fileNameLowerCase = fileName.toLowerCase();
        List<String> commonFiles = Arrays.asList("calendriers.xml", "commun.xml","arrets.xml","reseaux.xml");

        //mobiiti stops file has a custom file name starting with "ARRET_"
        return fileNameLowerCase.startsWith("arret_") || commonFiles.stream()
                .anyMatch(fileNameLowerCase::equals) ;
    }


    public static boolean isRegtoppZip(Set<String> filesInZip) {
        return filesInZip.stream().anyMatch(p -> p.matches(requiredRegtoppFilesExtensionsRegex));
    }

    public static boolean isGtfsZip(final Set<String> filesInZip) {
        return filesInZip.stream().anyMatch(p -> p.matches(requiredGtfsFilesRegex));
    }

    public static boolean isNeptuneZip(final Set<String> filesInZip) {
        return filesInZip.stream().anyMatch(p -> p.matches(requiredNeptuneFilesRegex));
    }

    public static boolean isNetexZip(final Set<String> filesInZip, InputStream inputStream) {
        return filesInZip.stream().anyMatch(p -> p.matches(xmlFilesRegex)) //TODO skip file extension check unless it can be more specific?
                && isNetexXml(inputStream);
    }

    private static boolean isNetexXml(InputStream inputStream) {
        try {
            return validateZipContent(inputStream, firstElementQNameMatchesNetex(), NON_XML_FILE_XML);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
