package kr.co.pillguide.backend.api.drug.reference;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.pillguide.backend.api.drug.entity.Drug;
import kr.co.pillguide.backend.api.drug.entity.DrugInteraction;
import kr.co.pillguide.backend.api.drug.repository.DrugInteractionRepository;
import kr.co.pillguide.backend.api.drug.repository.DrugRepository;
import kr.co.pillguide.backend.common.exception.BaseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static kr.co.pillguide.backend.api.drug.reference.ReferenceSnapshot.*;

@Service
public class ReferenceReadService {
    private final ReferenceSyncRepository states;
    private final DrugRepository drugs;
    private final DrugInteractionRepository interactions;
    private final SupportedDrugCatalog catalog;
    private final ObjectMapper mapper;
    private final long refreshDays;

    public ReferenceReadService(ReferenceSyncRepository states, DrugRepository drugs,
            DrugInteractionRepository interactions, SupportedDrugCatalog catalog, ObjectMapper mapper,
            @Value("${reference.sync.refresh-days:7}") long refreshDays) {
        this.states = states;
        this.drugs = drugs;
        this.interactions = interactions;
        this.catalog = catalog;
        this.mapper = mapper;
        this.refreshDays = refreshDays;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRES_NEW)
    public ReferenceSnapshot read(Set<String> itemSeqs) {
        if (itemSeqs == null || itemSeqs.stream().anyMatch(code -> catalog.byItemSeq(code).isEmpty()))
            throw new IllegalArgumentException("Only validated model identities can be read");
        ReferenceSyncState state = states.findById(1L)
                .filter(s -> s.getCurrentVersion() != null && s.getSourceAsOf() != null && s.getLastSuccessAt() != null)
                .orElseThrow(() -> new BaseException(HttpStatus.SERVICE_UNAVAILABLE, "약품 참조자료를 준비 중입니다. 잠시 후 다시 시도해 주세요."));
        Map<String, ReferencedDrug> selected = new HashMap<>();
        if (!itemSeqs.isEmpty()) for (Drug drug : drugs.findAllByCodeIn(itemSeqs)) {
            if (!state.getSourceAsOf().equals(drug.getSourceAsOf()) || drug.getDrugInfo() == null
                    || drug.getInfoStatus() == null || drug.getAuthorizationStatus() == null) throw corrupt();
            selected.put(drug.getCode(), new ReferencedDrug(drug.getDrugId(), drug.getCode(), drug.getName(),
                    drug.getInfoStatus(), drug.getAuthorizationStatus()));
        }
        if (!selected.keySet().equals(itemSeqs)) throw corrupt();
        List<DrugInteraction> rules = interactions.findAll();
        if (rules.size() != ApprovedInteractionPairs.ALL.size()) throw corrupt();
        Set<String> seen = new HashSet<>();
        List<Match> matches = new ArrayList<>();
        for (DrugInteraction rule : rules) {
            Drug low = rule.getLowDrug();
            Drug high = rule.getHighDrug();
            var approved = ApprovedInteractionPairs.ALL.stream().filter(p ->
                    Set.of(p.firstItemSeq(), p.secondItemSeq()).equals(Set.of(low.getCode(), high.getCode())))
                    .findFirst().orElseThrow(ReferenceReadService::corrupt);
            String key = approved.firstItemSeq() + "|" + approved.secondItemSeq();
            if (!seen.add(key) || !state.getCurrentVersion().equals(rule.getReferenceVersion())
                    || !state.getSourceAsOf().equals(rule.getSourceAsOf())
                    || !rule.getSourceHash().equals(rule.getSummarySourceHash())
                    || rule.getReviewedSummary() == null) throw corrupt();
            if (!selected.containsKey(low.getCode()) || !selected.containsKey(high.getCode())) continue;
            matches.add(new Match(low.getDrugId(), high.getDrugId(), low.getCode(), high.getCode(),
                    low.getName(), high.getName(), approved.kind().name(), rule.getOfficialReason(),
                    rule.getReviewedSummary(), rule.getSourceHash(), rule.getSourceAsOf(), evidence(rule)));
        }
        matches.sort(Comparator.comparing(Match::firstItemSeq).thenComparing(Match::secondItemSeq));
        boolean delayed = state.getLastFailureCode() != null || state.getLastSuccessAt().plusDays(refreshDays)
                .isBefore(LocalDateTime.now(ZoneId.of("Asia/Seoul")));
        String coverage = selected.values().stream().allMatch(d -> "COMPLETE".equals(d.infoStatus()))
                ? "REVIEWED_LIST_ONLY" : "REFERENCE_DATA_PARTIAL";
        int unique = selected.size();
        Combination combination = new Combination(unique, unique * (unique - 1) / 2, matches.size(), matches,
                coverage, state.getSourceAsOf(), delayed);
        return new ReferenceSnapshot(state.getCurrentVersion(), state.getSourceAsOf(), delayed, selected, combination);
    }

    private List<Map<String, Object>> evidence(DrugInteraction rule) {
        try {
            List<Map<String, Object>> rows = mapper.readValue(rule.getEvidenceJson(), new TypeReference<>() { });
            if (rows == null || rows.isEmpty()) throw corrupt();
            return rows;
        } catch (Exception ex) { throw corrupt(); }
    }

    private static IllegalStateException corrupt() {
        return new IllegalStateException("Reference generation integrity check failed");
    }
}
