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

  Pattern gYear_re = Pattern.compile("([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)$");
  Pattern gYearMonth_re = Pattern
      .compile("([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)(-(0[1-9]|1[0-2]))$");
  Pattern date_re = Pattern.compile(
      "([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)(-(0[1-9]|1[0-2])(-(0[1-9]|[1-2][0-9]|3[0-1]))?)?$");
  Pattern dateTime_re = Pattern.compile("([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)" +
      "(-(0[1-9]|1[0-2])(-(0[1-9]|[1-2][0-9]|3[0-1])" +
      "(T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\\.[0-9]+)?" +
      "(Z|(\\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00))))?)$");
  Pattern time_re = Pattern.compile("([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\\.[0-9]+)?$");


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

  public Map toR4(Map fhir_json, String versionBase, String contextserver, String fhirserver, boolean addContext) {
    //# Do the recursive conversion
    String resource_type = (String) fhir_json.get(RESOURCETYPE_KEY); //     # Pick this up before it processed for use in context below
    dict_processor(fhir_json, resource_type, Lists.newArrayList(), Maps.newHashMap(), false, contextserver, fhirserver);

    //# Traverse the graph adjusting relative URL's
    adjust_urls(fhir_json, "");

    //# Add nodeRole
    fhir_json.put("nodeRole", "fhir:treeRoot");

    this.addOntologyHeader(fhir_json, versionBase);

    if (addContext) {
      this.addContext(fhir_json, resource_type, contextserver, fhirserver);
    }

    return fhir_json;
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
      System.out.println("JSON does not have an identifier");
    }
  }

  private void addContext(Map<String, Object> fhir_json, String resource_type, String contextserver, String fhirserver) {
    List contexts = Lists.newArrayList(
        contextserver + resource_type.toLowerCase() + ".context.jsonld",
        contextserver + "root.context.jsonld");

    fhir_json.put("@context", contexts);

    Map<String, Object> localContext = new HashMap<>();

    Map<String, Object> nodeRole = new HashMap<>();
    nodeRole.put("@type", "@id");
    nodeRole.put("@id", "fhir:nodeRole");

    localContext.put("nodeRole", nodeRole);

    if (fhirserver != null) {
      localContext.put("@base", fhirserver);
    }

    Map<String, Object> imports = new HashMap<>();
    imports.put("@type", "@id");
    localContext.put("owl:imports", imports);

    Map<String, Object> versionIri = new HashMap<>();
    versionIri.put("@type", "@id");
    localContext.put("owl:versionIRI", versionIri);

    ((List) fhir_json.get("@context")).add(localContext);

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
      if (gYear_re.matcher(dateString).matches()) {
        dt = XSD.gYear;
      } else if (gYearMonth_re.matcher(dateString).matches()) {
        dt = XSD.gYearMonth;
      } else if (date_re.matcher(dateString).matches()) {
        dt = XSD.date;
      } else if (dateTime_re.matcher(dateString).matches()) {
        dt = XSD.dateTime;
      }
    }

    if (dt != null) {
      Map typed_obj = new HashMap();
      typed_obj.put("@value", dateString);
      typed_obj.put("@type", dt.getURI());
      json.put("value", typed_obj);
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

  public Map genReference(String ref, Map refobject, String server, Map<String, String> id_map) {
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

      if (id_map.containsKey(link)) {
        rval.put("@id", id_map.get(link));
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
      String contained_id = "#" + container.get(ID_KEY);
      String contained_type = (String) resource.get(RESOURCETYPE_KEY);
      idMap.put(contained_id, contained_type + '/' + resource.get(ID_KEY) + contained_id);
    }
  }

  private Map<String, String> bundle_urls(Map resource) {
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
          String resource_type = (String) resource.get(RESOURCETYPE_KEY);

          rval.put(resource_type + "/" + resourceId, fullUrl);
        }
      }
    }

    return rval;
  }

  private void adjust_urls(Object json, String outerUrl) {
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
        adjust_urls(value, outerUrl);
      }
    } else if (json instanceof List) {
      for (Object entry : (List) json) {
        adjust_urls(entry, outerUrl);
      }
    }
  }

  private void map_element(String element_key, Object element_value,
      String container_type, List<String> path, Map container,
      Map<String, String> id_map, boolean in_container, String resource_type, String contextserver, String fhirserver) {
    if (element_key.startsWith("@")) { //:  # Ignore JSON-LD components"
      return;
    }

    if (! this.is_choice_element(element_key)) {
      path.add(element_key);
    }
    if (path.equals(Lists.newArrayList("Coding", "system"))) {
      this.addTypeArc(container);
    }

    String inner_type = this.localName((String) container.getOrDefault(RESOURCETYPE_KEY, null));

    if (element_value instanceof Map) { //          # Inner object -- process each element\n"
      dict_processor((Map) element_value, resource_type, path, id_map, false, contextserver, fhirserver);
    } else if (element_value instanceof List) { //           # List -- process each member individually\n"
      container.put(element_key, this.list_processor(element_key, (List) element_value, resource_type, path, id_map, contextserver, fhirserver));
    } else if (element_key.equals(RESOURCETYPE_KEY) && element_value instanceof String && ! ((String) element_value).startsWith("fhir:")) {
      container.put(element_key, "fhir:" + element_value);
      container.put("@context",  contextserver + ((String) element_value).toLowerCase() + ".context.jsonld");
    } else if (element_key.equals(ID_KEY)) {
      String relative_id;
      if (in_container || !container.containsKey(RESOURCETYPE_KEY)) {
        relative_id = "#" + element_value;
      } else {
        if (((String) element_value).startsWith("#")) {
          relative_id = (String) element_value;
        } else {
          relative_id = ((inner_type == null ? container_type : inner_type) + '/' + element_value);
        }

      }

      String container_id = id_map != null ? id_map.getOrDefault(relative_id, relative_id) : relative_id;

      if (! container.containsKey("@id")) { //Bundle ids have already been added elsewhere
        container.put("@id", container_id);
      }

      container.put(element_key, this.toValue(element_value));
    } else if (! Sets.newHashSet(NODEROLE_KEY, INDEX_KEY, DIV_KEY).contains(element_key)) { //      # Convert most other nodes to value entries
      container.put(element_key, this.toValue(element_value));
    }

    if (! (element_value instanceof List)) {
      this.add_type_arcs(element_key, container.get(element_key), container, path, contextserver, id_map);
    }

    if (! this.is_choice_element(element_key)) {
      path.remove(path.size() - 1);
    }
  }

  private void dict_processor(Map<String, Object> container, String resource_type, List<String> path, Map<String, String> id_map, boolean in_container, String contextserver, String fhirserver) {
    if (container.containsKey(RESOURCETYPE_KEY)) {
      resource_type = (String) container.get(RESOURCETYPE_KEY);
      path = Lists.newArrayList(resource_type);
    }

    // If we've got bundle, build an id map to use in the interior
    Map<String, String> possible_id_map = this.bundle_urls(container); // # Note that this will also assign ids to bundle entries
    if (possible_id_map != null) {
      id_map = possible_id_map;
    } else if (id_map == null) {
      id_map = Maps.newHashMap();
    }

    // Add any contained resources to the contained URL map
    this.addContainedUrls(container, id_map);

    // Process each of the elements in the dictionary
    // Note: use keys() and re-look up to prevent losing the JsonObj characteristics of the values
    for (String key : container.keySet().stream().filter(k -> ! ((String) k).startsWith("_")).collect(Collectors.toList())) {
      if (this.is_choice_element(key)) {
        map_element(key, container.get(key), resource_type, Lists.newArrayList(key.substring(VALUE_KEY_LEN)), container, id_map, in_container, resource_type, contextserver, fhirserver);
      } else {
        map_element(key, container.get(key), resource_type, path, container, id_map, in_container, resource_type, contextserver, fhirserver);
      }
    }

    // Merge any extensions (keys that start with '_') into the base
    for (Map.Entry<String, Object> entry : container.entrySet().stream().filter(e -> e.getKey().startsWith("_")).collect(Collectors.toList())) {
      String base_key = entry.getKey().substring(1);

      Object ext_value = entry.getValue();
      container.remove(entry.getKey());

      if (! container.containsKey(base_key)) {
        container.put(base_key, ext_value); // No base -- move the extension in
      } else if (! (container.get(base_key) instanceof Map)) {
        Map newValue = this.toValue(container.get(base_key));
        container.put(base_key, newValue); //     #Base is not a JSON object
        if (ext_value instanceof Map) {
          newValue.put("extension", ((Map) ext_value).get("extension"));
        } else {
          newValue.put("extension", ext_value);
        }
      } else {
        ((Map) container.get(base_key)).put("extension", ((Map) ext_value).get("extension"));
      }

      map_element(base_key, ext_value, EXTENSION_RESOURCE_TYPE, Lists.newArrayList(EXTENSION_RESOURCE_TYPE), container, id_map, false, resource_type, contextserver, fhirserver);
    }

  }

  private List<Object> list_processor(String list_key, List<Object> list_object, String resource_type, List<String> path, Map<String, String> id_map, String contextserver, String fhirserver) {

    BiFunction<Object, Integer, Object> list_element = ((entry, pos) -> {
      if (entry instanceof Map) {
        dict_processor((Map) entry, resource_type, path, id_map, list_key.contains(CONTAINED_KEY), contextserver, fhirserver);
        if (((Map) entry).containsKey(INDEX_KEY) && !this.fsvProcessor.flat_path(path)
            .contains("_")) {
          throw new RuntimeException();
        } else {
          ((Map) entry).put("index", pos);
        }

        if (list_key.equals(CODING_KEY)) {
          this.addTypeArc((Map) entry);
        }
      } else if (entry instanceof List) {
        throw new RuntimeException();
      } else {
        entry = this.toValue(entry);
        //add_type_arcs(list_key, entry, entry, path, opts, server, id_map)
        this.add_type_arcs(list_key, (Map) entry, (Map) entry, path, fhirserver, id_map);
        ((Map) entry).put("index", pos);
      }

      return entry;
    });

    List returnList = Lists.newArrayList();

    for (int i = 0; i < list_object.size(); i++) {
      returnList.add(list_element.apply(list_object.get(i), i));
    }

    return returnList;
  }

  private void add_type_arcs(String elementKey, Object container, Map<String, Object> parentContainer, List<String> path, String server, Map<String, String> id_map) {
    if (this.fsvProcessor.isCanonical(path)) {
      ((Map) container).put("fhir:link", this.genReference((String) this.fromValue(container), (Map) container, server, id_map));
    } else if (elementKey.equals(REFERENCE_KEY)) {
      Object container_value = this.fromValue(container);

      if (container_value instanceof String) {
        Map ref = this.genReference((String) container_value, (Map) container, server, id_map);
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

  private boolean is_choice_element(String name) {
    return name.startsWith(VALUE_KEY) &&
        StringUtils.isNotEmpty(name.substring(VALUE_KEY_LEN)) &&
        CharUtils.isAsciiAlphaUpper(name.charAt(VALUE_KEY_LEN)) &&
        !name.substring(VALUE_KEY_LEN).equals("Set");
  }

}