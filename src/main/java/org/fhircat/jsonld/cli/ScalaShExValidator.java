package org.fhircat.jsonld.cli;

import com.google.common.collect.Lists;
import es.weso.shapemaps.RDFNodeSelector;
import es.weso.shapemaps.ResultShapeMap;
import es.weso.shapemaps.Status;
import es.weso.shex.Schema;
import es.weso.shex.validator.ShExsValidatorBuilder;
import fr.inria.lille.shexjava.schema.Label;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Consumer;
import org.apache.jena.rdf.model.Model;
import scala.Console;
import es.weso.shex.validator.ShExsValidator;

public class ScalaShExValidator extends BaseShExValidator {

  private static ShExsValidator validator;

  synchronized void lazyInit() {
    if (validator == null) {
      InputStream is = this.getClass().getClassLoader().getResourceAsStream("fhir-r4/fhir-r4.shex");
      validator = ShExsValidatorBuilder.fromInputStreamSync(is,"ShexC");
    }
  }

  @Override
  protected boolean doValidate(
      org.apache.commons.rdf.api.IRI focusNode,
      Label shapeLabel,
      Model model,
      Consumer<List<ValidationResult>> errorHandler) {
    return Console.withOut(OutputStream.nullOutputStream(), () -> {
      try {
        return this.runValidataion(focusNode, shapeLabel, model, errorHandler);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  private boolean runValidataion(
          org.apache.commons.rdf.api.IRI focusNode,
          Label shapeLabel,
          Model model,
          Consumer<List<ValidationResult>> errorHandler) {
    this.lazyInit();

    ResultShapeMap result = validator.validateNodeShapeSync(model,focusNode.getIRIString(),shapeLabel.stringValue(),false);

    List<ValidationResult> errors = Lists.newArrayList();
    boolean valid = result.associations().toList().toStream().forall(assoc -> {
      Status status = assoc.info().status();

      if (status.toString().equals("NonConformant")) {
        String node = ((RDFNodeSelector) assoc.node()).node().getLexicalForm();
        errors.add(new ValidationResult(node, assoc.shape().toString(), assoc.info().reason().get()));

        return false;
      } else {
        return true;
      }
    });

    errorHandler.accept(errors);

    return valid;
  }
}
