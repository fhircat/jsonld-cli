package org.fhircat.jsonld.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.net.PercentEscaper;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.lang3.CharUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.XSD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pre-process raw JSON into a specialized format for JSONLD processing.
 */
public class Preprocess extends BaseOperation {

  private static Logger log = LoggerFactory.getLogger(ToRdf.class);

  private FsvProcessor fsvProcessor = new FsvProcessor();

  private ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  private static final String VALUE_TAG = "value";
  private static final String REFERENCE_KEY = "reference";
  private static final String CODE_KEY = "code";
  private static final String VALUE_KEY = "value";
  private static final String CONTAINED_KEY = "contained";
  private static final String ID_KEY = "id";
  private static final String RESOURCETYPE_KEY = "resourceType";

  private static final String BUNDLE_RESOURCE_TYPE = "Bundle";
  private static final String BUNDLE_ENTRY = "entry";
  private static final String BUNDLE_ENTRY_FULLURL = "fullUrl";
  private static final String BUNDLE_ENTRY_RESOURCE = "resource";
  private static final String TYPE_KEY = "type";

  private static final String EXTENSION_RESOURCE_TYPE = "Extension";

  private static final String NODEROLE_KEY = "nodeRole"; //nodeRole is one WE add, We don't use value notation
  private static final String INDEX_KEY = "index";       //index is one we add (although there appears to be a couple of native values)
  private static final String DIV_KEY = "div";           //div is not converted to value as per the spec

  private static final String CODING_KEY = "coding" ;     //Assumption is that ALL "coding" entries carry concept codes and that all entries are lists

  private static final int VALUE_KEY_LEN = VALUE_KEY.length();
  private static final String CONTEXT_SERVER = "https://fhircat.org/fhir-r5/original/contexts/";

  private PercentEscaper escaper = new PercentEscaper("-._", false);

  Pattern gYearRe = Pattern.compile("([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)$");
  Pattern gYearMonthRe = Pattern
      .compile("([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)(-(0[1-9]|1[0-2]))$");
  Pattern dateRe = Pattern.compile(
      "([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)(-(0[1-9]|1[0-2])(-(0[1-9]|[1-2][0-9]|3[0-1]))?)?$");
  Pattern dateTimeRe = Pattern.compile("([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)" +
      "(-(0[1-9]|1[0-2])(-(0[1-9]|[1-2][0-9]|3[0-1])" +
      "(T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\\.[0-9]+)?" +
      "(Z|(\\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00))))?)$");
  Pattern timeRe = Pattern.compile("([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\\.[0-9]+)?$");


  private static final Map<String, String> CODE_SYSTEM_MAP = new HashMap<>();

  static {
    CODE_SYSTEM_MAP.put("http://snomed.info/sct", "sct");
    CODE_SYSTEM_MAP.put("http://loinc.org", "loinc");
  }

  @Override
  protected void doRun(File inputFile, File outputFile, CommandLine command) {
    Consumer<File> fn = (file) -> {
      try {
        boolean addContext = command.hasOption("c");

        Map result = this.toR4(this.objectMapper.readValue(new FileReader(file), Map.class),
            command.getOptionValue("vb", "http://build.fhir.org/"),
            command.getOptionValue("cs", "https://fhircat.org/fhir-r5/original/contexts/"),
            command.getOptionValue("fs", "http://hl7.org/fhir/"),
            addContext
        );

        this.objectMapper.writeValue(new File(outputFile, file.getName()), result);
      } catch (Throwable e) {
        log.warn("Error writing file: " + file.getPath(), e);
      }
    };

    if (inputFile.isDirectory()) {
      if (!outputFile.isDirectory()) {
        throw new RuntimeException("If the input file is a directory, the output must be as well.");
      }

      Arrays.stream(inputFile.listFiles((dir, name) -> name.endsWith(".json"))).forEach(fn);
    } else {
      fn.accept(inputFile);
    }
  }

  public Map toR4(Map fhirJson, String versionBase, String contextServer, String fhirServer, boolean addContext) {
    //# Do the recursive conversion
    String resourceType = (String) fhirJson.get(RESOURCETYPE_KEY); //     # Pick this up before it processed for use in context below
    dictProcessor(fhirJson, resourceType, Lists.newArrayList(), Maps.newHashMap(), false, contextServer, fhirServer);

    //# Traverse the graph adjusting relative URL's
    adjustUrls(fhirJson, "");

    //# Add nodeRole
    fhirJson.put("nodeRole", "fhir:treeRoot");

    this.addOntologyHeader(fhirJson, versionBase);

    if (addContext) {
      this.addContext(fhirJson, resourceType, contextServer, fhirServer);
    }

    return fhirJson;
  }

