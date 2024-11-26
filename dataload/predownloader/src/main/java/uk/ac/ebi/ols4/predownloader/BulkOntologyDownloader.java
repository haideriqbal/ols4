
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
	private Set<String> mainOntologyIds;

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
		this.mainOntologyIds = new HashSet<>();
		for (Ontology ontology : ontologies) {
			mainOntologyIds.add(ontology.getId());
		}
    }

	public void downloadAll() {

		while (!ontologiesToDownload.isEmpty()) {

			List<Thread> threads = new ArrayList<>();
			List<Ontology> imports = Collections.synchronizedList(new ArrayList<>());

			int threadsStarted = 0;

			while (threadsStarted < NUM_THREADS) {

				Ontology ontology = null;

				synchronized (ontologiesToDownload) {
					// Remove and get the next unprocessed ontology
					while (!ontologiesToDownload.isEmpty()) {
						Ontology tempOntology = ontologiesToDownload.remove(0);
						if (!ontologyIdsAlreadyProcessed.contains(tempOntology.getId())) {
							ontology = tempOntology;
							ontologyIdsAlreadyProcessed.add(ontology.getId());
							break;
						}
					}
				}

				if (ontology == null) {
					// No more unprocessed ontologies to start in this iteration
					break;
				}

				// Start the thread
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

				Thread t = new Thread(downloaderThread, "Downloader thread " + threadsStarted);
				threads.add(t);

				t.start();

				threadsStarted++;
			}

			// Wait for all threads to finish
			for (Thread t : threads) {
				try {
					t.join();
					System.out.println(t.getName() + " finished");
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			// Add imports to the list to be processed
			synchronized (ontologiesToDownload) {
				for (Ontology importedOntology : imports) {
					if (!ontologyIdsAlreadyProcessed.contains(importedOntology.getId())) {
						ontologiesToDownload.add(importedOntology);
					}
				}
			}
		}

		// After all threads complete, save the updated checksums
		saveChecksums(updatedChecksums);

		// Output the summary of updates
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

	// Provide a method to check if an ontology ID is a main ontology
	public boolean isMainOntology(String ontologyId) {
		return mainOntologyIds.contains(ontologyId);
	}

}
