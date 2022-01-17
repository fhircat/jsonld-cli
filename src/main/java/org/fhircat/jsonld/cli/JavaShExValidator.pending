package org.fhircat.jsonld.cli;

import com.google.common.collect.Lists;
import fr.inria.lille.shexjava.GlobalFactory;
import fr.inria.lille.shexjava.schema.Label;
import fr.inria.lille.shexjava.schema.ShexSchema;
import fr.inria.lille.shexjava.schema.abstrsynt.ShapeExpr;
import fr.inria.lille.shexjava.schema.parsing.ShExCParser;
import fr.inria.lille.shexjava.validation.RecursiveValidation;
import fr.inria.lille.shexjava.validation.ValidationAlgorithm;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.commons.rdf.api.IRI;
import org.apache.jena.rdf.model.Model;

/**
 * A ShEx validator for FHIR.
 */
public class JavaShExValidator extends BaseShExValidator {

  private static ShexSchema schema;

  public JavaShExValidator() {
    GlobalFactory.RDFFactory = this.rdf;
  }

  synchronized void lazyInit() {
    if (schema == null) {
      try {
        ShExCParser parser = new ShExCParser();

        Map<Label, ShapeExpr> rules = parser
            .getRules(this.getClass().getClassLoader().getResourceAsStream("fhir-r4/fhir-r4.shex"));

        schema = new ShexSchema(GlobalFactory.RDFFactory, rules, parser.getStart());
      } catch (Exception e) {
        throw new IllegalStateException("Problem loading FHIR ShEx schema.", e);
      }
    }
  }

  @Override
  protected boolean doValidate(IRI focusNode, Label shapeLabel, Model dataGraph, Consumer<List<ValidationResult>> errorHandler) {
    ValidationAlgorithm validation = new RecursiveValidation(schema, this.rdf.asGraph(dataGraph));

    boolean result = validation.validate(focusNode, shapeLabel);

    if (! result) {
      errorHandler.accept(Lists.newArrayList(new ValidationResult(focusNode.getIRIString(), shapeLabel.stringValue(), "Shape validation failed")));
    }

    return result;
  }

}

