package uk.ac.ebi.ols4.predownloader;

public class Ontology {
    private String id;
    private String url;

    public Ontology(String id, String url) {
        this.id = id;
        this.url = url;
    }

    public String getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }
}

