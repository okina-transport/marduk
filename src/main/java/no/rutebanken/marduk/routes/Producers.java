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
        producers.put("bordeaux_metropole","Bordeaux Métropole");
        producers.put("ca_bassin_de_brive","CA Bassin de Brive");
        producers.put("ca_grand_chatellerault","CA Grand Châtellerault");
        producers.put("ca_grand_angouleme","CA Grand Angoulême");
        producers.put("ca_grand_cognac","CA Grand Cognac");
        producers.put("ca_la_rochelle","CA La Rochelle");
        producers.put("ca_limoges_metropole","CA Limoges Métropole");
        producers.put("ca_du_niortais","CA du Niortais");
        producers.put("ca_pau_bearn_pyrenees","CA Pau Béarn Pyrénées");
        producers.put("ca_rochefort_ocean","CA Rochefort Océan");
        producers.put("ca_royan_atlantique","Ca Royan Atlantique");
        producers.put("ca_tulle","CA Tulle");
        producers.put("ca_grand_dax","CA Grand Dax");
        producers.put("ca_grand_perigueux","CA Grand Périgueux");
        producers.put("grand_poitiers","Grand Poitiers");
        producers.put("macs","MACS");
        producers.put("ca_bergeracoise","CA Bergeracoise");
        producers.put("ca_agen","CA Agen");
        producers.put("ca_val_de_garonne","CA Val de Garonne");
        producers.put("cobas","COBAS");
        producers.put("ca_du_grand_villeneuvois","CA du Grand Villeneuvois");
        producers.put("ca_du_libournais","CA du Libournais");
        producers.put("ca_du_marsan","CA du Marsan");
        producers.put("ca_pays_basque","CA Pays Basque");
        producers.put("ca_grand_gueret","CA Grand Guéret");
        producers.put("ca_bocage_bressuirais","CA Bocage Bressuirais");
        producers.put("ole","CdC Ile d'Oléron");
        producers.put("ca_saintes","CA Saintes");

        //Sites territorialisés
        producers.put("charente","Charente");
        producers.put("charente_maritime","Charente-Maritime");
        producers.put("correze","Corrèze");
        producers.put("creuse","Creuse");
        producers.put("deux_sevres","Deux-Sèvres");
        producers.put("dordogne","Dordogne");
        producers.put("gironde","Gironde");
        producers.put("haute_vienne","Haute-Vienne");
        producers.put("landes","Landes");
        producers.put("lot_et_garonne","Lot-et-Garonne");
        producers.put("pyrenees_atlantiques","Pyrénées-Atlantiques");
        producers.put("vienne","Vienne");
        producers.put("bac_gironde","Bac Gironde");
        producers.put("fai","Aix-Fouras");
        producers.put("sncf","SNCF");

        producers.put("alr", "Aéroport La Rochelle");

        return producers;
    }


    public Map<String, String> producersTransportTypeList(){
        Map<String, String> producersTransportType = new HashMap<>();
        //AOM
        producersTransportType.put("bordeaux_metropole","urbain");
        producersTransportType.put("ca_bassin_de_brive","urbain");
        producersTransportType.put("ca_grand_chatellerault","urbain");
        producersTransportType.put("ca_grand_angouleme","urbain");
        producersTransportType.put("ca_grand_cognac","urbain");
        producersTransportType.put("ca_la_rochelle","urbain");
        producersTransportType.put("ca_limoges_metropole","urbain");
        producersTransportType.put("ca_du_niortais","urbain");
        producersTransportType.put("ca_pau_bearn_pyrenees","urbain");
        producersTransportType.put("ca_rochefort_ocean","urbain");
        producersTransportType.put("ca_royan_atlantique","urbain");
        producersTransportType.put("ca_tulle","urbain");
        producersTransportType.put("ca_grand_dax","urbain");
        producersTransportType.put("ca_grand_perigueux","urbain");
        producersTransportType.put("grand_poitiers","urbain");
        producersTransportType.put("macs","urbain");
        producersTransportType.put("ca_bergeracoise","urbain");
        producersTransportType.put("ca_agen","urbain");
        producersTransportType.put("ca_val_de_garonne","urbain");
        producersTransportType.put("cobas","urbain");
        producersTransportType.put("ca_du_grand_villeneuvois","urbain");
        producersTransportType.put("ca_du_libournais","urbain");
        producersTransportType.put("ca_du_marsan","urbain");
        producersTransportType.put("ca_pays_basque","urbain");
        producersTransportType.put("ca_grand_gueret","urbain");
        producersTransportType.put("ca_bocage_bressuirais","urbain");
        producersTransportType.put("ole","fluvial");
        producersTransportType.put("ca_saintes","urbain");

        //Sites territorialisés
        producersTransportType.put("charente","interurbain");
        producersTransportType.put("charente_maritime","interurbain");
        producersTransportType.put("correze","interurbain");
        producersTransportType.put("creuse","interurbain");
        producersTransportType.put("deux_sevres","interurbain");
        producersTransportType.put("dordogne","interurbain");
        producersTransportType.put("gironde","interurbain");
        producersTransportType.put("haute_vienne","interurbain");
        producersTransportType.put("landes","interurbain");
        producersTransportType.put("lot_et_garonne","interurbain");
        producersTransportType.put("pyrenees_atlantiques","interurbain");
        producersTransportType.put("vienne","interurbain");
        producersTransportType.put("bac_gironde","fluvial");
        producersTransportType.put("fai","fluvial");
        producersTransportType.put("sncf","ferroviaire");

        return producersTransportType;
    }



    public Map<String, String> producersGranulariteList(){
        Map<String, String> producersGranularite = new HashMap<>();
        //AOM
        producersGranularite.put("bordeaux_metropole","ECPI");
        producersGranularite.put("ca_bassin_de_brive","ECPI");
        producersGranularite.put("ca_grand_chatellerault","ECPI");
        producersGranularite.put("ca_grand_angouleme","ECPI");
        producersGranularite.put("ca_grand_cognac","ECPI");
        producersGranularite.put("ca_la_rochelle","ECPI");
        producersGranularite.put("ca_limoges_metropole","ECPI");
        producersGranularite.put("ca_du_niortais","ECPI");
        producersGranularite.put("ca_pau_bearn_pyrenees","ECPI");
        producersGranularite.put("ca_rochefort_ocean","ECPI");
        producersGranularite.put("ca_royan_atlantique","ECPI");
        producersGranularite.put("ca_tulle","ECPI");
        producersGranularite.put("ca_grand_dax","ECPI");
        producersGranularite.put("ca_grand_perigueux","ECPI");
        producersGranularite.put("grand_poitiers","ECPI");
        producersGranularite.put("macs","ECPI");
        producersGranularite.put("ca_bergeracoise","ECPI");
        producersGranularite.put("ca_agen","ECPI");
        producersGranularite.put("ca_val_de_garonne","ECPI");
        producersGranularite.put("cobas","ECPI");
        producersGranularite.put("ca_du_grand_villeneuvois","ECPI");
        producersGranularite.put("ca_du_libournais","ECPI");
        producersGranularite.put("ca_du_marsan","ECPI");
        producersGranularite.put("ca_pays_basque","ECPI");
        producersGranularite.put("ca_grand_gueret","ECPI");
        producersGranularite.put("ca_bocage_bressuirais","ECPI");
        producersGranularite.put("ole","ECPI");
        producersGranularite.put("ca_saintes","ECPI");


        //Sites territorialisés
        producersGranularite.put("charente","Département");
        producersGranularite.put("charente_maritime","Département");
        producersGranularite.put("correze","Département");
        producersGranularite.put("creuse","Département");
        producersGranularite.put("deux_sevres","Département");
        producersGranularite.put("dordogne","Département");
        producersGranularite.put("gironde","Département");
        producersGranularite.put("haute_vienne","Département");
        producersGranularite.put("landes","Département");
        producersGranularite.put("lot_et_garonne","Département");
        producersGranularite.put("pyrenees_atlantiques","Département");
        producersGranularite.put("vienne","Département");
        producersGranularite.put("bac_gironde","Département");
        producersGranularite.put("fai","Département");
        producersGranularite.put("sncf","Région");

        return producersGranularite;
    }

}
