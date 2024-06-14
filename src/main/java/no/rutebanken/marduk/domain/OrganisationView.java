package no.rutebanken.marduk.domain;


public class OrganisationView {

    private Schemaview preProductionInfos;
    private Schemaview productionInfos;

    public Schemaview getPreProductionInfos() {
        return preProductionInfos;
    }

    public void setPreProductionInfos(Schemaview preProductionInfos) {
        this.preProductionInfos = preProductionInfos;
    }

    public Schemaview getProductionInfos() {
        return productionInfos;
    }

    public void setProductionInfos(Schemaview productionInfos) {
        this.productionInfos = productionInfos;
    }
}
