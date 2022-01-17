package org.fhircat.jsonld.cli;

import com.google.common.collect.Lists;

import java.util.List;
import java.util.function.Consumer;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.shex.*;

/**
 * A ShEx validator for FHIR.
 */
public class JenaShExValidator extends BaseShExValidator {

  private static ShexSchema schema;

  public JenaShExValidator() { }

  synchronized void lazyInit() {
    if (schema == null) {
      try {
        Shex.readSchema(this.getClass().getClassLoader().getResource("fhir-r4/fhir-r4.shex").toString());
      } catch (Exception e) {
        throw new IllegalStateException("Problem loading FHIR ShEx schema.", e);
      }
    }
  }

  @Override
  protected boolean doValidate(String focusNode, String shapeLabel, Model dataGraph, Consumer<List<ValidationResult>> errorHandler) {
    String shapeMapStr = "<" + focusNode + ">@<" + shapeLabel + ">" ;
    ShexMap shapeMap = Shex.shapeMapFromString(shapeMapStr,"");
    ShexReport report = ShexValidator.get().validate(dataGraph.getGraph(), schema, shapeMap);
    boolean result = report.conforms();

    if (! result) {
      errorHandler.accept(Lists.newArrayList(new ValidationResult(focusNode, shapeLabel, "Shape validation failed")));
    }

    return result;
  }

}

