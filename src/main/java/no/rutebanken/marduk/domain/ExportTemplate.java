package no.rutebanken.marduk.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import static java.util.stream.Collectors.toList;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExportTemplate implements Serializable {

    private Long id;
    private String name;
    private String description;
    private ExportType type;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime endDate;

    private List<Line> lines;
    private List<Consumer> consumers;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ExportType getType() {
        return type;
    }

    public void setType(ExportType type) {
        this.type = type;
    }

    public List<Line> getLines() {
        return lines;
    }

    public void setLines(List<Line> lines) {
        this.lines = lines;
    }


    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public List<Consumer> getConsumers() {
        return consumers;
    }

    public void setConsumers(List<Consumer> consumers) {
        this.consumers = consumers;
    }

    /**
     * Détermine si le fichier exporté correspondant à cet export doit être accessible en public
     * Un export est public si au moins un de ses consommateurs définit l'accès à public
     * @return
     */
    public boolean hasExportFilePublicAccess() {
        List<Consumer> consumersWithPublicAccess = this.getConsumers().stream().filter(c -> c.isPublicExport()).collect(toList());
        return !consumersWithPublicAccess.isEmpty();

    }

    /**
     * Renvoie une liste d'exports dont l'accès au fichier exporté correspond à publicAccess
     * Si accès public: un export est public si au moins un de ses consommateurs définit l'accès à public
     * Si accès non public: un export est privé si tous ses consommateurs définissent un accès privé
     * @param exports
     * @param publicAccess
     * @return
     */
    public static List<ExportTemplate> filterExportsByPublicAccess(List<ExportTemplate> exports, boolean publicAccess) {
        return exports.stream().filter(e -> publicAccess == e.hasExportFilePublicAccess()).collect(toList());
    }


    public static List<ExportTemplate> publicAccessExports(List<ExportTemplate> exports) {
        return filterExportsByPublicAccess(exports, true);
    }

    public static List<ExportTemplate> privateAccessExports(List<ExportTemplate> exports) {
        return filterExportsByPublicAccess(exports, false);
    }
}