  private void addOntologyHeader(Map<String, Object> json, String versionBase) {
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
      throw new RuntimeException("JSON does not have an identifier");
    }
  }

  private void addContext(Map<String, Object> fhirJson, String resourceType, String contextServer, String fhirServer) {
    List contexts = Lists.newArrayList(
        contextServer + resourceType.toLowerCase() + ".context.jsonld",
        contextServer + "root.context.jsonld");

    fhirJson.put("@context", contexts);

    Map<String, Object> localContext = new HashMap<>();

    Map<String, Object> nodeRole = new HashMap<>();
    nodeRole.put("@type", "@id");
    nodeRole.put("@id", "fhir:nodeRole");

    localContext.put("nodeRole", nodeRole);

    if (fhirServer != null) {
      localContext.put("@base", fhirServer);
    }

    Map<String, Object> imports = new HashMap<>();
    imports.put("@type", "@id");
    localContext.put("owl:imports", imports);

    Map<String, Object> versionIri = new HashMap<>();
    versionIri.put("@type", "@id");
    localContext.put("owl:versionIRI", versionIri);

    ((List) fhirJson.get("@context")).add(localContext);

  }

  private Map toValue(Object value) {
    Map valueMap = new HashMap();
    valueMap.put(VALUE_TAG, value);

    return valueMap;
  }

  private Object fromValue(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String) {
      return value;
    } else if (value instanceof Map) {
      return ((Map) value).get("value");
    } else {
      throw new UnsupportedOperationException();
    }
  }

  private String localName(String tag) {
    String localName = StringUtils.substringAfter(tag, "fhir:");

    if (StringUtils.isNotBlank(localName)) {
      return localName;
    } else
      return null;
  }

  private Map addDateType(String dateUri, Map json) {
    String dateString = (String) this.fromValue(json);

    Resource dt = null;
    if (dateUri.equals(FHIR.DATE) || dateUri.equals(FHIR.DATE_TIME)) {
      if (gYearRe.matcher(dateString).matches()) {
        dt = XSD.gYear;
      } else if (gYearMonthRe.matcher(dateString).matches()) {
        dt = XSD.gYearMonth;
      } else if (dateRe.matcher(dateString).matches()) {
        dt = XSD.date;
      } else if (dateTimeRe.matcher(dateString).matches()) {
        dt = XSD.dateTime;
      }
    }

    if (dt != null) {
      Map typedObj = new HashMap();
      typedObj.put("@value", dateString);
      typedObj.put("@type", dt.getURI());
      json.put("value", typedObj);
    }

    return json;
  }

  private Map<String, Object> addTypeArc(Map<String, Object> n) {
    if (n.containsKey("system") && (n.containsKey("code"))) {
      String system = (String) this.fromValue(n.get("system"));
      String code = this.escaper.escape((String) this.fromValue(n.get("code")));

      String systemRoot = stripEnd(system, "/", "#");

      String base;
      if (CODE_SYSTEM_MAP.containsKey(systemRoot)) {
        base = CODE_SYSTEM_MAP.get(systemRoot) + ":";
      } else {
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

  public Map genReference(String ref, Map refobject, String server, Map<String, String> idMap) {
    String link;

    if (!ref.contains("://") && !ref.startsWith("/")) {
      link = (server != null ? "" : "../") + ref;
    } else {
      link = ref;
    }

    String typ = null;

    if (link != null) {
      if (refobject.containsKey(TYPE_KEY)) {
        typ = (String) refobject.get(TYPE_KEY);
      } else {
        Matcher m = FHIR.R5_FHIR_URI_RE.matcher(ref);
        if (m.matches()) {
          typ = m.group(4);
        }
      }

      Map rval = new HashMap();

      if (idMap.containsKey(link)) {
        rval.put("@id", idMap.get(link));
      } else {
        rval.put("@id", link);
        if (typ != null) {
          rval.put("@type", "fhir:" + typ);
        }
      }

      return rval;
    }

    return null;
  }

  private void addContainedUrls(Map resource, Map<String, String> idMap) {
    List<Map> containers = (List<Map>) resource.getOrDefault(CONTAINED_KEY, Lists.newArrayList());

    for (Map container : containers) {
      String containedId = "#" + container.get(ID_KEY);
      String containedType = (String) resource.get(RESOURCETYPE_KEY);
      idMap.put(containedId, containedType + '/' + resource.get(ID_KEY) + containedId);
    }
  }

  private Map<String, String> bundleUrls(Map resource) {
    String resourceType = (String) resource.get(RESOURCETYPE_KEY);

    if (resourceType == null) {
      return null;
    }

    if (! resourceType.equals(BUNDLE_RESOURCE_TYPE)) {
      return null;
    }

    Map rval = Maps.newHashMap();

    List<Map> entries = (List<Map>) resource.getOrDefault(BUNDLE_ENTRY, Lists.newArrayList());

    for (Map entry : entries) {
      String fullUrl = (String) entry.get(BUNDLE_ENTRY_FULLURL);

      if (fullUrl != null) {
        resource = (Map) entry.get(BUNDLE_ENTRY_RESOURCE);

        if (resource != null) {
          resource.put("@id", fullUrl);

          String resourceId = (String) resource.get(ID_KEY);
          String innerResourceType = (String) resource.get(RESOURCETYPE_KEY);

          rval.put(innerResourceType + "/" + resourceId, fullUrl);
        }
      }
    }

    return rval;
  }

  private void adjustUrls(Object json, String outerUrl) {
    if (json instanceof Map) {
      Map map = (Map) json;

      if (map.containsKey("@id")) {
        String containerId = (String) map.get("@id");

        if (containerId.startsWith("#")) {
          containerId = outerUrl + containerId;
          map.put("@id", containerId);
        }

        outerUrl = containerId;
      }
      for (Object value : ((Map) json).values()) {
        adjustUrls(value, outerUrl);
      }
    } else if (json instanceof List) {
      for (Object entry : (List) json) {
        adjustUrls(entry, outerUrl);
      }
    }
  }

  private void mapElement(String elementKey, Object elementValue,
      String containerType, List<String> path, Map container,
      Map<String, String> idMap, boolean inContainer, String resourceType, String contextServer, String fhirServer) {
    if (elementKey.startsWith("@")) { //:  # Ignore JSON-LD components"
      return;
    }

    if (! this.isChoiceElement(elementKey)) {
      path.add(elementKey);
    }
    if (path.equals(Lists.newArrayList("Coding", "system"))) {
      this.addTypeArc(container);
    }

    String innerType = this.localName((String) container.getOrDefault(RESOURCETYPE_KEY, null));

    if (elementValue instanceof Map) { //          # Inner object -- process each element\n"
      dictProcessor((Map) elementValue, resourceType, path, idMap, false, contextServer, fhirServer);
    } else if (elementValue instanceof List) { //           # List -- process each member individually\n"
      container.put(elementKey, this.listProcessor(elementKey, (List) elementValue, resourceType, path, idMap, contextServer, fhirServer));
    } else if (elementKey.equals(RESOURCETYPE_KEY) && elementValue instanceof String && ! ((String) elementValue).startsWith("fhir:")) {
      container.put(elementKey, "fhir:" + elementValue);
      container.put("@context",  contextServer + ((String) elementValue).toLowerCase() + ".context.jsonld");
    } else if (elementKey.equals(ID_KEY)) {
      String relativeId;
      if (inContainer || !container.containsKey(RESOURCETYPE_KEY)) {
        relativeId = "#" + elementValue;
      } else {
        if (((String) elementValue).startsWith("#")) {
          relativeId = (String) elementValue;
        } else {
          relativeId = ((innerType == null ? containerType : innerType) + '/' + elementValue);
        }

      }

      String containerId = idMap != null ? idMap.getOrDefault(relativeId, relativeId) : relativeId;

      if (! container.containsKey("@id")) { //Bundle ids have already been added elsewhere
        container.put("@id", containerId);
      }

      container.put(elementKey, this.toValue(elementValue));
    } else if (! Sets.newHashSet(NODEROLE_KEY, INDEX_KEY, DIV_KEY).contains(elementKey)) { //      # Convert most other nodes to value entries
      container.put(elementKey, this.toValue(elementValue));
    }

    if (! (elementValue instanceof List)) {
      this.addTypeArcs(elementKey, container.get(elementKey), container, path, contextServer, idMap);
    }

    if (! this.isChoiceElement(elementKey)) {
      path.remove(path.size() - 1);
    }
  }

  private void dictProcessor(Map<String, Object> container, String resourceType, List<String> path, Map<String, String> idMap, boolean inContainer, String contextServer, String fhirServer) {
    if (container.containsKey(RESOURCETYPE_KEY)) {
      resourceType = (String) container.get(RESOURCETYPE_KEY);
      path = Lists.newArrayList(resourceType);
    }

    // If we've got bundle, build an id map to use in the interior
    Map<String, String> possibleIdMap = this.bundleUrls(container); // # Note that this will also assign ids to bundle entries
    if (possibleIdMap != null) {
      idMap = possibleIdMap;
    } else if (idMap == null) {
      idMap = Maps.newHashMap();
    }

    // Add any contained resources to the contained URL map
    this.addContainedUrls(container, idMap);

    // Process each of the elements in the dictionary
    // Note: use keys() and re-look up to prevent losing the JsonObj characteristics of the values
    for (String key : container.keySet().stream().filter(k -> ! ((String) k).startsWith("_")).collect(Collectors.toList())) {
      if (this.isChoiceElement(key)) {
        mapElement(key, container.get(key), resourceType, Lists.newArrayList(key.substring(VALUE_KEY_LEN)), container, idMap, inContainer, resourceType, contextServer, fhirServer);
      } else {
        mapElement(key, container.get(key), resourceType, path, container, idMap, inContainer, resourceType, contextServer, fhirServer);
      }
    }

    // Merge any extensions (keys that start with '_') into the base
    for (Map.Entry<String, Object> entry : container.entrySet().stream().filter(e -> e.getKey().startsWith("_")).collect(Collectors.toList())) {
      String baseKey = entry.getKey().substring(1);

      Object extValue = entry.getValue();
      container.remove(entry.getKey());

      if (! container.containsKey(baseKey)) {
        container.put(baseKey, extValue); // No base -- move the extension in
      } else if (! (container.get(baseKey) instanceof Map)) {
        Map newValue = this.toValue(container.get(baseKey));
        container.put(baseKey, newValue); //     #Base is not a JSON object
        if (extValue instanceof Map) {
          newValue.put("extension", ((Map) extValue).get("extension"));
        } else {
          newValue.put("extension", extValue);
        }
      } else {
        ((Map) container.get(baseKey)).put("extension", ((Map) extValue).get("extension"));
      }

      mapElement(baseKey, extValue, EXTENSION_RESOURCE_TYPE, Lists.newArrayList(EXTENSION_RESOURCE_TYPE), container, idMap, false, resourceType, contextServer, fhirServer);
    }

  }

  private List<Object> listProcessor(String listKey, List<Object> listObject, String resourceType, List<String> path, Map<String, String> idMap, String contextServer, String fhirServer) {

    BiFunction<Object, Integer, Object> listElement = ((entry, pos) -> {
      if (entry instanceof Map) {
        dictProcessor((Map) entry, resourceType, path, idMap, listKey.contains(CONTAINED_KEY), contextServer, fhirServer);
        if (((Map) entry).containsKey(INDEX_KEY) && !this.fsvProcessor.flatPath(path)
            .contains("_")) {
          throw new RuntimeException();
        } else {
          ((Map) entry).put("index", pos);
        }

        if (listKey.equals(CODING_KEY)) {
          this.addTypeArc((Map) entry);
        }
      } else if (entry instanceof List) {
        throw new RuntimeException();
      } else {
        entry = this.toValue(entry);
        //addTypeArcs(listKey, entry, entry, path, opts, server, idMap)
        this.addTypeArcs(listKey, (Map) entry, (Map) entry, path, fhirServer, idMap);
        ((Map) entry).put("index", pos);
      }

      return entry;
    });

    List returnList = Lists.newArrayList();

    for (int i = 0; i < listObject.size(); i++) {
      returnList.add(listElement.apply(listObject.get(i), i));
    }

    return returnList;
  }

  private void addTypeArcs(String elementKey, Object container, Map<String, Object> parentContainer, List<String> path, String server, Map<String, String> idMap) {
    if (this.fsvProcessor.isCanonical(path)) {
      ((Map) container).put("fhir:link", this.genReference((String) this.fromValue(container), (Map) container, server, idMap));
    } else if (elementKey.equals(REFERENCE_KEY)) {
      Object containerValue = this.fromValue(container);

      if (containerValue instanceof String) {
        Map ref = this.genReference((String) containerValue, (Map) container, server, idMap);
        parentContainer.put("fhir:link", ref);
      }
    }

    Optional<String> dateType = this.fsvProcessor.isDate(path);

    if (dateType.isPresent()) {
      this.addDateType(dateType.get(), (Map) container);
    }

    if (elementKey.equals(CODE_KEY)) {
      addTypeArc((Map) container);
    }
  }

  private boolean isChoiceElement(String name) {
    return name.startsWith(VALUE_KEY) &&
        StringUtils.isNotEmpty(name.substring(VALUE_KEY_LEN)) &&
        CharUtils.isAsciiAlphaUpper(name.charAt(VALUE_KEY_LEN)) &&
        !name.substring(VALUE_KEY_LEN).equals("Set");
  }

}