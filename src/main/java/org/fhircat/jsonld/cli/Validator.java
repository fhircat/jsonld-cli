package org.fhircat.jsonld.cli;

import fr.inria.lille.shexjava.GlobalFactory;
import fr.inria.lille.shexjava.schema.Label;
import fr.inria.lille.shexjava.schema.ShexSchema;
import fr.inria.lille.shexjava.schema.abstrsynt.ShapeExpr;
import fr.inria.lille.shexjava.schema.parsing.ShExCParser;
import fr.inria.lille.shexjava.util.Pair;
import fr.inria.lille.shexjava.validation.RecursiveValidation;
import fr.inria.lille.shexjava.validation.Status;
import fr.inria.lille.shexjava.validation.ValidationAlgorithm;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.rdf.api.Graph;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDFTerm;
import org.apache.commons.rdf.jena.JenaRDF;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

/**
 * A ShEx validator for FHIR.
 */
public class Validator {

  private ShexSchema schema;
  private JenaRDF rdf;

  public Validator() {
    this.rdf = new JenaRDF();
    GlobalFactory.RDFFactory = this.rdf;
  }

  synchronized void lazyInit() {
    if (this.schema == null) {
      try {
        ShExCParser parser = new ShExCParser();

        Map<Label, ShapeExpr> rules = parser
            .getRules(this.getClass().getClassLoader().getResourceAsStream("fhir-r4/fhir-r4.shex"));

        this.schema = new ShexSchema(GlobalFactory.RDFFactory, rules, parser.getStart());
      } catch (Exception e) {
        throw new IllegalStateException("Problem loading FHIR ShEx schema.", e);
      }
    }
  }

  public boolean validate(String rdfString, String language, Consumer<List<Pair<RDFTerm, Label>>> errorHandler) {
    Model model = ModelFactory.createDefaultModel();
    model.read(new StringReader(rdfString), null, language);

    return this.validate(model, errorHandler);
  }

  public boolean validate(File rdfFile, String language, Consumer<List<Pair<RDFTerm, Label>>> errorHandler) {
    Model model = ModelFactory.createDefaultModel();
    try {
      model.read(new FileInputStream(rdfFile), null, language);
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }

    return this.validate(model, errorHandler);
  }

  public boolean validate(Model model, Consumer<List<Pair<RDFTerm, Label>>> errorHandler) {
    this.lazyInit();

    String focusUri = this.getFocusNode(model);

    Graph dataGraph = this.rdf.asGraph(model);

    String resourceType = this.getResourceType(model, focusUri);

    IRI focusNode = this.rdf.createIRI(focusUri);
    Label shapeLabel = new Label(this.rdf.createIRI("http://hl7.org/fhir/shape/" + StringUtils.substringAfterLast(resourceType, "/"))); //to change with what you want

    ValidationAlgorithm validation = new RecursiveValidation(schema, dataGraph);

    boolean result = validation.validate(focusNode, shapeLabel);

    if (! result) {
      errorHandler.accept(validation.getTyping().getStatusMap().entrySet()
          .stream().filter(x -> x.getValue() == Status.NONCONFORMANT)
          .map(x -> x.getKey())
          .collect(Collectors.toList()));
    }

    return result;
  }

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

    QuerySolution solution = results.next();

    return solution.getResource("o").getURI();
  }

}
