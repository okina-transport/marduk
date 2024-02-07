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
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipFileUtils {
    private static Logger logger = LoggerFactory.getLogger(ZipFileUtils.class);

    public Set<String> listFilesInZip(File file) {
        try (ZipFile zipFile = new ZipFile(file)) {
            return zipFile.stream().filter(ze -> !ze.isDirectory()).map(ze -> ze.getName()).collect(Collectors.toSet());
        } catch (IOException e) {
            return Collections.emptySet();
        }
    }

    public Set<String> listFilesInZip(byte[] data) {
        return listFilesInZip(new ByteArrayInputStream(data));
    }

    public Set<String> listFilesInZip(InputStream inputStream) {
        Set<String> fileNames = new HashSet<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry zipEntry = zipInputStream.getNextEntry();
            while (zipEntry != null) {
                fileNames.add(zipEntry.getName());
                zipEntry = zipInputStream.getNextEntry();
            }
            return fileNames;
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptySet();
        }
    }

    public static boolean zipFileContainsSingleFolder(byte[] data) {

        try {
            return zipFileContainsSingleFolder(getFile(data));
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean zipFileContainsSingleFolder(File inputFile) {
        try {
            return getZipFileIfSingleFolder(inputFile) != null;
        } catch (IOException e) {
            e.printStackTrace();
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
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(tmpFile));

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
        out.close();

        logger.info("File written to : " + tmpFile.getAbsolutePath());

        return tmpFile;
    }


    public static File zipFilesInFolder(String folder, String targetFilePath) {
        try {

            FileOutputStream out = new FileOutputStream(new File(targetFilePath));
            ZipOutputStream outZip = new ZipOutputStream(out);

            FileUtils.listFiles(new File(folder), null, true).stream().forEach(file -> addToZipFile(file, outZip));

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
        try (FileSystem fs = FileSystems.newFileSystem(Paths.get(zipFile.getPath()), null)) {
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


    private static File getFile(byte[] data) throws IOException {
        File inputFile = File.createTempFile("marduk-input", ".zip");

        FileOutputStream fos = new FileOutputStream(inputFile);
        fos.write(data);
        fos.close();
        return inputFile;
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
                    for (int read = fileInputStream.read(buffer); read > -1; read = ((InputStream) fileInputStream).read(buffer)) {
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

}
