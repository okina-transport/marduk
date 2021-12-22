package no.rutebanken.marduk.Utils;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

@Component
public class ExportCsv {

    /**
     * Délimiteur CSV
     */
    public static final String NEW_LINE_SEPARATOR = "\n";
    public static final char NEW_COLUMN_SEPARATOR = ';';

    // Create the CSVFormat object with "\n" as a record delimiter
    CSVFormat csvFileFormat = CSVFormat.DEFAULT.withRecordSeparator(NEW_LINE_SEPARATOR).withDelimiter(NEW_COLUMN_SEPARATOR);


    public File createCSVFile(String nameFile, String[] header, ArrayList<String[]> rows) {

        File csvFile = null;
        CSVPrinter csvFilePrinter;
        Writer fstream;

        try {
            csvFile = new File(nameFile);
            fstream = new OutputStreamWriter(new FileOutputStream(csvFile), StandardCharsets.UTF_8);
            csvFilePrinter = new CSVPrinter(fstream, csvFileFormat);

            csvFilePrinter.printRecord(header);
            for (String[] row : rows) {
                csvFilePrinter.printRecord(row);
            }

            csvFilePrinter.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return csvFile;
    }
}
