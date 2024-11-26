
package uk.ac.ebi.ols4.predownloader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RDFParserBuilder;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.sparql.core.Quad;

public class OntologyDownloaderThread implements Runnable {

    BulkOntologyDownloader downloader;
    Ontology ontology;
    Consumer<Collection<Ontology>> consumeImports;
    Map<String, String> previousChecksums;
    Map<String, String> updatedChecksums;
    List<String> updatedOntologyIds;
    List<String> unchangedOntologyIds;

    public OntologyDownloaderThread(BulkOntologyDownloader downloader,
                                    Ontology ontology,
                                    Consumer<Collection<Ontology>> consumeImports,
                                    Map<String, String> previousChecksums,
                                    Map<String, String> updatedChecksums,
                                    List<String> updatedOntologyIds,
                                    List<String> unchangedOntologyIds) {

        super();

        this.downloader = downloader;
        this.ontology = ontology;
        this.consumeImports = consumeImports;
        this.previousChecksums = previousChecksums;
        this.updatedChecksums = updatedChecksums;
        this.updatedOntologyIds = updatedOntologyIds;
        this.unchangedOntologyIds = unchangedOntologyIds;
    }


    @Override
    public void run() {
        String ontologyId = ontology.getId();
        String ontologyUrl = ontology.getUrl();
        String path = downloader.downloadPath + "/" + urlToFilename(ontologyUrl);

        System.out.println(Thread.currentThread().getName() + " Starting download for " + ontologyUrl + " to " + path);

        long begin = System.nanoTime();

        try {

            String mimetype = downloadURL(ontologyUrl, path);

            String newChecksum = computeMD5Checksum(new File(path));

            String previousChecksum = previousChecksums.get(ontologyUrl);

            // Update the checksum map (synchronized for thread safety)
            updatedChecksums.put(ontologyUrl, newChecksum);

            if (previousChecksum == null || !newChecksum.equals(previousChecksum)) {
                // Ontology is new or has changed; process it
                System.out.println("Processing updated ontology: " + ontologyUrl);

                // Parse ontology for imports
                Set<Ontology> importOntologies = parseOntologyForImports(path, mimetype);

                // Record that this ontology was updated if it's a main ontology
                if (downloader.isMainOntology(ontologyId)) {
                    updatedOntologyIds.add(ontologyId);
                }

                // Pass import URLs to the parent downloader
                consumeImports.accept(importOntologies);

            } else {
                // Ontology hasn't changed; skip processing
                System.out.println("Skipping unchanged ontology: " + ontologyUrl);
                // Record that this ontology was unchanged if it's a main ontology
                if (downloader.isMainOntology(ontologyId)) {
                    unchangedOntologyIds.add(ontologyId);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        long end = System.nanoTime();

        System.out.println(Thread.currentThread().getName() + " Downloading and parsing for imports " + ontologyUrl + " took " + ((end-begin) / 1000 / 1000 / 1000) + "s");
    }

    private String urlToFilename(String url) {
        return url.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
    }
    
    private RDFParserBuilder createParser(Lang lang) {

        return RDFParser.create()
                .forceLang(lang)
                .strict(false)
                .checking(false);
    }

    private static String downloadURL(String url, String filename) throws FileNotFoundException, IOException {

        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(5000)
                .setConnectionRequestTimeout(5000)
                .setSocketTimeout(5000).build();

        CloseableHttpClient client = HttpClientBuilder.create().setDefaultRequestConfig(config).build();

        HttpGet request = new HttpGet(url);
        HttpResponse response = client.execute(request);
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            entity.writeTo(new FileOutputStream(filename));
            Header contentTypeHeader = entity.getContentType();
            String contentType = contentTypeHeader != null ? contentTypeHeader.getValue() : "";
            Files.write(Paths.get(filename + ".mimetype"), contentType.getBytes(StandardCharsets.UTF_8), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return contentType;
        } else {
            return "";
        }
    }

    private String computeMD5Checksum(File file) throws NoSuchAlgorithmException {
        try (InputStream fis = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            byte[] digest = md.digest();
            // Convert the byte array to a hexadecimal string
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            System.err.println("Error computing checksum for file " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }

    private Set<Ontology> parseOntologyForImports(String path, String mimetype) throws FileNotFoundException {
        Set<Ontology> importOntologies = new LinkedHashSet<>();

        Lang lang = RDFLanguages.contentTypeToLang(mimetype);
        if (lang == null) {
            lang = Lang.RDFXML;
        }

        createParser(lang).source(new FileInputStream(path)).parse(new StreamRDF() {
            public void start() {}
            public void quad(Quad quad) {}
            public void base(String base) {}
            public void prefix(String prefix, String iri) {}
            public void finish() {}
            public void triple(Triple triple) {
                if (triple.getPredicate().getURI().equals("http://www.w3.org/2002/07/owl#imports")) {
                    String importUrl = triple.getObject().getURI();
                    importOntologies.add(new Ontology(importUrl, importUrl));
                }
            }
        });

        return importOntologies;
    }


}
