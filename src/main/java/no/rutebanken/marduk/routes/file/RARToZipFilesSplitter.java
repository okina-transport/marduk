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

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;
import no.rutebanken.marduk.routes.file.beans.FileTypeClassifierBean;
import org.apache.camel.Body;
import org.apache.camel.Exchange;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class RARToZipFilesSplitter {

    private RARToZipFilesSplitter() {
        throw new IllegalStateException("Utility class");
    }

	private static final Logger LOGGER = LoggerFactory.getLogger(RARToZipFilesSplitter.class);

	public static List<Object> splitRarFile(@Body Object b, Exchange exchange) {

		LOGGER.info("Splitting rar file");

		List<Object> zipFileObjects = new ArrayList<>();

		// Create tmp file on disk with content
		File tmpFolder = new File(System.getProperty("java.io.tmpdir"));
		File rarExtractFolder = new File(tmpFolder, UUID.randomUUID().toString());
		rarExtractFolder.mkdirs();

		File rarFile = new File(tmpFolder, UUID.randomUUID().toString());

		try {
			rarFile.createNewFile();
			try (FileOutputStream fos = new FileOutputStream(rarFile)) {

                switch (b) {
                    case byte[] byteArray -> fos.write(byteArray);
                    case InputStream inputStream -> IOUtils.copyLarge(inputStream, fos);
                    default -> throw new RuntimeException("Cannot handle body of type " + b);
                }

                LOGGER.info("Processing RAR file with length {}", rarFile.length());

                // Unpack to new folder
                try (Archive a = new Archive(rarFile)) {
                    FileHeader fh = a.nextFileHeader();
                    while (fh != null) {
                        File out = new File(rarExtractFolder, fh.getFileNameString().trim().replace('\\', '/'));
                        File parentFolder = out.getParentFile();
                        parentFolder.mkdirs();

                        if (!out.isDirectory()) {
                            FileOutputStream os = new FileOutputStream(out);
                            a.extractFile(fh, os);
                            os.close();
                        }
                        fh = a.nextFileHeader();
                    }
                }

                // Iterate content in folder
                zipFileObjects.addAll(processFolder(rarExtractFolder, exchange));
            }
		} catch (Exception e) {
			LOGGER.error("Error extracting RAR file", e);
		}

		return zipFileObjects;
	}

	public static List<Object> processFolder(File folder, Exchange exchange) throws IOException, RarException {
		LOGGER.info("Scanning directory {}", folder.getAbsolutePath());
		List<Object> zipFileObjects = new ArrayList<>();

		boolean regtoppZip = FileTypeClassifierBean.isRegtoppZip(new HashSet<>(Arrays.asList(folder.list())));
		if (regtoppZip) {
			LOGGER.info("Directory {} is a Regtopp folder", folder.getAbsolutePath());

			// Zip files together
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			ZipOutputStream zos = new ZipOutputStream(os);
			for (File f : folder.listFiles()) {
				ZipEntry entry = new ZipEntry(f.getName());
				zos.putNextEntry(entry);

				try (FileInputStream fis = new FileInputStream(f)) {
					IOUtils.copyLarge(fis, zos);
				}
			}
			zos.close();
			zipFileObjects.add(os.toByteArray());
			LOGGER.info("Zipped files in directory {}", folder.getAbsolutePath());
		} else {
			LOGGER.info("Not a Regtopp directory {}, scanning content", folder.getAbsolutePath());

			for (File f : folder.listFiles()) {
				LOGGER.info("Checking file {}", f.getAbsolutePath());

				if (f.isFile() && f.getName().toUpperCase().endsWith(".ZIP")) {
					// Already zipped here
					try (FileInputStream fis = new FileInputStream(f)) {
						byte[] byteArray = IOUtils.toByteArray(fis);
						zipFileObjects.add(byteArray);
					}
					LOGGER.info("Added existing zip file {}", f.getAbsolutePath());

				} else if (f.isFile() && f.getName().toUpperCase().endsWith(".RAR")) {
					// Embedded rar
					try (FileInputStream fis = new FileInputStream(f)) {
						byte[] byteArray = IOUtils.toByteArray(fis);
						zipFileObjects.add(splitRarFile(byteArray, exchange));
					}
					LOGGER.info("Added result of expanding embeddced rar file {}", f.getAbsolutePath());

				} else if (f.isDirectory()) {
					// Recurse
					LOGGER.info("Recursing into directory {}", f.getAbsolutePath());
					zipFileObjects.addAll(processFolder(f, exchange));
				}
			}
		}
		return zipFileObjects;
	}

}
