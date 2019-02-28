package no.rutebanken.marduk.routes;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class Producers {

    /**
     * List of identifiers and names of producers of data for display in the mail.
     */

    public Map<String, String> producersListName(){

        Map<String, String> producers = new HashMap<>();
        //AOM
        producers.put("bme","Bordeaux Métropole");
        producers.put("bri","CA Bassin de Brive");
        producers.put("chl","CA Grand Châtellerault");
        producers.put("ang","CA Grand Angoulême");
        producers.put("cog","CA Grand Cognac");
        producers.put("lro","CA La Rochelle");
        producers.put("lim","CA Limoges Métropole");
        producers.put("nio","CA du Niortais");
        producers.put("pau","CA Pau Béarn Pyrénées");
        producers.put("roc","CA Rochefort Océan");
        producers.put("car","Ca Royan Atlantique");
        producers.put("tut","CA Tulle");
        producers.put("cou","CA Grand Dax");
        producers.put("per","CA Grand Périgueux");
        producers.put("vit","Grand Poitiers");
        producers.put("yeg","MACS");
        producers.put("ber","CA Bergeracoise");
        producers.put("age","CA Agen");
        producers.put("vdg","CA Val de Garonne");
        producers.put("bda","COBAS");
        producers.put("vil","CA du Grand Villeneuvois");
        producers.put("lib","CA du Libournais");
        producers.put("mdm","CA du Marsan");
        producers.put("pba","CA Pays Basque");
        producers.put("gue","CA Grand Guéret");
        producers.put("bbr","CA Bocage Bressuirais");
        producers.put("ole","CdC Ile d'Oléron");
        producers.put("sai","CA Saintes");

        //Sites territorialisés
        producers.put("cha","Charente");
        producers.put("cma","Charente-Maritime");
        producers.put("cor","Corrèze");
        producers.put("cre","Creuse");
        producers.put("dse","Deux-Sèvres");
        producers.put("dor","Dordogne");
        producers.put("gir","Gironde");
        producers.put("hvi","Haute-Vienne");
        producers.put("lan","Landes");
        producers.put("lga","Lot-et-Garonne");
        producers.put("pat","Pyrénées-Atlantiques");
        producers.put("vie","Vienne");
        producers.put("bac","Liaisons Maritime Gironde");
        producers.put("fai","Aix-Fouras");
        producers.put("snc","SNCF");


        return producers;
    }




}
