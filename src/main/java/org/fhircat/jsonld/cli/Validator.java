package org.fhircat.jsonld.cli;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.StringReader;
import java.util.List;
import java.util.function.Consumer;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

/**
 * A ShEx validator interface.
 */
public interface Validator {

  boolean validate(Model model, Consumer<List<ValidationResult>> errorHandler);

  default boolean validate(String rdfString, String language, Consumer<List<ValidationResult>> errorHandler) {
    Model model = ModelFactory.createDefaultModel();
    model.read(new StringReader(rdfString), null, language);

    return this.validate(model, errorHandler);
  }

  default boolean validate(File rdfFile, String language, Consumer<List<ValidationResult>> errorHandler) {
    Model model = ModelFactory.createDefaultModel();
    try {
      model.read(new FileInputStream(rdfFile), null, language);
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }

    return this.validate(model, errorHandler);
  }

}
