package org.fhircat.jsonld.cli;

import org.apache.commons.cli.*;

public class Cli {

  public static void main(String... args) throws Throwable {
    Options options = new Options();

    Option outputFormat = new Option("f", "outputFormat", true, "output format");
    outputFormat.setRequired(true);
    options.addOption(outputFormat);

    Option input = new Option("i", "input", true, "input file path");
    input.setRequired(true);
    options.addOption(input);

    Option output = new Option("o", "output", true, "output file");
    output.setRequired(true);
    options.addOption(output);

    CommandLineParser parser = new DefaultParser();
    HelpFormatter formatter = new HelpFormatter();

    CommandLine command = null;

    try {
      command = parser.parse(options, args);
    } catch (ParseException e) {
      System.out.println(e.getMessage());
      formatter.printHelp("FHIRcat JSON-LD Command Line Interface", options);

      System.exit(1);
    }

    String inputFilePath = command.getOptionValue("input");
    String outputFilePath = command.getOptionValue("output");

    ToRdf toRdf = new ToRdf();

    toRdf.run(inputFilePath, outputFilePath);
  }

}
