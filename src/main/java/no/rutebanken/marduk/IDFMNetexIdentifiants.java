package no.rutebanken.marduk;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class IDFMNetexIdentifiants {
    private static Map<String, String> idSitesList = new HashMap<>();
    private static Map<String, String> nameSitesList = new HashMap<>();

    static {
        idSitesList.put("CTVMI", "81");
        idSitesList.put("CEOBUS", "84");
        idSitesList.put("PERRIER", "42");
        idSitesList.put("SQYBUS", "41");
        idSitesList.put("STILE", "89");
        idSitesList.put("TIMBUS", "87");
        idSitesList.put("TVM", "86");
        idSitesList.put("TEST", "12");
        idSitesList.put("RD_BREST", "BIBUS");
        idSitesList.put("RD_ANGERS", "IRIGO");
        idSitesList.put("MOBICITEL40", "79");
        idSitesList.put("MOBICITE469", "79");
        idSitesList.put("RDLA", "RDLA");
        idSitesList.put("CTVH", "CTVH");

        nameSitesList.put("CTVMI", "CTVMI");
        nameSitesList.put("CEOBUS", "GIRAUX VAL D'OISE");
        nameSitesList.put("PERRIER", "PERRIER");
        nameSitesList.put("SQYBUS", "SQYBUS");
        nameSitesList.put("STILE", "STILE");
        nameSitesList.put("TIMBUS", "TIM BUS");
        nameSitesList.put("TVM", "TVM");
        nameSitesList.put("TEST", "TEST");
        nameSitesList.put("RD_BREST", "BIBUS");
        nameSitesList.put("RD_ANGERS", "IRIGO");
        nameSitesList.put("MOBICITEL40", "MOBICITE");
        nameSitesList.put("MOBICITE469", "MOBICITE");
        nameSitesList.put("RDLA", "RDLA");
        nameSitesList.put("CTVH", "CTVH");

    }

    public static String getIdSite(String name){
        String retour = idSitesList.get(name);
        if(StringUtils.isEmpty(retour)){
            return name;
        }
        return retour;
    }

    public static String getNameSite(String name){
        String retour = nameSitesList.get(name);
        if(StringUtils.isEmpty(retour)){
            return name;
        }
        return retour;
    }

}
