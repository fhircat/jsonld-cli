package org.fhircat.jsonld.cli.exceptions;

/**
 * Used to indicate that a user-supplied CLI parameter was invalid.
 */
public class InvalidParameterException extends CliException {

  public InvalidParameterException(String parameter, String value, String message) {
    super("Input parameter `" + parameter + "` is invalid for value `" + value + "`. " + message);
  }

}
