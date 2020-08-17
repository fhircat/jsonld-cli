package org.fhircat.jsonld.cli;


import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.api.JsonLdError;
import com.apicatalog.jsonld.api.JsonLdOptions;
import com.apicatalog.jsonld.api.impl.ToRdfApi;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.http.media.MediaType;
import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.jsonld.loader.DocumentLoaderOptions;
import com.apicatalog.jsonld.loader.HttpLoader;
import com.apicatalog.rdf.Rdf;
import com.apicatalog.rdf.RdfDataset;
import com.apicatalog.rdf.io.RdfWriter;
import com.google.common.collect.Maps;
import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Map;

public class ToRdf {

  private static Logger log = LoggerFactory.getLogger(ToRdf.class);

  public void run(String input, String output) throws JsonLdError {
    JsonLdOptions jsonLdOptions = new JsonLdOptions();
    jsonLdOptions.setBase(URI.create("http://hl7.org/fhir/"));

    jsonLdOptions.setDocumentLoader(new DocumentLoader() {

      private HttpClient client = HttpClient.newHttpClient();

      Map<URI, Document> cache = Maps.newConcurrentMap();

      public synchronized Document loadDocument(URI url, DocumentLoaderOptions options) throws JsonLdError {
        if (this.cache.containsKey(url)) {
          return this.cache.get(url);
        }

        Document remoteDocument;
        if(url.getScheme().equals("file")) {

          Document document;
          try {
            document = JsonDocument.of(new FileReader(new File(url)));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }

          return document;
        } else {

          log.debug("Starting HTTP Load:" + url);
          remoteDocument = new HttpLoader(this.client).loadDocument(url, options);
          log.debug("Done HTTP Load");
        }

        this.cache.put(url, remoteDocument);

        return remoteDocument;
      }
    });

    File inputFile = new File(input);
    File outputFile = new File(output);

    if (inputFile.isDirectory()) {
      if (! outputFile.isDirectory()) {
        throw new RuntimeException("If the input file is a directory, the output must be as well.");
      }

      Arrays.stream(inputFile.listFiles((dir, name) -> name.endsWith(".json")))
              .forEach(file -> {
                try {
                  writeFile(file, jsonLdOptions, outputFile);
                } catch (Throwable e) {
                  throw new RuntimeException(e);
                }
              });
    }
  }

  private void writeFile(File input, JsonLdOptions jsonLdOptions, File output) throws Exception, JsonLdError {
    String fileName = input.getName();

    String ttlFilename = fileName.replace(".json", ".ttl");

    URI is = input.toURI();

    log.debug("Starting JSONLD for: " + is.getPath());
    ToRdfApi rdf = JsonLd.toRdf(is).options(jsonLdOptions);
    RdfDataset dataset = rdf.get();
    log.debug("Done JSONLD for: " + is.getPath());

    StringWriter sw = new StringWriter();
    log.debug("Starting RDF Writing Create for: " + is.getPath());
    RdfWriter writer = Rdf.createWriter(MediaType.N_QUADS, sw);
    log.debug("Starting RDF Writing for: " + is.getPath());
    writer.write(dataset);
    log.debug("Done RDF Writing for: " + is.getPath());

    log.debug("Starting RDF Transform for: " + is.getPath());
    Model model = ModelFactory.createDefaultModel();
    model.read(IOUtils.toInputStream(sw.toString(), Charset.defaultCharset()), "http://hl7.org/fhir/", "N-TRIPLE");
    log.debug("Done RDF Transform for: " + is.getPath());

    File outputFile = new File(output, ttlFilename);
    log.debug("Writing: " + input.getPath() + " to " + outputFile.getPath());
    model.write(new FileOutputStream(outputFile), "TTL");
  }

}
