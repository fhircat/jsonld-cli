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
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Map;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ToRdf extends BaseOperation {

  private static Logger log = LoggerFactory.getLogger(ToRdf.class);

  private HttpLoader loader = new HttpLoader(HttpClient.newHttpClient());

  private Map<URI, Document> cache = Maps.newConcurrentMap();

  @Override
  public void doRun(File inputFile, File outputFile, CommandLine commandLine) {
    JsonLdOptions jsonLdOptions = this.getJsonLdOptions();

    if (inputFile.isDirectory()) {
      if (! outputFile.isDirectory()) {
        throw new RuntimeException("If the input file is a directory, the output must be as well.");
      }

      Arrays.stream(inputFile.listFiles((dir, name) -> name.endsWith(".json")))
          .forEach(file -> {
            try {
              writeFile(file, jsonLdOptions, outputFile, null); //TODO: add output format
            } catch (Throwable e) {
              log.warn("Error writing file: " + file.getPath(), e);
            }

          });
    }
  }

  protected JsonLdOptions getJsonLdOptions() {
    JsonLdOptions jsonLdOptions = new JsonLdOptions();

    jsonLdOptions.setDocumentLoader(new DocumentLoader() {

      public synchronized Document loadDocument(URI url, DocumentLoaderOptions options) throws JsonLdError {
        if (cache.containsKey(url)) {
          return cache.get(url);
        }

        Document remoteDocument;
        if (url.getScheme().equals("file")) {

          Document document;
          try {
            document = JsonDocument.of(new FileReader(new File(url)));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }

          document.setDocumentUrl(url);

          return document;
        } else {

          log.info("Starting HTTP Load:" + url);
          remoteDocument = loader.loadDocument(url, options);
          log.info("Done HTTP Load");
        }

        cache.put(url, remoteDocument);

        return remoteDocument;
      }
    });

    return jsonLdOptions;
  }

  private void writeFile(File input, JsonLdOptions jsonLdOptions, File output, String outputFormat) throws Exception, JsonLdError {
    String fileName = input.getName();

    String ttlFilename = fileName.replace(".json", ".nq");

    URI is = input.toURI();

    long time = System.currentTimeMillis();
    log.info("Starting JSONLD for: " + is.getPath());
    ToRdfApi rdf = JsonLd.toRdf(is).options(jsonLdOptions);
    RdfDataset dataset = rdf.get();
    log.info("Done JSONLD for: " + is.getPath() + " " + Long.toString(System.currentTimeMillis() - time) + "ms");

    StringWriter sw = new StringWriter();
    log.info("Starting RDF Writing Create for: " + is.getPath() + " " + Long.toString(System.currentTimeMillis() - time) + "ms");
    RdfWriter writer = Rdf.createWriter(MediaType.N_QUADS, sw);
    log.info("Starting RDF Writing for: " + is.getPath() + " " + Long.toString(System.currentTimeMillis() - time) + "ms");

    writer.write(dataset);

    System.out.println(dataset.size());
    log.info("Done RDF Writing for: " + is.getPath() + " " + Long.toString(System.currentTimeMillis() - time) + "ms");

    File outputFile = new File(output, ttlFilename);

    if (outputFormat != null && !outputFormat.equals("N-TRIPLE")) {
      log.info("Starting RDF Transform for: " + is.getPath());
      Model model = ModelFactory.createDefaultModel();
      model.read(IOUtils.toInputStream(sw.toString(), Charset.defaultCharset()), null, outputFormat);
      log.info("Done RDF Transform for: " + is.getPath());

      model.write(new FileOutputStream(outputFile), outputFormat);
      model.close();
    } else {
      log.info("Writing: " + input.getPath() + " to " + outputFile.getPath());
      IOUtils.write(sw.toString(), new FileOutputStream(outputFile));
    }

    sw.close();
  }

}
