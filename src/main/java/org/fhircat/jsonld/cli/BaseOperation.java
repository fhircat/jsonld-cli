package org.fhircat.jsonld.cli;

import java.io.File;
import org.apache.commons.cli.CommandLine;

/**
 * An abstract base class for the {@link Operation} interface.
 */
public abstract class BaseOperation implements Operation {

  @Override
  public void run(CommandLine command) throws Exception {
    String inputFilePath = command.getOptionValue("input");
    String outputFilePath = command.getOptionValue("output");

    File inputFile = new File(inputFilePath);
    File outputFile = outputFilePath != null ? new File(outputFilePath) : null;

    this.doRun(inputFile, outputFile, command);
  }

  protected abstract void doRun(File inputFile, File outputFile, CommandLine command);

}
