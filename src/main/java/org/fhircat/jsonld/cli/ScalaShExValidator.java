package org.fhircat.jsonld.cli;

import cats.effect.IO;
import com.google.common.collect.Lists;
import es.weso.rdf.jena.RDFAsJenaModel;
import es.weso.rdf.nodes.IRI;
import es.weso.shapeMaps.RDFNodeSelector;
import es.weso.shapeMaps.ResultShapeMap;
import es.weso.shapeMaps.Status;
import es.weso.shex.ResolvedSchema;
import es.weso.shex.Schema;
import es.weso.shex.validator.ExternalResolver;
import es.weso.shex.validator.NoAction;
import es.weso.shex.validator.Result;
import fr.inria.lille.shexjava.schema.Label;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Consumer;
import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.Model;
import scala.Console;
import scala.Option;

public class ScalaShExValidator extends BaseShExValidator {

  private static Schema schema;

  synchronized void lazyInit() {
    if (schema == null) {

      IO<Schema> io;
      try {
        io = Schema.fromString(IOUtils.toString(
            this.getClass().getClassLoader().getResourceAsStream("fhir-r4/fhir-r4.shex")),
            "ShexC", Option.empty(), Option.empty());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      schema = io.unsafeRunSync();
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

    IO<RDFAsJenaModel> rdfmodel = RDFAsJenaModel.fromModel(model, Option.empty(), Option.empty());

    ResultShapeMap result = rdfmodel.flatMap(rdf ->
      ResolvedSchema.resolve(schema, Option.empty()).flatMap(resolvedSchema ->
          validate(resolvedSchema, rdf, focusNode.getIRIString(), shapeLabel.stringValue())).flatMap(r -> r.toResultShapeMap()
      )
    ).unsafeRunSync();

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

  private IO<Result> validate(ResolvedSchema resolvedSchema, RDFAsJenaModel rdfmodel, String focusNode, String shapeLabel) {
    ExternalResolver noAction = NoAction.instance();
    es.weso.shex.validator.Validator validator = new es.weso.shex.validator.Validator(resolvedSchema, noAction, rdfmodel);
    IO<Result> result = validator.validateNodeShape(rdfmodel, IRI.apply(focusNode), shapeLabel);

    return result;
  }

}
