# FHIRCat JSON-LD Command line interface

A simple CLI for processing JSON-LD files. This CLI takes in HL7 FHIR JSON and outputs JSON-DL with optional ShEx validation.

## Usage

```
usage: FHIRcat JSON-LD Command Line Interface
 -f,--outputFormat <arg>   output format (one of: RDF/XML,N3,TURTLE,N-TRIPLE,TTL)
 -i,--input <arg>          input file path (single file or directory)
 -o,--output <arg>         output file (single file or directory) - standard output if omitted
 -v,--shexvalidate         apply ShEx validation
 -p,--pre                  output the intermediate 'pre'-JSON structures
 -V,--verbose              print extra logging messages
 -h,--help                 print the usage help
 ```

## Parameters
```-f,--outputFormat <arg>   output format (one of: RDF/XML,N3,TURTLE,N-TRIPLE,TTL)```
The RDF output format. Currently all formats supported by [Apache Jena](https://jena.apache.org/) are supported here.

```-i,--input <arg>          input file path (single file or directory)```
The input HL7 JSON FHIR files to process. This may be either a single file or a directory. If this is a directory, all files with a ```.json``` extension will be processed.

```-o,--output <arg>         output file (single file or directory) - standard output if omitted```
The output file/folder. If the input parameter is a folder, this must be as well. If this parameters is a folder, the output file will be named the same as the input file, but with the appropriate file extension for the RDF format. If this parameter is omitted, results will be send to standard output. Omitting this parameters is only possible if the input parameters is a single file (not a directory).

```-v,--shexvalidate         apply ShEx validation```
Validates all output against the FHIR ShEx schema. If the resulting output does not pass validation, processing will stop and a message will be displayed listing the nonconformant shapes.

```-p,--pre                  output the intermediate 'pre'-JSON structures```
Outputs the intermediate 'pre' JSON FHIR structures. This parameter must be a directory (and it must exist). Files will be written using the same name as the input file but with a '-pre.json' suffix.
 
 
```-V,--verbose              print extra logging messages```
Outputs additional logging regarding the individual steps of the algorithm. Generally not necessary unless debugging or examining performance.
 
```-h,--help                 print the usage help```
Prints the usage message and exits.

## Usage Notes
* For processing multiple files, specifiying a directory as the input parameter will be much more efficient than processing each file individually.
* If ShEx validation is indicated, processing will slow by a few seconds. This is a one time cost as the ShEx file loads. If you are processing multiple files in bulk via an input directory, this will only happen once.
* All HTTP calls to external resources are cached for the scope of one CLI interaction. This means if the input is a directory, HTTP calls will be cached over the duration of all files being processed.

## Installation
//TODO
