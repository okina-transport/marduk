package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Utils.SendMail;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.JOB_STATUS_JOB_VALIDATION_LEVEL;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.EXPORT_NAME;
import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.GENERATE_MAP_MATCHING;
import static no.rutebanken.marduk.Constants.RECIPIENTS;
import static no.rutebanken.marduk.routes.status.JobEvent.TimetableAction.VALIDATION_LEVEL_1;
import static no.rutebanken.marduk.routes.status.JobEvent.TimetableAction.VALIDATION_LEVEL_2;

@Component
public class CreateMail {

    @Autowired
    SendMail sendMail;

    @Value("${client.name}")
    private String client;

    @Value("${server.name}")
    private String server;

    public static final String SIGNING = "<br><br>Cordialement,<br>L'équipe Mobi-iti";

    public void createMail(Exchange e, String format, JobEvent.TimetableAction timetableAction, boolean ok) {
        String recipientString = e.getIn().getHeader(RECIPIENTS, String.class);
        String[] recipients = recipientString != null ? recipientString.trim().split(",") : null;
        String referential = e.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class);
        String exportName = e.getIn().getHeader(EXPORT_NAME, String.class);
        String fileName = e.getIn().getHeader(FILE_NAME, String.class);
        String levelValidation= null;
        if(VALIDATION_LEVEL_1.equals(timetableAction) || JobEvent.TimetableAction.VALIDATION_LEVEL_2.equals(timetableAction) ){
            levelValidation = e.getIn().getHeader(JOB_STATUS_JOB_VALIDATION_LEVEL, String.class).equals(VALIDATION_LEVEL_2.name()) ?  "2" : "1";
        }
        if(recipients != null) {
            for (String recipient : recipients) {
                if (org.apache.commons.lang3.StringUtils.isNotEmpty(recipient)) {
                    if(ok){
                        sendMailOk(e, format, timetableAction, referential, recipient, exportName, fileName);
                    }
                    else {
                        sendMailFailed(format, timetableAction, referential, recipient, exportName, fileName, levelValidation);
                    }
                }
            }
        }
    }

    private void sendMailFailed(String format, JobEvent.TimetableAction timetableAction, String referential, String recipient, String exportName, String fileName, String levelValidation){
        if(timetableAction == null){
            sendMail.sendEmail("[" + client.toUpperCase() + " - " + server.toUpperCase() + "] Referentiel Mobi-iti - Nouvelle integration de donnees du reseau de " + referential,
                    recipient,
                    "Bonjour,"
                            + "<br>L'import automatique du fichier : " + fileName + " a échoué. Veuillez contacter un administrateur."
                            + SIGNING,
                    null);
        }
        if(JobEvent.TimetableAction.FILE_ANALYZE.equals(timetableAction)){
            sendMail.sendEmail("[" + client.toUpperCase() + " - " + server.toUpperCase() + "] Referentiel Mobi-iti - Nouvelle integration de donnees du reseau de " + referential,
                    recipient,
                    "Bonjour,"
                            + "<br>L'analyse du fichier : " + fileName + " a échoué."
                            + SIGNING,
                    null);
        }
        if(JobEvent.TimetableAction.IMPORT.equals(timetableAction)){
            sendMail.sendEmail("[" + client.toUpperCase() + " - " + server.toUpperCase() + "] Referentiel Mobi-iti - Nouvelle integration de donnees du reseau de " + referential,
                    recipient,
                    "Bonjour,"
                            + "<br>L'import du fichier : " + fileName + " a échoué."
                            + SIGNING,
                    null);
        }
        if(VALIDATION_LEVEL_1.equals(timetableAction) || JobEvent.TimetableAction.VALIDATION_LEVEL_2.equals(timetableAction) ){
            sendMail.sendEmail("[" + client.toUpperCase() + " - " + server.toUpperCase() + "] Referentiel Mobi-iti - Nouvelle integration de donnees du reseau de " + referential,
                    recipient,
                    "Bonjour,"
                            + "<br>La validation de niveau " + levelValidation + " du fichier : " + fileName + " a échoué."
                            + SIGNING,
                    null);
        }
        if(JobEvent.TimetableAction.BUILD_MAP_MATCHING.equals(timetableAction)){
            sendMail.sendEmail("[" + client.toUpperCase() + " - " + server.toUpperCase() + "] Referentiel Mobi-iti - Nouvelle integration de donnees du reseau de " + referential,
                    recipient,
                    "Bonjour,"
                            + "<br>La génération des tracés du fichier : " + fileName + " a échoué."
                            + SIGNING,
                    null);
        }
        if(StringUtils.equals(format, "GTFS")){
            if(JobEvent.TimetableAction.EXPORT.equals(timetableAction)){
                sendMail.sendEmail("[" + client.toUpperCase() + " - " + server.toUpperCase() + "] Referentiel Mobi-iti - Nouvelle integration de donnees du reseau de " + referential,
                        recipient,
                        "Bonjour,"
                                + "<br>L'export GTFS : " + exportName + " suite à l'import du fichier : " + fileName + " a échoué."
                                + SIGNING,
                        null);
            }
        }
        if(StringUtils.equals(format, "NEPTUNE")){
            if(JobEvent.TimetableAction.EXPORT.equals(timetableAction)){
                sendMail.sendEmail("[" + client.toUpperCase() + " - " + server.toUpperCase() + "] Referentiel Mobi-iti - Nouvelle integration de donnees du reseau de " + referential,
                        recipient,
                        "Bonjour,"
                                + "<br>L'export Neptune : " + exportName + " suite à l'import du fichier : " + fileName + " a échoué."
                                + SIGNING,
                        null);
            }
        }
        if(StringUtils.equals(format, "NETEX")){
            if(JobEvent.TimetableAction.EXPORT_NETEX.equals(timetableAction)){
                sendMail.sendEmail("[" + client.toUpperCase() + " - " + server.toUpperCase() + "] Referentiel Mobi-iti - Nouvelle integration de donnees du reseau de " + referential,
                        recipient,
                        "Bonjour,"
                                + "<br>L'export Netex : " + exportName + " suite à l'import du fichier : " + fileName + " a échoué."
                                + SIGNING,
                        null);
            }
        }
        if(StringUtils.equals(format, "")){
            sendMail.sendEmail("[" + client.toUpperCase() + " - " + server.toUpperCase() + "] Referentiel Mobi-iti - Nouvelle integration de donnees du reseau de " + referential,
                    recipient,
                    "Bonjour,"
                            + "<br>Le workflow de l'import automatique concernant le fichier : " + fileName + " doit être défini jusqu'à l'export mais il semblerait qu'aucun export automatique ne soit configuré."
                            + SIGNING,
                    null);
        }
    }

    private void sendMailOk(Exchange e, String format, JobEvent.TimetableAction timetableAction, String referential, String recipient, String exportName, String fileName) {
        if(JobEvent.TimetableAction.FILE_ANALYZE.equals(timetableAction)){
            sendMail.sendEmail("[" + client.toUpperCase() + " - " + server.toUpperCase() + "] Referentiel Mobi-iti - Nouvelle integration de donnees du reseau de " + referential,
                    recipient,
                    "Bonjour,"
                            + "<br>L'analyse du fichier: " + fileName + " s'est correctement effectuée."
                            + SIGNING,
                    null);
        }
        if(JobEvent.TimetableAction.IMPORT.equals(timetableAction)){
            sendMail.sendEmail("[" + client.toUpperCase() + " - " + server.toUpperCase() + "] Referentiel Mobi-iti - Nouvelle integration de donnees du reseau de " + referential,
                    recipient,
                    "Bonjour,"
                            + "<br>L'import du fichier: " + fileName + " s'est correctement effectué."
                            + SIGNING,
                    null);
        }
        if(VALIDATION_LEVEL_1.equals(timetableAction) || JobEvent.TimetableAction.VALIDATION_LEVEL_2.equals(timetableAction) ){
            String message = null;
            if (e.getIn().getHeader(JOB_STATUS_JOB_VALIDATION_LEVEL, String.class).equals(VALIDATION_LEVEL_2.name())) {
                if (e.getIn().getHeader(GENERATE_MAP_MATCHING, Boolean.class).equals(true)) {
                    message = "L'import, la validation niveau 1, la génération des tracés, le transfert et la validation niveau 2 du fichier : " + fileName + " se sont correctement effectués.";
                } else {
                    message = "L'import, la validation niveau 1, le transfert et la validation niveau 2 du fichier : " + fileName + " se sont correctement effectués.";
                }
            }

            if (e.getIn().getHeader(JOB_STATUS_JOB_VALIDATION_LEVEL, String.class).equals(VALIDATION_LEVEL_1.name())){
                if(e.getIn().getHeader(GENERATE_MAP_MATCHING, Boolean.class).equals(true)){
                    message = "L'import, la validation niveau 1 et la génération des tracés du fichier : " + fileName + " se sont correctement effectués.";
                } else{
                    message = "L'import et la validation niveau 1 du fichier : " + fileName + " se sont correctement effectués.";
                }
            }
            sendMail.sendEmail("[" + client.toUpperCase() + " - " + server.toUpperCase() + "] Referentiel Mobi-iti - Nouvelle integration de donnees du reseau de " + referential,
                    recipient,
                    "Bonjour," + "<br>" + message + SIGNING,
                    null);
        }
        if(JobEvent.TimetableAction.BUILD_MAP_MATCHING.equals(timetableAction)){
            sendMail.sendEmail("[" + client.toUpperCase() + " - " + server.toUpperCase() + "] Referentiel Mobi-iti - Nouvelle integration de donnees du reseau de " + referential,
                    recipient,
                    "Bonjour,"
                            + "<br>L'import, la validation niveau 1 et la génération des tracés du fichier : " + fileName + " se sont correctement effectués."
                            + SIGNING,
                    null);
        }
        if (StringUtils.equals(format, "GTFS")) {
            if(JobEvent.TimetableAction.EXPORT.equals(timetableAction)){
                sendMail.sendEmail("[" + client.toUpperCase() + " - " + server.toUpperCase() + "] Referentiel Mobi-iti - Nouvelle integration de donnees du reseau de " + referential,
                        recipient,
                        "Bonjour,"
                                + "<br>L'export GTFS : " + exportName + " suite à l'import du fichier : " + fileName + " s'est correctement effectué."
                                + SIGNING,
                        null);
            }
        }
        if (StringUtils.equals(format, "NEPTUNE")) {
            if(JobEvent.TimetableAction.EXPORT.equals(timetableAction)){
                sendMail.sendEmail("[" + client.toUpperCase() + " - " + server.toUpperCase() + "] Referentiel Mobi-iti - Nouvelle integration de donnees du reseau de " + referential,
                        recipient,
                        "Bonjour,"
                                + "<br>L'export Neptune : " + exportName + "suite à l'import du fichier : " + fileName + " s'est correctement effectué."
                                + SIGNING,
                        null);
            }
            if (StringUtils.equals(format, "NETEX")) {
                if(JobEvent.TimetableAction.EXPORT_NETEX.equals(timetableAction)){
                    sendMail.sendEmail("[" + client.toUpperCase() + " - " + server.toUpperCase() + "] Referentiel Mobi-iti - Nouvelle integration de donnees du reseau de " + referential,
                            recipient,
                            "Bonjour,"
                                    + "<br>L'export Netex : " + exportName + " suite à l'import du fichier : " + fileName + " s'est correctement effectué."
                                    + SIGNING,
                            null);
                }
            }
        }
    }

}
