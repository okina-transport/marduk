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


    public Map<String, String> producersTransportTypeList(){
        Map<String, String> producersTransportType = new HashMap<>();
        //AOM
        producersTransportType.put("bme","urbain");
        producersTransportType.put("bri","urbain");
        producersTransportType.put("chl","urbain");
        producersTransportType.put("ang","urbain");
        producersTransportType.put("cog","urbain");
        producersTransportType.put("lro","urbain");
        producersTransportType.put("lim","urbain");
        producersTransportType.put("nio","urbain");
        producersTransportType.put("pau","urbain");
        producersTransportType.put("roc","urbain");
        producersTransportType.put("car","urbain");
        producersTransportType.put("tut","urbain");
        producersTransportType.put("cou","urbain");
        producersTransportType.put("per","urbain");
        producersTransportType.put("vit","urbain");
        producersTransportType.put("yeg","urbain");
        producersTransportType.put("ber","urbain");
        producersTransportType.put("age","urbain");
        producersTransportType.put("vdg","urbain");
        producersTransportType.put("bda","urbain");
        producersTransportType.put("vil","urbain");
        producersTransportType.put("lib","urbain");
        producersTransportType.put("mdm","urbain");
        producersTransportType.put("pba","urbain");
        producersTransportType.put("gue","urbain");
        producersTransportType.put("bbr","urbain");
        producersTransportType.put("ole","fluvial");
        producersTransportType.put("sai","urbain");

        //Sites territorialisés
        producersTransportType.put("cha","interurbain");
        producersTransportType.put("cma","interurbain");
        producersTransportType.put("cor","interurbain");
        producersTransportType.put("cre","interurbain");
        producersTransportType.put("dse","interurbain");
        producersTransportType.put("dor","interurbain");
        producersTransportType.put("gir","interurbain");
        producersTransportType.put("hvi","interurbain");
        producersTransportType.put("lan","interurbain");
        producersTransportType.put("lga","interurbain");
        producersTransportType.put("pat","interurbain");
        producersTransportType.put("vie","interurbain");
        producersTransportType.put("bac","fluvial");
        producersTransportType.put("fai","fluvial");
        producersTransportType.put("snc","ferroviaire");

        return producersTransportType;
    }



    public Map<String, String> producersGranulariteList(){
        Map<String, String> producersGranularite = new HashMap<>();
        //AOM
        producersGranularite.put("bme","ECPI");
        producersGranularite.put("bri","ECPI");
        producersGranularite.put("chl","ECPI");
        producersGranularite.put("ang","ECPI");
        producersGranularite.put("cog","ECPI");
        producersGranularite.put("lro","ECPI");
        producersGranularite.put("lim","ECPI");
        producersGranularite.put("nio","ECPI");
        producersGranularite.put("pau","ECPI");
        producersGranularite.put("roc","ECPI");
        producersGranularite.put("car","ECPI");
        producersGranularite.put("tut","ECPI");
        producersGranularite.put("cou","ECPI");
        producersGranularite.put("per","ECPI");
        producersGranularite.put("vit","ECPI");
        producersGranularite.put("yeg","ECPI");
        producersGranularite.put("ber","ECPI");
        producersGranularite.put("age","ECPI");
        producersGranularite.put("vdg","ECPI");
        producersGranularite.put("bda","ECPI");
        producersGranularite.put("vil","ECPI");
        producersGranularite.put("lib","ECPI");
        producersGranularite.put("mdm","ECPI");
        producersGranularite.put("pba","ECPI");
        producersGranularite.put("gue","ECPI");
        producersGranularite.put("bbr","ECPI");
        producersGranularite.put("ole","ECPI");
        producersGranularite.put("sai","ECPI");


        //Sites territorialisés
        producersGranularite.put("cha","Département");
        producersGranularite.put("cma","Département");
        producersGranularite.put("cor","Département");
        producersGranularite.put("cre","Département");
        producersGranularite.put("dse","Département");
        producersGranularite.put("dor","Département");
        producersGranularite.put("gir","Département");
        producersGranularite.put("hvi","Département");
        producersGranularite.put("lan","Département");
        producersGranularite.put("lga","Département");
        producersGranularite.put("pat","Département");
        producersGranularite.put("vie","Département");
        producersGranularite.put("bac","Département");
        producersGranularite.put("fai","Département");
        producersGranularite.put("snc","Région");

        return producersGranularite;
    }

}
