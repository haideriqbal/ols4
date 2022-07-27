
package uk.ac.ebi.spot.ols.repository.v2;

import com.google.gson.Gson;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import uk.ac.ebi.spot.ols.model.v2.V2Term;
import uk.ac.ebi.spot.ols.model.v2.V2Ontology;
import uk.ac.ebi.spot.ols.repository.Neo4jQueryHelper;
import uk.ac.ebi.spot.ols.repository.SolrQueryHelper;
import uk.ac.ebi.spot.ols.repository.Validation;

import javax.validation.Valid;
import java.io.IOException;
import java.util.Map;

@Component
public class V2TermRepository {

    @Autowired
    SolrQueryHelper solrQueryHelper;

    @Autowired
    Neo4jQueryHelper neo4jQueryHelper;


    public Page<V2Term> find(
            Pageable pageable, String lang, String search, String searchFields, Map<String,String> properties) throws IOException {

        Validation.validateLang(lang);

        if(search != null && searchFields == null) {
            searchFields = "http://www.w3.org/2000/01/rdf-schema#label^100 definition";
        }

        SolrQuery query = SolrQueryHelper.createSolrQuery(lang, search, searchFields);
        query.addFilterQuery("type:term");
        SolrQueryHelper.addDynamicFilterProperties(query, properties);

        return this.solrQueryHelper.searchSolrPaginated(query, pageable)
                .map(result -> new V2Term(result, lang));
    }

    public Page<V2Term> findByOntologyId(
            String ontologyId, Pageable pageable, String lang, String search, String searchFields, Map<String,String> properties) throws IOException {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        if(search != null && searchFields == null) {
            searchFields = "http://www.w3.org/2000/01/rdf-schema#label^100 definition";
        }

        SolrQuery query = SolrQueryHelper.createSolrQuery(lang, search, searchFields);
        query.addFilterQuery("type:term");
        query.addFilterQuery("ontologyId:" + ontologyId);
        SolrQueryHelper.addDynamicFilterProperties(query, properties);

        return this.solrQueryHelper.searchSolrPaginated(query, pageable)
                .map(result -> new V2Term(result, lang));
    }

    public V2Term getByOntologyIdAndUri(String ontologyId, String uri, String lang) throws ResourceNotFoundException {

        Validation.validateOntologyId(ontologyId);
        Validation.validateLang(lang);

        String id = ontologyId + "+" + uri;

        return new V2Term(this.neo4jQueryHelper.getOne("OntologyTerm", id), lang);

    }


}

