# FHIRCat JSON-LD Command line interface

A simple CLI for processing JSON-LD files. This CLI takes in HL7 FHIR JSON and outputs JSON-DL with optional ShEx validation.

## Usage

```
usage: FHIRCat JSON-LD Command Line Interface
 -f,--outputFormat <arg>     output format (one of: RDF/XML,N3,TURTLE,N-TRIPLE,TTL)
 -i,--input <arg>            input file path (single file or directory)
 -o,--output <arg>           output file (single file or directory) - standard output if omitted
 -p,--pre <arg>              output the intermediate 'pre'-JSON structures
 -vb,--versionbase <arg>     base URI for OWL version
 -cs,--contextserver <arg>   context server base
 -fs,--fhirserver <arg>      FHIR server base
 -v,--shexvalidate           apply ShEx validation
 -v,--sheximpl               the ShEx validation implementation
 -V,--verbose                print extra logging messages
 -h,--help                   print the usage help
 ```

## Parameters
```-f,--outputFormat <arg>   output format (one of: RDF/XML,N3,TURTLE,N-TRIPLE,TTL)```

The RDF output format. Currently all formats supported by [Apache Jena](https://jena.apache.org/) are supported here.

```-i,--input <arg>          input file path (single file or directory)```

The input HL7 JSON FHIR files to process. This may be either a single file or a directory. If this is a directory, all files with a ```.json``` extension will be processed.

```-o,--output <arg>         output file (single file or directory) - standard output if omitted```

The output file/folder. If the input parameter is a folder, this must be as well. If this parameters is a folder, the output file will be named the same as the input file, but with the appropriate file extension for the RDF format. If this parameter is omitted, results will be sent to standard output. Omitting this parameters is only possible if the input parameters is a single file (not a directory).

```-p,--pre <arg>            output the intermediate 'pre'-JSON structures```

Outputs the intermediate 'pre' JSON FHIR structures. This parameter must be a directory (and it must exist). Files will be written using the same name as the input file but with a '-pre.json' suffix.

```-vb,--versionbase <arg>   base URI for OWL version```

The base OWL URI. Default: 'http://build.fhir.org/'

```-cs,--contextserver <arg> context server base```

The base of the JSON LD context files. Default: 'https://fhircat.org/fhir-r4/original/contexts/'

```-fs,--fhirserver <arg>    FHIR server base```

The base FHIR URI. Default: 'http://hl7.org/fhir/'

```-v,--shexvalidate         apply ShEx validation```

Validates all output against the FHIR ShEx schema. If the resulting output does not pass validation, processing will stop and a message will be displayed listing the nonconformant shapes.

```-v,--sheximpl             the ShEx validation implementation```

The FHIR ShEx schema implementation clas. Either 'java' or 'scala'. Default: 'scala'

For more information, see the [Java](https://github.com/iovka/shex-java) and [Scala](https://github.com/labra/shaclex) implementations.

```-V,--verbose              print extra logging messages```

Outputs additional logging regarding the individual steps of the algorithm. Generally not necessary unless debugging or examining performance.
 
```-h,--help                 print the usage help```

Prints the usage message and exits.

## Usage Notes
* For processing multiple files, specifiying a directory as the input parameter will be much more efficient than processing each file individually.
* If ShEx validation is indicated, processing will slow by a few seconds. This is a one time cost as the ShEx file loads. If you are processing multiple files in bulk via an input directory, this slowdown will only happen once.
* All HTTP calls to external resources are cached for the scope of one CLI interaction. This means if the input is a directory, HTTP calls will be cached over the duration of all files being processed.

## Installation
### Prerequisites
* Java 11 or higher

### Steps
1. Download the latest release [jsonld-cli-0.4.0-bin.tar.gz](https://github.com/fhircat/jsonld-cli/releases/download/v0.4.0-alpha/jsonld-cli-0.4.0-bin.tar.gz) or [jsonld-cli-0.4.0-bin.zip](https://github.com/fhircat/jsonld-cli/releases/download/v0.4.0-alpha/jsonld-cli-0.4.0-bin.zip)
2. Extract the archive, resulting in this format:
```
jsonld-cli-0.4.0
 |- bin/
 |- lib/
```
3. Navigate to the ```bin``` directory. There will be two scripts, ```fhircatjsonld```, and ```fhircatjsonld.bat```, for use with Linux and Windows, respectively.
4. You may need to adjust the permissions of the scripts depending on your system. For example, ```chmod 755 fhircatjsonld```.
5. Run the ```fhircatjsonld``` or ```fhircatjsonld.bat``` script with usages as described above.
