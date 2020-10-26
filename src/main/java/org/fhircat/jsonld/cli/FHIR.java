package org.fhircat.jsonld.cli;

import java.util.regex.Pattern;

public class FHIR {

  public static final String FHIR_NS = "http://hl7.org/fhir/";

  public static final String CANONICAL = FHIR_NS + "canonical";

  public static final String DATE = FHIR_NS + "date";
  public static final String TIME = FHIR_NS + "time";
  public static final String DATE_TIME = FHIR_NS + "dateTime";

  public static Pattern R5_FHIR_URI_RE = Pattern.compile("((http|https):\\/\\/([A-Za-z0-9\\-\\\\.\\:\\%\\$]*\\/)+)?(Account|ActivityDefinition|" +
  "AdministrableProductDefinition|AdverseEvent|AllergyIntolerance|Appointment|AppointmentResponse|" +
  "AuditEvent|Basic|Binary|BiologicallyDerivedProduct|BodyStructure|Bundle|CapabilityStatement|" +
  "CapabilityStatement2|CarePlan|CareTeam|CatalogEntry|ChargeItem|ChargeItemDefinition|Claim|" +
  "ClaimResponse|ClinicalImpression|ClinicalUseIssue|CodeSystem|Communication|CommunicationRequest|" +
  "CompartmentDefinition|Composition|ConceptMap|Condition|ConditionDefinition|Consent|Contract|" +
  "Coverage|CoverageEligibilityRequest|CoverageEligibilityResponse|DetectedIssue|Device|" +
  "DeviceDefinition|DeviceMetric|DeviceRequest|DeviceUseStatement|DiagnosticReport|DocumentManifest|" +
  "DocumentReference|EightBall|Encounter|Endpoint|EnrollmentRequest|EnrollmentResponse|EpisodeOfCare|" +
  "EventDefinition|Evidence|EvidenceVariable|ExampleScenario|ExplanationOfBenefit|" +
  "FamilyMemberHistory|Flag|Goal|GraphDefinition|Group|GuidanceResponse|HealthcareService|" +
  "ImagingStudy|Immunization|ImmunizationEvaluation|ImmunizationRecommendation|ImplementationGuide|" +
  "Ingredient|InsurancePlan|Invoice|Library|Linkage|List|Location|ManufacturedItemDefinition|" +
  "Measure|MeasureReport|Medication|MedicationAdministration|MedicationDispense|" +
  "MedicationKnowledge|MedicationRequest|MedicationUsage|MedicinalProductDefinition|" +
  "MessageDefinition|MessageHeader|MolecularSequence|NamingSystem|NutritionIntake|NutritionOrder|" +
  "NutritionProduct|Observation|ObservationDefinition|OperationDefinition|OperationOutcome|" +
  "Organization|OrganizationAffiliation|PackagedProductDefinition|Patient|PaymentNotice|" +
  "PaymentReconciliation|Permission|Person|PlanDefinition|Practitioner|PractitionerRole|Procedure|" +
  "Provenance|Questionnaire|QuestionnaireResponse|RegulatedAuthorization|RelatedPerson|RequestGroup|" +
  "ResearchStudy|ResearchSubject|RiskAssessment|Schedule|SearchParameter|ServiceRequest|Slot|" +
  "Specimen|SpecimenDefinition|StructureDefinition|StructureMap|Subscription|Substance|" +
  "SubstanceDefinition|SubstanceNucleicAcid|SubstancePolymer|SubstanceProtein|" +
  "SubstanceReferenceInformation|SubstanceSourceMaterial|SupplyDelivery|SupplyRequest|Task|" +
  "TerminologyCapabilities|TestReport|TestScript|Topic|ValueSet|VerificationResult|" +
  "VisionPrescription)\\/[A-Za-z0-9\\-\\.]{1,64}(\\/_history\\/[A-Za-z0-9\\-\\.]{1,64})?$");

}
