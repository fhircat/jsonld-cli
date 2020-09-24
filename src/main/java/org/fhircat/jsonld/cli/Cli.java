package org.fhircat.jsonld.cli;

import org.apache.commons.cli.*;

/**
 * The main Command Line Interface entry class.
 */
public class Cli {

  public static void main(String... args) throws Throwable {
    Options options = new Options();

    //Option outputFormat = new Option("f", "outputFormat", true, "output format");
    //outputFormat.setRequired(false);
    //options.addOption(outputFormat);

    Option input = new Option("i", "input", true, "input file path");
    input.setRequired(true);
    options.addOption(input);

    Option output = new Option("o", "output", true, "output file");
    output.setRequired(true);
    options.addOption(output);

    Option processingType = new Option("p", "processingType", true, "processingType");
    processingType.setRequired(true);
    options.addOption(processingType);

    Option serverBase = new Option("fs", "serverBase", true, "server base");
    serverBase.setRequired(false);
    options.addOption(serverBase);

    Option versionBase = new Option("vb", "versionBase", true, "version base");
    versionBase.setRequired(false);
    options.addOption(versionBase);

    Option addContext = new Option("c", "addcontext", false, "add context");
    addContext.setType(Boolean.class);
    addContext.setRequired(false);
    options.addOption(addContext);

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

    Operation operation = null;

    switch (command.getOptionValue("p")) {
      case "toRDF": operation = new ToRdf(); break;
      case "pre": operation = new Preprocess(); break;
      default: throw new RuntimeException("Option: " + command.getOptionValue("p") + " not recognized.");
    }

    operation.run(command);
  }

}
