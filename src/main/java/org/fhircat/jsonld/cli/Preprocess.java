package org.fhircat.jsonld.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.Sets;
import com.google.common.net.PercentEscaper;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pre-process raw JSON into a specialized format for JSONLD processing.
 */
public class Preprocess extends BaseOperation {

  private static Logger log = LoggerFactory.getLogger(ToRdf.class);

  private ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  private static final String VALUE_TAG = "value";
  private static final String CONTEXT_SERVER = "https://fhircat.org/fhir-r5/original/contexts/";

  private PercentEscaper escaper = new PercentEscaper("-._", false);

  private static final Map<String, String> CODE_SYSTEM_MAP = new HashMap<>();
  static {
    CODE_SYSTEM_MAP.put("http://snomed.info/sct", "sct");
    CODE_SYSTEM_MAP.put("http://loinc.org", "loinc");
  }

  @Override
  protected void doRun(File inputFile, File outputFile, CommandLine command) {
    if (inputFile.isDirectory()) {
      if (! outputFile.isDirectory()) {
        throw new RuntimeException("If the input file is a directory, the output must be as well.");
      }

      Arrays.stream(inputFile.listFiles((dir, name) -> name.endsWith(".json")))
          .forEach(file -> {
            try {
              try {
                String result = this.preprocess(IOUtils.toString(new FileReader(file)),
                    command.getOptionValue("fs"),
                    command.getOptionValue("vb"),
                    command.hasOption("c"));

                FileUtils.write(new File(outputFile, file.getName()), result);
              } catch (NotAFhirResourceException e) {
                log.warn(file.getPath() + " is not a FHIR Resource.");
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            } catch (Throwable e) {
              log.warn("Error writing file: " + file.getPath(), e);
            }

          });
    }
  }

  public String preprocess(String input, String server, String versionBase, boolean addContext) throws Exception {
    Set<String> resourceTypes = new HashSet<>();

    Map jsonObj = this.objectMapper.readValue(input, Map.class);

    if (! (jsonObj.containsKey("resourceType") || jsonObj.containsKey("id"))) {
      throw new NotAFhirResourceException();
    }

    jsonObj = this.dictProcessor(jsonObj, null, null, server, resourceTypes);

    // Add nodeRole
    jsonObj.put("nodeRole", "fhir:treeRoot");

    jsonObj = this.addOntologyHeader(jsonObj, versionBase);

    if (addContext) {
      jsonObj = this.addContext(jsonObj, resourceTypes, server);
    }

    return this.objectMapper.writeValueAsString(jsonObj);
  }

  private Map<String, Object> addOntologyHeader(Map<String, Object> json, String versionBase) {
    // Add the "ontology header"
    Map<String, Object> hdr = new HashMap<>();
    if (json.containsKey("@id")) {
      hdr.put("@id", json.get("@id") + ".ttl");
      hdr.put("owl:versionIRI", versionBase != null ? (versionBase + hdr.get("@id")) : hdr.get("@id"));
      hdr.put("owl:imports", "fhir:fhir.ttl");
      hdr.put("@type", "owl:Ontology");
      json.put("@included", hdr);
    }
    else {
      System.out.println("JSON does not have an identifier");
    }

    return json;
  }


  private Map<String, Object> addContext(Map<String, Object> json, Set<String> resourceTypeSet, String server) {
    //Fill out the rest of the context
    List contextList = resourceTypeSet.stream()
        .map(resource -> CONTEXT_SERVER + resource.toLowerCase() + ".context.jsonld")
        .sorted()
        .collect(Collectors.toList());

    contextList.add(CONTEXT_SERVER + "root.context.jsonld");
    json.put("@context", contextList);

    Map<String, Object> localContext = new HashMap<>();

    Map<String, Object> nodeRole = new HashMap<>();
    nodeRole.put("@type", "@id");
    nodeRole.put("@id", "fhir:nodeRole");

    localContext.put("nodeRole", nodeRole);

    if (server != null) {
      localContext.put("@base", server);

      Map<String, Object> imports = new HashMap<>();
      imports.put("@type", "@id");
      localContext.put("owl:imports", imports);

      Map<String, Object> versionIri = new HashMap<>();
      versionIri.put("@type", "@id");
      localContext.put("owl:versionIRI", versionIri);
      contextList.add(localContext);
    }

    return json;
  }

  private Map toValue(Object value) {
    Map valueMap = new HashMap();
    valueMap.put(VALUE_TAG, value);

    return valueMap;
  }

  private String fromValue(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String) {
      return (String) value;
    } else if (value instanceof Map) {
      return (String) ((Map) value).get("value");
    } else {
      throw new UnsupportedOperationException();
    }
  }

