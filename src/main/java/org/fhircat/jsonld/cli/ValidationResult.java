package org.fhircat.jsonld.cli;

public class ValidationResult {

  private String node;

  private String shape;

  private String message;

  public ValidationResult(String node, String shape, String message) {
    this.node = node;
    this.shape = shape;
    this.message = message;
  }

  public String getNode() {
    return node;
  }

  public String getShape() {
    return shape;
  }

  public String getMessage() {
    return message;
  }

  @Override
  public String toString() {
    return "ValidationResult{" +
        "node='" + node + '\'' +
        ", shape='" + shape + '\'' +
        ", message='" + message + '\'' +
        '}';
  }

}
