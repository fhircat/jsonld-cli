package org.fhircat.jsonld.cli;

import fr.inria.lille.shexjava.schema.Label;
import java.util.List;
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.jena.JenaRDF;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.fhircat.jsonld.cli.exceptions.ShExValidationException;

/**
 * A ShEx validator for FHIR.
 */
public abstract class BaseShExValidator implements Validator {

  protected JenaRDF rdf;

  public BaseShExValidator() {
    this.rdf = new JenaRDF();
  }

  synchronized void lazyInit() {
    //
  }

  @Override
  public final boolean validate(Model model, Consumer<List<ValidationResult>> errorHandler) {
    this.lazyInit();

    String focusUri = this.getFocusNode(model);

    String resourceType = this.getResourceType(model, focusUri);

    IRI focusNode = this.rdf.createIRI(focusUri);
    Label shapeLabel = new Label(this.rdf.createIRI("http://hl7.org/fhir/shape/" + StringUtils.substringAfterLast(resourceType, "/"))); //to change with what you want

    boolean result = this.doValidate(focusNode, shapeLabel, model, errorHandler);

    return result;
  }

  protected abstract boolean doValidate(IRI focusNode, Label shapeLabel, Model dataGraph, Consumer<List<ValidationResult>> errorHandler);

  String getFocusNode(Model model) {
    Query query = QueryFactory.create("select ?s { ?s <http://hl7.org/fhir/nodeRole> <http://hl7.org/fhir/treeRoot> . }");
    QueryExecution execution = QueryExecutionFactory.create(query, model);

    ResultSet results = execution.execSelect();

    QuerySolution solution = results.next();

    return solution.getResource("s").getURI();
  }

  String getResourceType(Model model, String focusUri) {
    Query query = QueryFactory.create("select ?o { <" + focusUri + "> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> ?o . }");
    QueryExecution execution = QueryExecutionFactory.create(query, model);

    ResultSet results = execution.execSelect();

    if (! results.hasNext()) {
      throw new ShExValidationException("No ShEx shape could be associated to: " + focusUri);
    }

    QuerySolution solution = results.next();

    return solution.getResource("o").getURI();
  }

}
