package kr.co.pillguide.backend.api.drug.reference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReferenceCollectorTest {
    private static final LocalDateTime FETCHED = LocalDateTime.of(2026, 11, 11, 10, 0);
    private final ObjectMapper mapper = new ObjectMapper();
    private final SupportedDrugCatalog catalog = new SupportedDrugCatalog();

    @Test
    void completeBatchKeepsModelIdentitySelectedFieldsAndFourReviewedPairs() throws Exception {
        Fixture fixture = new Fixture();
        AtomicInteger attempts = new AtomicInteger();
        ReferenceBatch batch = fixture.collect(attempts::incrementAndGet);

        assertEquals(751, attempts.get());
        assertEquals(150, batch.drugs().size());
        assertEquals(4, batch.interactions().size());
        assertEquals(catalog.mappingSha256(), fixture.catalog.mappingSha256());
        assertEquals("M203364, M250460", batch.drugs().stream()
                .filter(d -> d.itemSeq().equals("200811061")).findFirst().orElseThrow().ingredientDisplay());
        ReferenceBatch.DrugReference first = batch.drugs().getFirst();
        assertEquals("NORMAL", first.authorizationStatus());
        assertEquals("COMPLETE", first.infoStatus());
        assertFalse(first.selectedSourcesJson().contains("BIZRNO"));
        assertEquals(5, mapper.readTree(first.selectedSourcesJson()).path("sources").size());
        for (ReferenceBatch.InteractionReference interaction : batch.interactions()) {
            assertEquals(interaction.sourceHash(), interaction.summarySourceHash());
            assertEquals(64, interaction.sourceHash().length());
            assertNotNull(interaction.reviewedSummary());
        }
        JsonNode evidence = mapper.readTree(batch.interactions().get(2).evidenceJson());
        assertEquals(2, evidence.size());
        assertTrue(evidence.toString().contains("\"PROHBT_CONTENT\":null"));
    }

    @Test
    void unexpectedSupportedDirectPairBlocksEntireBatch() {
        Fixture fixture = new Fixture();
        fixture.extraDirect = true;
        ReferenceValidationException error = assertThrows(ReferenceValidationException.class,
                () -> fixture.collect(() -> { }));
        assertEquals("UNAPPROVED_DIRECT_PAIR", error.code());
    }

    @Test
    void changedOfficialReasonCannotReuseReviewedSummary() {
        Fixture fixture = new Fixture();
        fixture.changedReason = true;
        ReferenceValidationException error = assertThrows(ReferenceValidationException.class,
                () -> fixture.collect(() -> { }));
        assertEquals("REVIEW_REQUIRED", error.code());
    }

    @Test
    void missingIngredientMappingBlocksApprovedFourthPair() {
        Fixture fixture = new Fixture();
        fixture.missingMapping = true;
        ReferenceValidationException error = assertThrows(ReferenceValidationException.class,
                () -> fixture.collect(() -> { }));
        assertEquals("INGREDIENT_MAPPING_CHANGED", error.code());
    }

    @Test
    void ingredientProductOrManufacturerContradictionBlocksBatch() {
        Fixture wrongProduct = new Fixture();
        wrongProduct.wrongIngredientProduct = true;
        ReferenceValidationException productError = assertThrows(ReferenceValidationException.class,
                () -> wrongProduct.collect(() -> { }));
        assertEquals("INGREDIENT_IDENTITY_CONFLICT", productError.code());

        Fixture wrongManufacturer = new Fixture();
        wrongManufacturer.wrongIngredientManufacturer = true;
        ReferenceValidationException manufacturerError = assertThrows(ReferenceValidationException.class,
                () -> wrongManufacturer.collect(() -> { }));
        assertEquals("INGREDIENT_IDENTITY_CONFLICT", manufacturerError.code());
    }

    @Test
    void absentSecondaryIngredientLabelsKeepExactItemSequenceLink() {
        Fixture fixture = new Fixture();
        fixture.missingIngredientLabels = true;
        assertEquals(4, fixture.collect(() -> { }).interactions().size());
    }

    @Test
    void missingOfficialRecordKeepsSupportedIdentityWithoutInventingDescriptions() {
        Fixture fixture = new Fixture();
        fixture.missingOfficial = true;
        ReferenceBatch batch = fixture.collect(() -> { });
        ReferenceBatch.DrugReference drug = batch.drugs().stream()
                .filter(d -> d.itemSeq().equals("200108429")).findFirst().orElseThrow();
        assertNotNull(drug.modelName());
        assertEquals("NO_OFFICIAL_RECORD", drug.authorizationStatus());
        assertEquals("NO_OFFICIAL_RECORD", drug.infoStatus());
        assertNull(drug.officialName());
        assertNull(drug.effect());
    }

    @Test
    void externalDurCounterpartIsCountedButNotPersistedInDrugSelectedSources() throws Exception {
        Fixture fixture = new Fixture();
        fixture.outsideDirect = true;
        ReferenceBatch batch = fixture.collect(() -> { });
        ReferenceBatch.DrugReference drug = batch.drugs().stream()
                .filter(d -> d.itemSeq().equals("197500541")).findFirst().orElseThrow();
        JsonNode sources = mapper.readTree(drug.selectedSourcesJson()).path("sources");
        JsonNode dur = null;
        for (JsonNode source : sources) {
            if (source.path("source").asText().equals("DUR_PRODUCT")) dur = source;
        }
        assertNotNull(dur);
        assertEquals(2, dur.path("recordCount").asInt());
        assertEquals(1, dur.path("records").size());
        assertFalse(drug.selectedSourcesJson().contains("999999999"));
    }

    private final class Fixture {
        private final SupportedDrugCatalog catalog = ReferenceCollectorTest.this.catalog;
        boolean extraDirect;
        boolean changedReason;
        boolean missingMapping;
        boolean missingOfficial;
        boolean outsideDirect;
        boolean wrongIngredientProduct;
        boolean wrongIngredientManufacturer;
        boolean missingIngredientLabels;

        ReferenceBatch collect(Runnable beforeRequest) {
            MfdsClient client = mock(MfdsClient.class);
            when(client.fetch(any(MfdsClient.Service.class), nullable(String.class), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        MfdsClient.Service service = invocation.getArgument(0);
                        String seq = invocation.getArgument(1);
                        invocation.<Runnable>getArgument(2).run();
                        return new MfdsClient.SourceData(service,
                                seq == null ? "ALL" : seq, FETCHED, rows(service, seq));
                    });
            return new ReferenceCollector(client, catalog, mapper).collect(beforeRequest);
        }

        List<JsonNode> rows(MfdsClient.Service service, String seq) {
            if (service == MfdsClient.Service.DUR_INGREDIENT) {
                return List.of(row(Map.of("TYPE_NAME", "병용금기", "INGR_CODE", "D000448",
                        "MIXTURE_INGR_CODE", "D000698", "MIX_TYPE", "단일",
                        "MIXTURE_MIX_TYPE", "단일", "ORI", "[M203364]슈도에페드린염산염",
                        "MIXTURE_ORI", "[M257939]라사길린메실산염", "DEL_YN", "정상",
                        "PROHBT_CONTENT", "고혈압, 빈맥 유발")));
            }
            if (missingOfficial && "200108429".equals(seq)) return List.of();
            if (service == MfdsClient.Service.EASY) return List.of();
            if (service == MfdsClient.Service.PERMIT_LIST) {
                return List.of(row(Map.of("ITEM_SEQ", seq, "PRDLST_STDR_CODE", seq,
                        "ITEM_NAME", "공식 " + seq, "ENTP_NAME", "제약사", "CANCEL_NAME", "정상", "PRDUCT_TYPE", "[02130]이뇨제",
                        "BIZRNO", "must-not-be-persisted")));
            }
            if (service == MfdsClient.Service.PERMIT_DETAIL) {
                return List.of(row(Map.of("ITEM_SEQ", seq, "ITEM_NAME", "공식 " + seq,
                        "ENTP_NAME", "제약사", "CANCEL_NAME", "정상")));
            }
            if (service == MfdsClient.Service.PERMIT_INGREDIENTS) {
                List<JsonNode> result = new ArrayList<>();
                if (seq.equals("200811061")) {
                    result.add(ingredient(seq, missingMapping ? "M000001" : "M203364"));
                    result.add(ingredient(seq, "M250460"));
                } else {
                    String code = switch (seq) {
                        case "197500541" -> "M099796";
                        case "200001783" -> "M040438";
                        case "199906953" -> "M223198";
                        case "201401986" -> "M230909";
                        case "201606824" -> "M091703";
                        case "201309347" -> "M257939";
                        default -> "M000001";
                    };
                    result.add(ingredient(seq, code));
                }
                return result;
            }
            if (service == MfdsClient.Service.DUR_PRODUCT) {
                List<JsonNode> result = new ArrayList<>();
                if (seq.equals("197500541")) result.add(direct(seq, "200001783", "고칼륨혈증"));
                if (seq.equals("199906953")) {
                    result.add(direct(seq, "201401986", changedReason ? "새 사유" : "실로도신의 혈중농도 증가"));
                    result.add(direct(seq, "201606824", "콜키신에 대한 노출 증가 가능"));
                    ObjectNode noReason = direct(seq, "201606824", null);
                    result.add(noReason);
                }
                if (extraDirect && seq.equals("197500541")) {
                    result.add(direct(seq, "201309347", "new pair"));
                }
                if (outsideDirect && seq.equals("197500541")) {
                    result.add(direct(seq, "999999999", "outside supported set"));
                }
                return result;
            }
            throw new IllegalStateException();
        }

        private ObjectNode ingredient(String seq, String code) {
            if (missingIngredientLabels) {
                return row(Map.of("ITEM_SEQ", seq, "MTRAL_CODE", code, "MTRAL_NM", code));
            }
            String product = wrongIngredientProduct && seq.equals("200811061") ? "다른 품목" : "공식 " + seq;
            String manufacturer = wrongIngredientManufacturer && seq.equals("200811061") ? "다른 업체" : "제약사";
            return row(Map.of("ITEM_SEQ", seq, "MTRAL_CODE", code, "MTRAL_NM", code,
                    "PRDUCT", product, "ENTRPS", manufacturer));
        }

        private ObjectNode direct(String seq, String other, String reason) {
            ObjectNode row = row(Map.of("ITEM_SEQ", seq, "MIXTURE_ITEM_SEQ", other,
                    "TYPE_NAME", "병용금기", "TYPE_CODE", "A", "DUR_SEQ", "1",
                    "MIX", "단일", "MIXTURE_MIX", "단일"));
            switch (other) {
                case "200001783" -> {
                    row.put("INGR_CODE", "D000731"); row.put("MIXTURE_INGR_CODE", "D000565");
                    row.put("MAIN_INGR", "[M099796]스피로노락톤");
                    row.put("MIXTURE_MAIN_INGR", "[M040438]염화칼륨");
                }
                case "201401986" -> {
                    row.put("INGR_CODE", "D000373"); row.put("MIXTURE_INGR_CODE", "D000342");
                    row.put("MAIN_INGR", "[M223198]클래리트로마이신");
                    row.put("MIXTURE_MAIN_INGR", "[M230909]실로도신");
                }
                case "201606824" -> {
                    row.put("INGR_CODE", "D000373"); row.put("MIXTURE_INGR_CODE", "D000582");
                    row.put("MAIN_INGR", "[M223198]클래리트로마이신");
                    row.put("MIXTURE_MAIN_INGR", "[M091703]콜키신");
                }
            }
            if (reason == null) row.putNull("PROHBT_CONTENT");
            else row.put("PROHBT_CONTENT", reason);
            return row;
        }
    }

    private ObjectNode row(Map<String, String> values) {
        ObjectNode result = mapper.createObjectNode();
        values.forEach(result::put);
        return result;
    }
}
