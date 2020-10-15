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
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import fr.inria.lille.shexjava.schema.Label;
import fr.inria.lille.shexjava.util.Pair;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.io.IOUtils;
import org.apache.commons.rdf.api.RDFTerm;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.fhircat.jsonld.cli.exceptions.ShExValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ToRdf extends BaseOperation {

  private static Logger log = LoggerFactory.getLogger(ToRdf.class);

  private HttpLoader loader = new HttpLoader(HttpClient.newHttpClient());

  private Map<URI, Document> cache = Maps.newConcurrentMap();

  private Preprocess preprocess = new Preprocess();

  private Validator validator = new Validator();

  protected static Map<String, String> formatFileExtensions = Maps.newHashMap();
  static {
    formatFileExtensions.put("RDF/XML", "xml");
    formatFileExtensions.put("N-TRIPLE", "nq");
    formatFileExtensions.put("TURTLE", "ttl");
    formatFileExtensions.put("TTL", "ttl");
    formatFileExtensions.put("N3", "n3");
  }

  @Override
  public void doRun(File inputFile, File outputFile, CommandLine commandLine) {
    JsonLdOptions jsonLdOptions = this.getJsonLdOptions();

    Consumer<File> fn = (file) -> {
      try {
        boolean validate = commandLine.hasOption("v");

        writeFile(file, jsonLdOptions, outputFile,
            commandLine.getOptionValue("f", "N-TRIPLE"),
            commandLine.getOptionValue("fs", "http://hl7.org/fhir/"),
            commandLine.getOptionValue("vb", "http://build.fhir.org/"),
            true,
            validate
        );
      } catch (Throwable e) {
        log.warn("Error writing file: " + file.getPath(), e);
      }
    };

    if (inputFile.isDirectory()) {
      if (outputFile == null) {
        throw new RuntimeException("If the input file is a directory, the output must not be to standard out.");
      }

      if (! outputFile.isDirectory()) {
        throw new RuntimeException("If the input file is a directory, the output must be as well.");
      }

      Arrays.stream(inputFile.listFiles((dir, name) -> name.endsWith(".json"))).forEach(fn);
    } else {
      fn.accept(inputFile);
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

          log.debug("Starting HTTP Load:" + url);
          remoteDocument = loader.loadDocument(url, options);
          log.debug("Done HTTP Load");
        }

        cache.put(url, remoteDocument);

        return remoteDocument;
      }
    });

    return jsonLdOptions;
  }

  private void writeFile(File input, JsonLdOptions jsonLdOptions, File output, String outputFormat, String server, String versionBase, boolean addContext, boolean validate) throws Exception, JsonLdError {
    String preprocessedJson = this.preprocess.preprocess(
        IOUtils.toString(new FileReader(input)),
        server,
        versionBase,
        addContext
    );

    String fileName = input.getName();

    long time = System.currentTimeMillis();
    log.debug("Starting JSONLD for: " + input.getPath());
    ToRdfApi rdf = JsonLd.toRdf(JsonDocument.of(new StringReader(preprocessedJson))).options(jsonLdOptions);

    RdfDataset dataset = rdf.get();
    log.debug("Done JSONLD for: " + input.getPath() + " " + Long.toString(System.currentTimeMillis() - time) + "ms");

    StringWriter sw = new StringWriter();
    log.debug("Starting RDF Writing Create for: " + input.getPath() + " " + Long.toString(System.currentTimeMillis() - time) + "ms");
    RdfWriter writer = Rdf.createWriter(MediaType.N_QUADS, sw);
    log.debug("Starting RDF Writing for: " + input.getPath() + " " + Long.toString(System.currentTimeMillis() - time) + "ms");

    writer.write(dataset);

    log.debug("Data set size: " + Integer.toString(dataset.size()));
    log.debug("Done RDF Writing for: " + input.getPath() + " " + Long.toString(System.currentTimeMillis() - time) + "ms");

    log.debug("Starting RDF Transform for: " + input.getPath());
    Model model = ModelFactory.createDefaultModel();
    model.read(IOUtils.toInputStream(sw.toString(), Charset.defaultCharset()), null, "N-TRIPLES");
    log.debug("Done RDF Transform for: " + input.getPath());

    if (validate) {
      List<Pair<RDFTerm, Label>> errors = Lists.newArrayList();
      Consumer<List<Pair<RDFTerm, Label>>> errorHandler = (x) -> {
        errors.addAll(x);
      };

      boolean isValid = this.validator.validate(model, errorHandler);
      if (! isValid) {
        throw new ShExValidationException("Input file " + input.getPath() + " does not pass ShEx validation.", errors);
      }
    }

    if (output != null) {
      if (output.isDirectory()) {
        String ttlFilename = fileName
            .replace(".json", "." + formatFileExtensions.get(outputFormat));

        File outputFile = new File(output, ttlFilename);

        model.write(new FileOutputStream(outputFile), outputFormat);
      } else {
        model.write(new FileOutputStream(output), outputFormat);
      }
    } else {
      model.write(System.out, outputFormat);
    }

    sw.close();
  }

}
