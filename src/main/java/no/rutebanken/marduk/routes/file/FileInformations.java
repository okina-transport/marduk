package no.rutebanken.marduk.routes.file;

import no.rutebanken.marduk.exceptions.MardukException;
import org.apache.camel.Exchange;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.FileItemFactory;
import org.apache.tomcat.util.http.fileupload.UploadContext;
import org.apache.tomcat.util.http.fileupload.disk.DiskFileItem;
import org.apache.tomcat.util.http.fileupload.disk.DiskFileItemFactory;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static no.rutebanken.marduk.Constants.CLEAN_INPUT_NETEX_ZIP;

public class FileInformations {

    public static void getObjectUpload(Exchange e) {
        byte[] bytes;
        try {
            bytes = IOUtils.toByteArray(e.getIn().getBody(InputStream.class));
        } catch (Exception ex) {
            throw new MardukException("Failed to parse multipart content: " + ex.getMessage());
        }

        convertBodyToFileItems(e, bytes);
    }

    private static void convertBodyToFileItems(Exchange e, byte[] byteArray) {
        FileItemFactory factory = new DiskFileItemFactory();
        ServletFileUpload upload = new ServletFileUpload(factory);
        List<FileItem> fileItems;

        try {
            fileItems = upload.parseRequest(new SimpleUploadContext(StandardCharsets.UTF_8, e.getIn().getHeader(Exchange.CONTENT_TYPE, String.class), byteArray));
            fileItems.removeIf(fileItem -> fileItem.getName() == null);
            if (CollectionUtils.isNotEmpty(fileItems) && BooleanUtils.isTrue((Boolean) e.getIn().getHeader(CLEAN_INPUT_NETEX_ZIP))) {
                String targetFilename = fileItems.get(0).getName();
                String storeLocation = ((DiskFileItem) fileItems.get(0)).getStoreLocation().getAbsolutePath();
                FileItem file = factory.createItem("file", "application/zip", false, targetFilename);
                OutputStream outputStream = file.getOutputStream();
                ZipFileUtils.copyZipFileWithoutUnwantedFiles(Path.of(storeLocation), outputStream, ".xml");
                e.getIn().setBody(List.of(file));
            } else {
                e.getIn().setBody(fileItems);
            }
        } catch (Exception ex) {
            throw new MardukException("Failed to parse File multipart content: " + ex.getMessage());
        }
    }


    /**
     * Wrapper class for passing form multipart body to ServletFileUpload parser.
     */
    public static class SimpleUploadContext implements UploadContext {
        private final Charset charset;
        private final String contentType;
        private final byte[] content;

        public SimpleUploadContext(Charset charset, String contentType, byte[] content) {
            this.charset = charset;
            this.contentType = contentType;
            this.content = content;
        }

        public String getCharacterEncoding() {
            return charset.displayName();
        }

        public String getContentType() {
            return contentType;
        }

        @Override
        public long contentLength() {
            return content.length;
        }

        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(content);
        }
    }

}
