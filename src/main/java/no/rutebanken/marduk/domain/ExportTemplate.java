package no.rutebanken.marduk.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.util.List;

import static java.util.stream.Collectors.toList;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExportTemplate implements Serializable {

    private Long id;
    private String name;
    private String description;
    private ExportType type;

    private Long startDate;
    private Long endDate;

    private String exportedFileName;

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

    public String getExportedFileName() {
        return exportedFileName;
    }

    public void setExportedFileName(String exportedFileName) {
        this.exportedFileName = exportedFileName;
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


    public Long getStartDate() {
        return startDate;
    }

    public void setStartDate(Long startDate) {
        this.startDate = startDate;
    }

    public Long getEndDate() {
        return endDate;
    }

    public void setEndDate(Long endDate) {
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
