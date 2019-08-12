package no.rutebanken.marduk.routes.file;

import no.rutebanken.marduk.exceptions.MardukException;
import org.apache.camel.Exchange;
import org.apache.commons.io.IOUtils;
import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.FileItemFactory;
import org.apache.tomcat.util.http.fileupload.UploadContext;
import org.apache.tomcat.util.http.fileupload.disk.DiskFileItemFactory;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static no.rutebanken.marduk.Constants.DESCRIPTION;
import static no.rutebanken.marduk.Constants.USER;

public class FileInformations {

    public static void getObjectUpload(Exchange e) throws IOException {
        byte[] bytes;
        try {
            bytes = IOUtils.toByteArray(e.getIn().getBody(InputStream.class));
        } catch (Exception ex) {
            throw new MardukException("Failed to parse multipart content: " + ex.getMessage());
        }

        String informations = "";

        try {
            InputStream inputStream = new ByteArrayInputStream(bytes);
            informations = IOUtils.toString(inputStream, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            ex.printStackTrace();
        }

        int indexOfUser = informations.lastIndexOf("Content-Disposition: form-data; name=\"user\"");
        if (indexOfUser != -1) {
            String user = informations.substring(indexOfUser);
            indexOfUser = user.indexOf("------WebKitFormBoundary");
            user = user.substring(0, indexOfUser);
            user = user.substring(user.lastIndexOf('"') + 1);
            user = user.trim();
            e.getIn().setHeader(USER, user);
        }

        int indexOfDescription = informations.lastIndexOf("Content-Disposition: form-data; name=\"description\"");
        if (indexOfDescription != -1) {
            String description = informations.substring(indexOfDescription);
            indexOfDescription = description.indexOf("------WebKitFormBoundary");
            description = description.substring(0, indexOfDescription);
            description = description.substring(description.lastIndexOf('"') + 1);
            description = description.trim();
            e.getIn().setHeader(DESCRIPTION, description);
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
            e.getIn().setBody(fileItems);
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
