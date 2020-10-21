package org.fhircat.jsonld.cli.exceptions;

/**
 * Generic base exception.
 */
public class CliException extends RuntimeException {

  public CliException() {
  }

  public CliException(String message) {
    super(message);
  }

  public CliException(String message, Throwable cause) {
    super(message, cause);
  }

  public CliException(Throwable cause) {
    super(cause);
  }

  public CliException(String message, Throwable cause, boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

}
