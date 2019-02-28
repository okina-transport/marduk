package no.rutebanken.marduk.routes.pigma;

import no.rutebanken.marduk.Utils.ExportCsv;
import no.rutebanken.marduk.routes.Producers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;

@Component
public class CreateMetadataFile {

    @Autowired
    private Producers producers;

    @Autowired
    private ExportCsv exportCsv;


    private static final String[] FILE_HEADER_PRINTER = new String[]{"Titre", "Description", "Mots clés", "Frequence de mise à jour", "Licence", "Titre du fichier", "Nom fichier GTFS", "Nom fichier Netex"};

    private static final String[] FILE_ROW_PRINTER_GENERAL = new String[]{
            "Arrêts, horaires et parcours théoriques des réseaux de transport public des membres du syndicat Nouvelle-Aquitaine Mobilités.",
            "Ce jeu de données contient la liste des arrêts, des horaires et des parcours théoriques des réseaux de transport public des membres du syndicat Nouvelle-Aquitaine Mobilités.",
            "#NETEX#GTFS#données-ouvertes",
            "hebdomadaire",
            "Open Data Commons Open Database License (OdbL)",
            "RESEAUX-NOUVELLE-AQUITAINE-MOBILITES",
            "naq-aggregated-gtfs.zip",
            "naq-aggregated-netex.zip"
    };

    private static final String[] FILE_ROW_PRINTER_PRODUCER = new String[]{
            "Arrêts horaires et parcours théoriques des bus du réseau des transports publics ",
            "Ce jeu de données contient la liste des arrêts, des horaires et des parcours théoriques du réseau des transports publics ",
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
            "RESEAUX-NOUVELLE-AQUITAINE-MOBILITES",
            "",
            "naq-stops-netex.zip"
    };

    public File createMetadataFile(String nameFile, String nameMetadataFile, String referential){
        String[] data;
        ArrayList<String[]> rows = new ArrayList<>();

        if(!nameFile.equals("CurrentAndFuture_latest.zip") && referential == null){
            data = FILE_ROW_PRINTER_GENERAL;
            rows.add(data);
        }
        else if (nameFile.equals("CurrentAndFuture_latest.zip")){
            nameMetadataFile = "naq-stops-netex-metadonnes.csv";
            data = FILE_ROW_PRINTER_STOPS;
            rows.add(data);
        }
        else{
            String[] prefixParts = referential.split("_");
            String producerName = producers.producersListName().get(prefixParts[1]);
            String title = FILE_ROW_PRINTER_PRODUCER[0] + producerName;
            String description = FILE_ROW_PRINTER_PRODUCER[1] + producerName;
            String titleFile = FILE_ROW_PRINTER_PRODUCER[5] + producerName;
            String titleGtfs = referential + "-aggregated-gtfs.zip";
            String titleNetex = referential + "-aggregated-netex.zip";

            data = new String[]{title, description, FILE_ROW_PRINTER_PRODUCER[2], FILE_ROW_PRINTER_PRODUCER[3], FILE_ROW_PRINTER_PRODUCER[4], titleFile, titleGtfs, titleNetex};
            rows.add(data);
        }


        return exportCsv.createCSVFile(nameMetadataFile, FILE_HEADER_PRINTER, rows);
    }



}
