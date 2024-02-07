package no.rutebanken.marduk.services.opendatasoft;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public class FileMetadata {
    private String uid;
    private String filename;
    private String mimetype;

    private String created_at;

    // Getters and Setters
    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getMimetype() {
        return mimetype;
    }

    public void setMimetype(String mimetype) {
        this.mimetype = mimetype;
    }

    public String getCreated_at() {
        return created_at;
    }

    public void setCreated_at(String created_at) {
        this.created_at = created_at;
    }

    // toString method for debugging or printing
    @Override
    public String toString() {
        return "FileMetadata{" +
                "uid='" + uid + '\'' +
                ", filename='" + filename + '\'' +
                ", mimetype='" + mimetype + '\'' +
                ", created_at=" + created_at +
                '}';
    }
}
