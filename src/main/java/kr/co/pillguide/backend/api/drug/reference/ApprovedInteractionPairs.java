package kr.co.pillguide.backend.api.drug.reference;

import java.util.List;

/** Reviewed scope, not a claim that the current upstream evidence has been fetched. */
public final class ApprovedInteractionPairs {
    public enum EvidenceKind { DIRECT_ITEM, INGREDIENT_LINK }
    public record Pair(String firstItemSeq, String secondItemSeq, EvidenceKind kind) { }
    public static final List<Pair> ALL = List.of(
            new Pair("197500541", "200001783", EvidenceKind.DIRECT_ITEM),
            new Pair("199906953", "201401986", EvidenceKind.DIRECT_ITEM),
            new Pair("199906953", "201606824", EvidenceKind.DIRECT_ITEM),
            new Pair("200811061", "201309347", EvidenceKind.INGREDIENT_LINK));

    private ApprovedInteractionPairs() { }
}
