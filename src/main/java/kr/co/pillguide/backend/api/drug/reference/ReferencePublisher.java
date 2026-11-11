package kr.co.pillguide.backend.api.drug.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.pillguide.backend.api.drug.entity.Drug;
import kr.co.pillguide.backend.api.drug.entity.DrugInfo;
import kr.co.pillguide.backend.api.drug.entity.DrugInteraction;
import kr.co.pillguide.backend.api.drug.repository.DrugInteractionRepository;
import kr.co.pillguide.backend.api.drug.repository.DrugRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Publishes only a complete checked candidate under the active-run lock. */
@Service
public class ReferencePublisher {
    private final SupportedDrugCatalog catalog;
    private final DrugRepository drugs;
    private final DrugInteractionRepository interactions;
    private final ReferenceSyncService syncService;
    private final ObjectMapper mapper;

    public ReferencePublisher(SupportedDrugCatalog catalog, DrugRepository drugs,
                              DrugInteractionRepository interactions, ReferenceSyncService syncService,
                              ObjectMapper mapper) {
        this.catalog = catalog;
        this.drugs = drugs;
        this.interactions = interactions;
        this.syncService = syncService;
        this.mapper = mapper;
    }

    @Transactional
    public void publish(UUID runId, ReferenceBatch batch) {
        validate(batch);
        var state = syncService.lockActive(runId);
        Set<String> itemSeqs = batch.drugs().stream().map(ReferenceBatch.DrugReference::itemSeq)
                .collect(Collectors.toSet());
        Map<String, Drug> byCode = new HashMap<>();
        for (Drug drug : drugs.findAllByCodeIn(itemSeqs)) byCode.put(drug.getCode(), drug);

        for (ReferenceBatch.DrugReference row : batch.drugs()) {
            Drug drug = byCode.computeIfAbsent(row.itemSeq(), code ->
                    new Drug(row.officialName() == null ? row.modelName() : row.officialName(), code, null));
            drug.updateReferenceData(row.officialName(), row.modelName(), row.productCode(),
                    row.classificationCode(), row.classificationName(), row.ingredientDisplay(),
                    row.authorizationStatus(), row.infoStatus(), batch.sourceAsOf());
            DrugInfo info = drug.getDrugInfo();
            if (info == null) {
                info = new DrugInfo(drug, row.dosageMethod(), row.storageMethod(),
                        row.sideEffects(), row.effect());
                drug.setDrugInfo(info);
            } else {
                info.updateDescriptions(row.effect(), row.dosageMethod(), row.storageMethod(), row.sideEffects());
            }
            info.updateSelectedSources(row.selectedSourcesJson(), row.infoStatus(), row.fetchedAt());
        }
        drugs.saveAllAndFlush(byCode.values());

        // The approved four pairs are the complete current rule set.
        interactions.deleteAllInBatch();
        for (ReferenceBatch.InteractionReference row : batch.interactions()) {
            Drug first = byCode.get(row.firstItemSeq());
            Drug second = byCode.get(row.secondItemSeq());
            Drug low = first.getDrugId() < second.getDrugId() ? first : second;
            Drug high = first.getDrugId() < second.getDrugId() ? second : first;
            interactions.save(new DrugInteraction(low, high, row.evidenceJson(), row.officialReason(),
                    row.reviewedSummary(), row.sourceHash(), row.summarySourceHash(),
                    batch.sourceAsOf(), batch.version()));
        }
        interactions.flush();
        syncService.complete(state, batch.version(), batch.sourceAsOf());
    }

    private void validate(ReferenceBatch batch) {
        if (batch == null || batch.version() == null || batch.version().isBlank()
                || batch.sourceAsOf() == null || batch.drugs() == null || batch.drugs().size() != 150
                || batch.interactions() == null || batch.interactions().size() != ApprovedInteractionPairs.ALL.size()) {
            throw invalid("BATCH_INCOMPLETE");
        }
        Map<String, SupportedDrugCatalog.SupportedDrug> expected = catalog.all().stream()
                .collect(Collectors.toMap(SupportedDrugCatalog.SupportedDrug::itemSeq, d -> d));
        Set<String> seen = new HashSet<>();
        for (ReferenceBatch.DrugReference row : batch.drugs()) {
            SupportedDrugCatalog.SupportedDrug model = row == null ? null : expected.get(row.itemSeq());
            if (model == null || !seen.add(row.itemSeq()) || !model.modelName().equals(row.modelName())
                    || !model.productCode().equals(row.productCode()) || row.infoStatus() == null
                    || row.infoStatus().isBlank() || row.fetchedAt() == null
                    || row.selectedSourcesJson() == null || row.selectedSourcesJson().isBlank()) {
                throw invalid("SUPPORTED_SET_INVALID");
            }
            try {
                if (!mapper.readTree(row.selectedSourcesJson()).isObject()) throw invalid("SELECTED_SOURCE_INVALID");
            } catch (Exception ex) {
                throw invalid("SELECTED_SOURCE_INVALID");
            }
        }
        if (!seen.equals(expected.keySet())) throw invalid("SUPPORTED_SET_INVALID");

        Set<String> approved = ApprovedInteractionPairs.ALL.stream()
                .map(p -> pairKey(p.firstItemSeq(), p.secondItemSeq())).collect(Collectors.toSet());
        Set<String> supplied = new HashSet<>();
        for (ReferenceBatch.InteractionReference row : batch.interactions()) {
            if (row == null || row.firstItemSeq() == null || row.secondItemSeq() == null
                    || row.firstItemSeq().equals(row.secondItemSeq())
                    || !approved.contains(pairKey(row.firstItemSeq(), row.secondItemSeq()))
                    || !supplied.add(pairKey(row.firstItemSeq(), row.secondItemSeq()))
                    || row.evidenceJson() == null || row.evidenceJson().isBlank()
                    || row.sourceHash() == null || !row.sourceHash().matches("[0-9a-fA-F]{64}")
                    || !row.sourceHash().equals(row.summarySourceHash())
                    || row.reviewedSummary() == null || row.reviewedSummary().isBlank()) {
                throw invalid("APPROVED_PAIR_INVALID");
            }
            try {
                if (!mapper.readTree(row.evidenceJson()).isArray()) throw invalid("EVIDENCE_INVALID");
            } catch (Exception ex) {
                throw invalid("EVIDENCE_INVALID");
            }
        }
        if (!supplied.equals(approved)) throw invalid("APPROVED_PAIR_INVALID");
    }

    private static String pairKey(String first, String second) {
        return first.compareTo(second) < 0 ? first + "|" + second : second + "|" + first;
    }

    private static ReferenceValidationException invalid(String code) {
        return new ReferenceValidationException(code, "Publication candidate rejected");
    }
}
