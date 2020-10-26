package org.fhircat.jsonld.cli;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;

public class FsvProcessor {

  private Model model;

  FsvProcessor() {
    Model model = ModelFactory.createDefaultModel();
    model.read(this.getClass().getClassLoader().getResourceAsStream("fhir-r4/fhir.ttl"), null,
        "TURTLE");

    this.model = model;
  }

  public String flat_path(List<String> path) {
    return FHIR.FHIR_NS + StringUtils.join(path, ".");
  }

  Optional<String> isDate(List<String> path) {
    return this.traverse(path, Sets.newHashSet(FHIR.DATE, FHIR.TIME, FHIR.DATE_TIME))
        .flatMap(node -> Optional.of(node.asResource().getURI()));
  }

  boolean isCanonical(List<String> path) {
    return this.traverse(path, Sets.newHashSet(FHIR.CANONICAL)).isPresent();
  }

  private Optional<RDFNode> getRange(List<String> path) {
    String dotPath = this.flat_path(path);

    Resource dotPathResource = model.createResource(dotPath);

    boolean containsDotPath = this.model.containsResource(dotPathResource);

    if (containsDotPath) {

      NodeIterator rangeObjects = this.model.listObjectsOfProperty(dotPathResource, RDFS.range);

      if (rangeObjects.hasNext()) {
        RDFNode range = rangeObjects.next();

        if (rangeObjects.hasNext()) {
          throw new RuntimeException("Too many ranges found.");
        }

        return Optional.of(range);
      }
    }

    return Optional.empty();
  }

  Optional<RDFNode> traverse(List<String> path, Set<String> targetTypes) {
    Optional<RDFNode> range = this.getRange(path);

    if (range.isPresent()) {
      if (targetTypes.contains(range.get().asResource().getURI())) {
        return Optional.of(range.get());
      } else {
        return Optional.empty();
      }

    }

    RDFNode pathRange = null;
    int i = path.size() - 1;
    for (;i>0;i--) {
      Optional<RDFNode> newRange = this.getRange(path.subList(0, i));

      if (newRange.isPresent()) {
        pathRange = newRange.get();
        break;
      }
    }

    if (pathRange == null) {
      return Optional.empty();
    }

    String rangeName = pathRange.asResource().getLocalName();

    List<String> recurseList = Lists.newArrayList();
    recurseList.add(rangeName);
    recurseList.addAll(path.subList(i, path.size()));

    return this.traverse(recurseList, targetTypes);
  }

}
