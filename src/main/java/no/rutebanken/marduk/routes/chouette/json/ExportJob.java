package no.rutebanken.marduk.routes.chouette.json;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "exportJob")
@XmlAccessorType(XmlAccessType.FIELD)
public class ExportJob {

    private Long id;
    private String jobUrl;
    private String fileName;
    private String subFolder;
//    private String message;
//    private Instant started;
//    private Instant finished;
//    private JobStatus status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getJobUrl() {
        return jobUrl;
    }

    public void setJobUrl(String jobUrl) {
        this.jobUrl = jobUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getSubFolder() {
        return subFolder;
    }

    public void setSubFolder(String subFolder) {
        this.subFolder = subFolder;
    }

//    public String getMessage() {
//        return message;
//    }
//
//    public void setMessage(String message) {
//        this.message = message;
//    }
//
//    public Instant getStarted() {
//        return started;
//    }
//
//    public void setStarted(Instant started) {
//        this.started = started;
//    }
//
//    public Instant getFinished() {
//        return finished;
//    }
//
//    public void setFinished(Instant finished) {
//        this.finished = finished;
//    }
//
//    public JobStatus getStatus() {
//        return status;
//    }
//
//    public void setStatus(JobStatus status) {
//        this.status = status;
//    }
}
