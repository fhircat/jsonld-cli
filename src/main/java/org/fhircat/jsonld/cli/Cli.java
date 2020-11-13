package org.fhircat.jsonld.cli;

import ch.qos.logback.classic.Level;
import java.util.stream.Collectors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.LoggerFactory;

/**
 * The main Command Line Interface entry class.
 */
public class Cli {

  public static void main(String... args) throws Throwable {
    Options options = new Options();

    Option outputFormat = new Option("f", "outputFormat", true,
        "output format (one of: " + ToRdf.formatFileExtensions.keySet().stream().collect(Collectors.joining(",")) +")");
    outputFormat.setRequired(false);
    options.addOption(outputFormat);

    Option input = new Option("i", "input", true, "input file path (single file or directory)");
    input.setRequired(true);
    options.addOption(input);

    Option output = new Option("o", "output", true, "output file (single file or directory) - standard output if omitted");
    output.setRequired(false);
    options.addOption(output);

    Option pre = new Option("p", "pre", true, "output the intermediate 'pre'-JSON structures");
    pre.setRequired(false);
    options.addOption(pre);

    Option versionBase = new Option("vb", "versionbase", true, "base URI for OWL version");
    versionBase.setRequired(false);
    options.addOption(versionBase);

    Option contextServer = new Option("cs", "contextserver", true, "context server base");
    contextServer.setRequired(false);
    options.addOption(contextServer);

    Option fhirServer = new Option("fs", "fhirserver", true, "FHIR server base");
    fhirServer.setRequired(false);
    options.addOption(fhirServer);

    Option shexValidate = new Option("v", "shexvalidate", false, "apply ShEx validation");
    shexValidate.setType(Boolean.class);
    shexValidate.setRequired(false);
    options.addOption(shexValidate);

    Option verbose = new Option("V", "verbose", false, "print extra logging messages");
    verbose.setType(Boolean.class);
    verbose.setRequired(false);
    options.addOption(verbose);

    Option help = new Option("h", "help", false, "print the usage help");
    help.setType(Boolean.class);
    help.setRequired(false);
    options.addOption(help);

    CommandLineParser parser = new DefaultParser();

    CommandLine command = null;

    String helpText = "FHIRCat JSON-LD Command Line Interface";
    int helpWidth = 500;

    HelpFormatter formatter = new HelpFormatter();
    formatter.setWidth(helpWidth);
    formatter.setOptionComparator(null);

    try {
      command = parser.parse(options, args);
    } catch (ParseException e) {
      System.out.println(e.getMessage());

      formatter.printHelp(helpText, options);

      System.exit(1);
    }

    if (command.hasOption('h')) {
      formatter.printHelp(helpText, options);

      System.exit(0);
    }

    if (command.hasOption("V")) {
      ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.fhircat.jsonld.cli");
      logger.setLevel(Level.DEBUG);
    }

    Operation operation = new ToRdf();

    try {
      operation.run(command);
    } catch (Exception e) {
      System.err.print(e.getMessage());
    }
  }

}
