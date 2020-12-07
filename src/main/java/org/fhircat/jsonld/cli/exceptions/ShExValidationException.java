package org.fhircat.jsonld.cli.exceptions;

import java.util.List;
import org.fhircat.jsonld.cli.ValidationResult;

/**
 * Exception thrown if the input does not pass ShEx validation.
 */
public class ShExValidationException extends CliException {

  public ShExValidationException(String msg) {
    super(msg);
  }

  public ShExValidationException(String msg, List<ValidationResult> errors) {
    super(msg + "\nInvalid shapes: " + errors);
  }

}
