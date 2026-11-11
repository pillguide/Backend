package kr.co.pillguide.backend.api.drug.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Model identities only. Inclusion does not imply official metadata availability. */
@Component
public class SupportedDrugCatalog {
    public record SupportedDrug(Integer classId, String productCode, String itemSeq, String modelName) { }
    private record Document(String sourceMappingSha256, List<SupportedDrug> drugs) { }

    private final String mappingSha256;
    private final List<SupportedDrug> drugs;
    private final Map<String, SupportedDrug> byItemSeq;
    private final Map<Integer, SupportedDrug> byClassId;

    public SupportedDrugCatalog() {
        this(SupportedDrugCatalog.class.getResourceAsStream("/reference/supported-drugs.json"));
    }

    SupportedDrugCatalog(InputStream input) {
        if (input == null) throw new IllegalStateException("Supported drug catalog is missing");
        try (input) {
            Document document = new ObjectMapper().readValue(input, Document.class);
            if (document.sourceMappingSha256() == null || !document.sourceMappingSha256().matches("[0-9a-f]{64}")
                    || document.drugs() == null || document.drugs().size() != 150) {
                throw new IllegalStateException("Invalid supported drug catalog");
            }
            var ids = new HashSet<Integer>();
            var codes = new HashSet<String>();
            var products = new HashSet<String>();
            for (SupportedDrug drug : document.drugs()) {
                if (drug == null || drug.classId() == null || drug.classId() < 0 || drug.classId() >= 150
                        || drug.itemSeq() == null || !drug.itemSeq().matches("[0-9]{9}")
                        || drug.productCode() == null || !drug.productCode().matches("K-[0-9]{6}")
                        || drug.modelName() == null || drug.modelName().isBlank()
                        || !ids.add(drug.classId()) || !codes.add(drug.itemSeq()) || !products.add(drug.productCode())) {
                    throw new IllegalStateException("Invalid or duplicate supported drug identity");
                }
            }
            this.mappingSha256 = document.sourceMappingSha256();
            this.drugs = List.copyOf(document.drugs());
            this.byItemSeq = drugs.stream().collect(Collectors.toUnmodifiableMap(SupportedDrug::itemSeq, Function.identity()));
            this.byClassId = drugs.stream().collect(Collectors.toUnmodifiableMap(SupportedDrug::classId, Function.identity()));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read supported drug catalog", e);
        }
    }

    public String mappingSha256() { return mappingSha256; }
    public List<SupportedDrug> all() { return drugs; }
    public Optional<SupportedDrug> byItemSeq(String itemSeq) { return Optional.ofNullable(byItemSeq.get(itemSeq)); }
    public Optional<SupportedDrug> byClassId(int classId) { return Optional.ofNullable(byClassId.get(classId)); }
}
