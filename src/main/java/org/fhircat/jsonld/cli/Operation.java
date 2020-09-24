package org.fhircat.jsonld.cli;

import org.apache.commons.cli.CommandLine;

/**
 * An operation for the Command Line Interface.
 */
public interface Operation {

  void run(CommandLine command) throws Exception;

}
