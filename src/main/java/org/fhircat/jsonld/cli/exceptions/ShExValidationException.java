package org.fhircat.jsonld.cli.exceptions;

import fr.inria.lille.shexjava.schema.Label;
import fr.inria.lille.shexjava.util.Pair;
import java.util.List;
import org.apache.commons.rdf.api.RDFTerm;

/**
 * Exception thrown if the input does not pass ShEx validation.
 */
public class ShExValidationException extends Exception {

  public ShExValidationException(String msg, List<Pair<RDFTerm, Label>> errors) {
    super(msg + "\nInvalid shapes: " + errors);
  }

}
