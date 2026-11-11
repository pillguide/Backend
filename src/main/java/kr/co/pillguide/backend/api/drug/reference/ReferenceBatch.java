package kr.co.pillguide.backend.api.drug.reference;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** A fully checked, immutable input to the short reference publishing transaction. */
public record ReferenceBatch(String version, LocalDate sourceAsOf,
                             List<DrugReference> drugs, List<InteractionReference> interactions) {
    public ReferenceBatch {
        drugs = List.copyOf(drugs);
        interactions = List.copyOf(interactions);
    }

    public record DrugReference(String itemSeq, String modelName, String productCode,
                                String officialName, String classificationCode, String classificationName,
                                String ingredientDisplay, String authorizationStatus, String infoStatus,
                                String effect, String dosageMethod, String storageMethod, String sideEffects,
                                String selectedSourcesJson, LocalDateTime fetchedAt) { }

    public record InteractionReference(String firstItemSeq, String secondItemSeq, String evidenceJson,
                                       String officialReason, String reviewedSummary,
                                       String sourceHash, String summarySourceHash) { }
}
