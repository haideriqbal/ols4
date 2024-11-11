
package uk.ac.ebi.ols4.predownloader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BulkOntologyDownloader {

    static final int NUM_THREADS = 16;

	List<Ontology> ontologiesToDownload;
	private Set<String> ontologyIdsAlreadyProcessed;
    String downloadPath;
    boolean loadLocalFiles;
	List<String> updatedOntologyIds;
	List<String> unchangedOntologyIds;
	private Map<String, String> previousChecksums;
	private Map<String, String> updatedChecksums;

    Set<OntologyDownloaderThread> threads = new HashSet<>();

    public BulkOntologyDownloader(List<Ontology> ontologies,
								  String downloadPath,
								  boolean loadLocalFiles,
								  Map<String, String> previousChecksums) {
        this.ontologiesToDownload = new ArrayList<>(ontologies);
		this.ontologyIdsAlreadyProcessed = Collections.synchronizedSet(new HashSet<>());
        this.downloadPath = downloadPath;
        this.loadLocalFiles = loadLocalFiles;
		this.previousChecksums = previousChecksums;
		this.updatedChecksums = new ConcurrentHashMap<>();
		this.updatedOntologyIds = Collections.synchronizedList(new ArrayList<>());
		this.unchangedOntologyIds = Collections.synchronizedList(new ArrayList<>());
    }

    public void downloadAll() {

		while (!ontologiesToDownload.isEmpty()) {

		List<Thread> threads = new ArrayList<>();
		Set<Ontology> imports = new LinkedHashSet<>();

		for(int i = 0; i < NUM_THREADS; ++ i) {

			if (ontologiesToDownload.isEmpty()) {
				break;
			}

			Ontology ontology = ontologiesToDownload.remove(0);

			// Check if we've already processed this ontology ID
			if (ontologyIdsAlreadyProcessed.contains(ontology.getId())) {
				continue;
			}

			ontologyIdsAlreadyProcessed.add(ontology.getId());

			OntologyDownloaderThread downloaderThread = new OntologyDownloaderThread(
					this,
					ontology,
					importedOntologies -> {
						synchronized (imports) {
							imports.addAll(importedOntologies);
						}
					},
					previousChecksums,
					updatedChecksums,
					updatedOntologyIds,
					unchangedOntologyIds
			);


			Thread thread = new Thread(downloaderThread, "Downloader thread " + i);
			threads.add(thread);

			thread.start();

			for (Thread t : threads) {
				try {
					t.join();
					System.out.println(t.getName() + " finished");
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

			synchronized (ontologiesToDownload) {
				for (Ontology importedOntology : imports) {
					if (!ontologyIdsAlreadyProcessed.contains(importedOntology.getId())) {
						ontologiesToDownload.add(importedOntology);
					}
				}
			}
		}

		saveChecksums(updatedChecksums);
		printUpdateSummary();
    }

	private void saveChecksums(Map<String, String> checksums) {
		try (Writer writer = new FileWriter("checksums.json")) {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			gson.toJson(checksums, writer);
		} catch (IOException e) {
			System.err.println("Error writing checksums.json: " + e.getMessage());
		}
	}

	private void printUpdateSummary() {
		System.out.println("\nUpdate Summary:");
		System.out.println("Total ontologies processed: " + (updatedOntologyIds.size() + unchangedOntologyIds.size()));
		System.out.println("Ontologies updated: " + updatedOntologyIds.size());
		System.out.println("Ontologies unchanged: " + unchangedOntologyIds.size());

		if (!updatedOntologyIds.isEmpty()) {
			System.out.println("\nUpdated Ontologies:");
			for (String id : updatedOntologyIds) {
				System.out.println(" - " + id);
			}
		}

		if (!unchangedOntologyIds.isEmpty()) {
			System.out.println("\nUnchanged Ontologies:");
			for (String id : unchangedOntologyIds) {
				System.out.println(" - " + id);
			}
		}
	}

}