  private Map<String, Object> dictProcessor(Map<String, Object> json, String resourceType, String fullUrl, String server, Set<String> resourceTypes) {
    String innerType =  null;

    if (json.containsKey("resourceType") && json.get("resourceType") instanceof String) {
      innerType = (String) json.getOrDefault("resourceType", null);
      if (innerType != null) {
        resourceType = innerType;

        if (fullUrl != null) {
          json.put("@id", fullUrl);
        }
      }
    }

    if (fullUrl == null) {
      String rawIdValue = (String) json.getOrDefault("id", null);

      if (rawIdValue != null) {
        String idValue =
            (innerType == null && !rawIdValue.startsWith("#") ? "#" : (resourceType + '/'))
                + rawIdValue;

        json.put("@id", idValue);
      }
    }

    String innerFullUrl = this.getFullUrl(json);

    for (Map.Entry<String, Object> entry : new HashSet<>(json.entrySet())) {
      String key = entry.getKey();
      Object value = entry.getValue();

      if (key.startsWith("@")) { //Ignore JSON-LD components
        continue;
      } else if (value instanceof Map) { //Inner object -- process recursively
        entry.setValue(this.dictProcessor((Map<String, Object>) value, resourceType, innerFullUrl, server, resourceTypes));
      } else if (value instanceof List) { //Add ordering to the list
        entry.setValue(listProcessor((List) value, resourceType, server, resourceTypes));
      }
      else if (key.equals("reference")) { //Link to another document
        if (! json.containsKey("link")) {
          json.put("fhir:link", this.genReference(json, server));
        }
        entry.setValue(this.toValue(value));
      } else if (key.equals("resourceType") && !value.toString().startsWith("fhir:")) {
        resourceTypes.add((String) value);
        entry.setValue("fhir:" + value);
      } else if (! Sets.newHashSet("nodeRole", "index", "div").contains(key)){
        entry.setValue(this.toValue(value)); //Convert most other nodes to value entries
      }

      if (key.equals("coding")) {
        entry.setValue(((List) value).stream().map(n -> this.addTypeArc((Map<String, Object>) n)).collect(Collectors.toList()));
      }
    }

    // Merge any extensions (keys that start with '_') into the base
    for (Map.Entry<String, Object> entry : json.entrySet().stream().filter(e -> e.getKey().startsWith("_")).collect(Collectors.toList())) {
      String key = entry.getKey();
      Object value  = entry.getValue();

      String baseK = key.substring(1);

      if (!json.containsKey(baseK) || !(json.get(baseK) instanceof Map)) {
        json.put(baseK, new HashMap());
      } else {
        for (Map.Entry innerEntry : ((Map<String, Object>)value).entrySet()) {
          if (((Map) json.get(baseK)).containsKey(innerEntry.getKey())) {
            throw new RuntimeException("Extension element {kp} is already in the base for {k}");
          } else{
            ((Map) json.get(baseK)).put(innerEntry.getKey(), innerEntry.getValue());
          }
        }

      }

      json.remove(key);
    }

    return json;
  }


  public Map genReference(Map json, String server) {
    String reference = (String) json.getOrDefault("reference", null);

    String typ;
    String link;

    if (!reference.contains("://") && !reference.startsWith("/")) {
      if (json.containsKey("type")) {
        typ = fromValue(json.get("type"));
      } else{
        typ = reference.split("/", 2)[0];
      }
      link = (server != null ? "" : "../") + reference;
    } else{
      link = reference;
      typ = fromValue(json.getOrDefault("type", null));
    }

    Map rval = new HashMap();
    rval.put("@id", link);

    if (typ != null) {
      rval.put("@type", "fhir:" + typ);
    }

    return rval;
  }

  private List listProcessor(List list, String resourceType, String server, Set<String> resourceTypes) {
    List returnList = new ArrayList();

    for (int i=0;i<list.size();i++) {
      Object entry = list.get(i);

      Map value;
      if (entry instanceof Map) {
        value = this.dictProcessor((Map<String, Object>) entry, resourceType, this.getFullUrl((Map) entry), server, resourceTypes);
      } else {
        value = this.toValue(entry);
      }

      value.put("index", i);

      returnList.add(value);
    }

    return returnList;
  }

  private String getFullUrl(Map json) {
    return (String) json.getOrDefault("fullUrl", null);
  }

  private Map<String, Object> addTypeArc(Map<String, Object> n) {
    if (n.containsKey("system") && (n.containsKey("code"))) {
      String system = this.fromValue(n.get("system"));
      String code = this.escaper.escape(this.fromValue(n.get("code")));

      String systemRoot = stripEnd(system, "/", "#");

      String base;
      if (CODE_SYSTEM_MAP.containsKey(systemRoot)) {
        base = CODE_SYSTEM_MAP.get(systemRoot) + ":";
      } else{
        base = system + ((system.endsWith("/") || system.endsWith("#")) ? "" : "/");
      }

      n.put("@type", base + code);
    }

    return n;
  }

  private static String stripEnd(String s, String... toStrip) {
    for (String strip : toStrip) {
      s = StringUtils.stripEnd(s, strip);
    }

    return s;
  }

}
