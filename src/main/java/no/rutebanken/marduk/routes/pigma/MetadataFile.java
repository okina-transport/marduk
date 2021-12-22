package no.rutebanken.marduk.routes.pigma;

import no.rutebanken.marduk.Utils.ExportCsv;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.routes.Producers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class MetadataFile {

    @Autowired
    private Producers producers;

    @Autowired
    private ExportCsv exportCsv;

    @Value("${pigma.metadata.mail:test@test.fr}")
    private String metadataMail;


    private static final String[] FILE_HEADER_PRINTER = new String[]{"Titre", "Description", "Mots clés", "Frequence de mise à jour", "Licence", "Granularité", "Contact", "Titre du fichier", "Nom fichier GTFS", "Nom fichier Netex"};

    private static final String[] FILE_ROW_PRINTER_GENERAL = new String[]{
            "Arrêts horaires et parcours théoriques des réseaux de transport public des membres du syndicat Nouvelle-Aquitaine Mobilités.",
            "Ce jeu de données contient la liste des arrêts des horaires et des parcours théoriques des réseaux de transport public des membres du syndicat Nouvelle-Aquitaine Mobilités.",
            "#NETEX#GTFS#données-ouvertes",
            "hebdomadaire",
            "Open Data Commons Open Database License (OdbL)",
            "Région",
            "",
            "RESEAUX-NOUVELLE-AQUITAINE-MOBILITES",
            "naq-aggregated-gtfs.zip",
            "naq-aggregated-netex.zip"
    };

    private static final String[] FILE_ROW_PRINTER_PRODUCER = new String[]{
            "Arrêts horaires et parcours théoriques des bus du réseau de transport public",
            "Ce jeu de données contient la liste des arrêts des horaires et des parcours théoriques du réseau de transport public",
            "#NETEX#GTFS#données-ouvertes",
            "hebdomadaire",
            "Open Data Commons Open Database License (OdbL)",
            "Réseau ",
            "",
            ""
    };

    private static final String[] FILE_ROW_PRINTER_STOPS = new String[]{
            "Arrêts des réseaux de transport public des membres du syndicat Nouvelle-Aquitaine Mobilités.",
            "Ce jeu de données contient la liste des arrêts des réseaux de transport public des membres du syndicat Nouvelle-Aquitaine Mobilités.",
            "#NETEX#GTFS#données-ouvertes",
            "hebdomadaire",
            "Open Data Commons Open Database License (OdbL)",
            "Région",
            "",
            "RESEAUX-NOUVELLE-AQUITAINE-MOBILITES",
            "",
            "naq-stops-netex.zip"
    };

    public File createMetadataFile(String nameFile, ArrayList<BlobStoreFiles.File> listBlobStoreFiles) {
        String[] data;
        ArrayList<String[]> rows = new ArrayList<>();

        List<String> listPrefix =
                listBlobStoreFiles
                        .stream()
                        .filter(file -> file.getReferential() != null)
                        .map(BlobStoreFiles.File::getReferential)
                        .distinct()
                        .collect(Collectors.toList());

        for (String prefix : listPrefix) {
            prefix = prefix.replaceFirst("mobiiti_", "");
            String producerName = producers.producersListName().get(prefix);
            String type = producers.producersTransportTypeList().get(prefix);

            String title = FILE_ROW_PRINTER_PRODUCER[0] + " " + type + " de " + producerName;
            String description = FILE_ROW_PRINTER_PRODUCER[1] + " " + type + " de " + producerName;
            String granularite = producers.producersGranulariteList().get(prefix);
            String titleFile = FILE_ROW_PRINTER_PRODUCER[5] + producerName;
            String titleGtfs = prefix + "-aggregated-gtfs.zip";
            String titleNetex = prefix + "-aggregated-netex.zip";

            FILE_ROW_PRINTER_GENERAL[6] = metadataMail;
            FILE_ROW_PRINTER_STOPS[6] = metadataMail;

            data = new String[]{title, description, FILE_ROW_PRINTER_PRODUCER[2], FILE_ROW_PRINTER_PRODUCER[3], FILE_ROW_PRINTER_PRODUCER[4], granularite, metadataMail, titleFile, titleGtfs, titleNetex};
            rows.add(data);
        }

        data = FILE_ROW_PRINTER_GENERAL;
        rows.add(data);
        data = FILE_ROW_PRINTER_STOPS;
        rows.add(data);

        return exportCsv.createCSVFile(nameFile, FILE_HEADER_PRINTER, rows);
    }

}
