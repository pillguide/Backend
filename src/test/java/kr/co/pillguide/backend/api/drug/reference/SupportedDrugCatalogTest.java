package kr.co.pillguide.backend.api.drug.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SupportedDrugCatalogTest {
    @Test
    void catalogContains150StableModelIdentitiesAndAllFourReviewedPairs() {
        var catalog = new SupportedDrugCatalog();
        assertEquals(150, catalog.all().size());
        assertEquals("71569e7f4d277b5c6e69930086db7d52920a1c12dae3325a9cbc2234829e8bce", catalog.mappingSha256());
        assertEquals("197400246", catalog.byClassId(0).orElseThrow().itemSeq());
        for (var drug : catalog.all()) {
            assertEquals(drug, catalog.byItemSeq(drug.itemSeq()).orElseThrow());
            assertEquals(drug, catalog.byClassId(drug.classId()).orElseThrow());
        }
        assertEquals(4, ApprovedInteractionPairs.ALL.size());
        for (var pair : ApprovedInteractionPairs.ALL) {
            assertTrue(catalog.byItemSeq(pair.firstItemSeq()).isPresent());
            assertTrue(catalog.byItemSeq(pair.secondItemSeq()).isPresent());
            assertTrue(pair.firstItemSeq().compareTo(pair.secondItemSeq()) < 0);
        }
        assertTrue(catalog.byClassId(150).isEmpty());
        assertTrue(catalog.byItemSeq("000000000").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> catalog.all().clear());
    }

    @Test
    void duplicateOrMissingMappingFailsClosed() throws Exception {
        var mapper = new ObjectMapper();
        try (var source = getClass().getResourceAsStream("/reference/supported-drugs.json")) {
            var tree = mapper.readTree(source);
            var items = (com.fasterxml.jackson.databind.node.ArrayNode) tree.get("drugs");
            items.set(1, items.get(0).deepCopy());
            byte[] duplicate = mapper.writeValueAsBytes(tree);
            assertThrows(IllegalStateException.class, () -> new SupportedDrugCatalog(new ByteArrayInputStream(duplicate)));
            items.remove(149);
            byte[] incomplete = mapper.writeValueAsBytes(tree);
            assertThrows(IllegalStateException.class, () -> new SupportedDrugCatalog(new ByteArrayInputStream(incomplete)));
        }
        assertThrows(IllegalStateException.class, () -> new SupportedDrugCatalog(null));
        assertThrows(IllegalStateException.class, () -> new SupportedDrugCatalog(
                new ByteArrayInputStream("{broken".getBytes(StandardCharsets.UTF_8))));
    }
}
