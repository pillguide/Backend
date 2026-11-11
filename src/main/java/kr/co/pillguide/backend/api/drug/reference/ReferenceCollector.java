package kr.co.pillguide.backend.api.drug.reference;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static kr.co.pillguide.backend.api.drug.reference.MfdsClient.Service;

/** Fetches a complete candidate. It does not write the database or run during scan requests. */
@Component
public class ReferenceCollector {
    private static final String SCHEMA_VERSION = "mfds-selected-v1";
    private static final Pattern CLASSIFICATION = Pattern.compile("^\\[([0-9]+)](.*)$", Pattern.DOTALL);
    private static final Pattern INGREDIENT_CODE = Pattern.compile("\\[(M[0-9]{6})]");
    private static final Map<Service, List<String>> FIELDS = Map.of(
            Service.EASY, List.of("itemSeq", "itemName", "entpName", "itemImage", "efcyQesitm", "useMethodQesitm", "atpnWarnQesitm", "atpnQesitm", "intrcQesitm", "seQesitm", "depositMethodQesitm", "updateDe", "openDe"),
            Service.PERMIT_LIST, List.of("ITEM_SEQ", "PRDLST_STDR_CODE", "ITEM_NAME", "ENTP_NAME", "SPCLTY_PBLC", "PRDUCT_TYPE", "BIG_PRDT_IMG_URL", "CANCEL_NAME", "CANCEL_DATE", "ITEM_INGR_NAME", "ITEM_INGR_CNT", "ITEM_ENG_NAME", "ENTP_ENG_NAME", "ITEM_PERMIT_DATE", "EDI_CODE"),
            Service.PERMIT_DETAIL, List.of("ITEM_SEQ", "ITEM_NAME", "ENTP_NAME", "ETC_OTC_CODE", "EE_DOC_DATA", "UD_DOC_DATA", "NB_DOC_DATA", "PN_DOC_DATA", "STORAGE_METHOD", "MAIN_ITEM_INGR", "MAIN_INGR_ENG", "CANCEL_NAME", "CANCEL_DATE", "CHANGE_DATE", "ATC_CODE", "ITEM_ENG_NAME", "ENTP_ENG_NAME", "ITEM_PERMIT_DATE", "CHART", "PACK_UNIT", "TOTAL_CONTENT", "VALID_TERM", "BAR_CODE", "EDI_CODE", "MATERIAL_NAME", "INGR_NAME", "EE_DOC_ID", "UD_DOC_ID", "NB_DOC_ID"),
            Service.PERMIT_INGREDIENTS, List.of("ITEM_SEQ", "TAMT_SEQ", "MTRAL_SN", "MTRAL_CODE", "MTRAL_NM", "MAIN_INGR_ENG", "QNT", "INGD_UNIT_CD", "CPNT_CTNT_CONT"),
            Service.DUR_PRODUCT, List.of("ITEM_SEQ", "MIXTURE_ITEM_SEQ", "ITEM_NAME", "MIXTURE_ITEM_NAME", "DUR_SEQ", "MIXTURE_DUR_SEQ", "TYPE_CODE", "TYPE_NAME", "INGR_CODE", "INGR_KOR_NAME", "INGR_ENG_NAME", "MAIN_INGR", "MIX", "MIX_INGR", "MIXTURE_INGR_CODE", "MIXTURE_INGR_KOR_NAME", "MIXTURE_INGR_ENG_NAME", "MIXTURE_MAIN_INGR", "MIXTURE_MIX", "PROHBT_CONTENT", "REMARK", "NOTIFICATION_DATE", "CHANGE_DATE", "MIXTURE_CHANGE_DATE", "FORM_CODE", "FORM_NAME", "MIXTURE_FORM_CODE", "MIXTURE_FORM_NAME"),
            Service.DUR_INGREDIENT, List.of("TYPE_NAME", "INGR_CODE", "INGR_KOR_NAME", "INGR_ENG_NAME", "MIX_TYPE", "MIX", "ORI", "MIXTURE_INGR_CODE", "MIXTURE_INGR_KOR_NAME", "MIXTURE_INGR_ENG_NAME", "MIXTURE_MIX_TYPE", "MIXTURE_MIX", "MIXTURE_ORI", "NOTIFICATION_DATE", "PROHBT_CONTENT", "REMARK", "DEL_YN"));
    private static final List<Service> ITEM_SERVICES = List.of(Service.EASY, Service.PERMIT_LIST,
            Service.PERMIT_DETAIL, Service.PERMIT_INGREDIENTS, Service.DUR_PRODUCT);

    private final MfdsClient client;
    private final SupportedDrugCatalog catalog;
    private final ObjectMapper mapper;

    public ReferenceCollector(MfdsClient client, SupportedDrugCatalog catalog, ObjectMapper mapper) {
        this.client = client;
        this.catalog = catalog;
        this.mapper = mapper;
    }

    public ReferenceBatch collect(Runnable beforeRequest) {
        if (beforeRequest == null) throw new IllegalArgumentException("beforeRequest is required");
        Map<String, Map<Service, MfdsClient.SourceData>> sources = new LinkedHashMap<>();
        for (SupportedDrugCatalog.SupportedDrug drug : catalog.all()) {
            Map<Service, MfdsClient.SourceData> perDrug = new EnumMap<>(Service.class);
            for (Service service : ITEM_SERVICES) {
                perDrug.put(service, client.fetch(service, drug.itemSeq(), beforeRequest));
            }
            sources.put(drug.itemSeq(), perDrug);
        }
        MfdsClient.SourceData ingredientRules = client.fetch(Service.DUR_INGREDIENT, null, beforeRequest);

        // Nothing is made publishable until every source has been obtained and checked.
        validateSources(sources, ingredientRules);
        List<ReferenceBatch.DrugReference> drugs = new ArrayList<>(catalog.all().size());
        for (SupportedDrugCatalog.SupportedDrug drug : catalog.all()) {
            drugs.add(assembleDrug(drug, sources.get(drug.itemSeq())));
        }
        List<ReferenceBatch.InteractionReference> interactions = assembleInteractions(sources, ingredientRules);
        LocalDate sourceAsOf = sources.values().stream().flatMap(m -> m.values().stream())
                .map(s -> s.fetchedAt().toLocalDate()).min(LocalDate::compareTo)
                .orElseThrow(() -> invalid("SOURCE_DATE_MISSING", "No item source date"));
        if (ingredientRules.fetchedAt().toLocalDate().isBefore(sourceAsOf)) {
            sourceAsOf = ingredientRules.fetchedAt().toLocalDate();
        }
        return new ReferenceBatch(UUID.randomUUID().toString(), sourceAsOf, drugs, interactions);
    }

    private void validateSources(Map<String, Map<Service, MfdsClient.SourceData>> sources,
                                 MfdsClient.SourceData ingredientRules) {
        if (sources.size() != 150 || catalog.all().size() != 150) {
            throw invalid("SUPPORTED_SET_INVALID", "Expected exactly 150 model identities");
        }
        for (var entry : sources.entrySet()) {
            for (Service service : ITEM_SERVICES) {
                MfdsClient.SourceData data = entry.getValue().get(service);
                if (data == null || data.service() != service || !entry.getKey().equals(data.queryKey())
                        || data.fetchedAt() == null || data.records() == null) {
                    throw invalid("SOURCE_INCOMPLETE", service + " / " + entry.getKey());
                }
                for (JsonNode row : data.records()) {
                    String key = service == Service.EASY ? "itemSeq" : "ITEM_SEQ";
                    if (row == null || !row.isObject() || !entry.getKey().equals(value(row, key))) {
                        throw invalid("ITEM_SEQ_MISMATCH", service + " / " + entry.getKey());
                    }
                    if (service == Service.PERMIT_LIST && hasText(row, "PRDLST_STDR_CODE")
                            && !entry.getKey().equals(value(row, "PRDLST_STDR_CODE"))) {
                        throw invalid("PRODUCT_CODE_MISMATCH", entry.getKey());
                    }
                    if (service == Service.PERMIT_INGREDIENTS) {
                        JsonNode list = first(entry.getValue().get(Service.PERMIT_LIST));
                        JsonNode detail = first(entry.getValue().get(Service.PERMIT_DETAIL));
                        if (!sameWhenPresent(value(row, "PRDUCT"), value(list, "ITEM_NAME"))
                                || !sameWhenPresent(value(row, "PRDUCT"), value(detail, "ITEM_NAME"))
                                || !sameWhenPresent(value(row, "ENTRPS"), value(list, "ENTP_NAME"))
                                || !sameWhenPresent(value(row, "ENTRPS"), value(detail, "ENTP_NAME"))) {
                            throw invalid("INGREDIENT_IDENTITY_CONFLICT", entry.getKey());
                        }
                    }
                }
                if (service != Service.DUR_PRODUCT && service != Service.PERMIT_INGREDIENTS
                        && data.records().size() > 1) {
                    throw invalid("AMBIGUOUS_ITEM", service + " / " + entry.getKey());
                }
            }
        }
        if (ingredientRules == null || ingredientRules.service() != Service.DUR_INGREDIENT
                || !"ALL".equals(ingredientRules.queryKey()) || ingredientRules.fetchedAt() == null
                || ingredientRules.records() == null || ingredientRules.records().isEmpty()
                || ingredientRules.records().stream().anyMatch(row -> row == null || !row.isObject())) {
            throw invalid("SOURCE_INCOMPLETE", "DUR_INGREDIENT / ALL");
        }
    }

    private ReferenceBatch.DrugReference assembleDrug(SupportedDrugCatalog.SupportedDrug drug,
                                                        Map<Service, MfdsClient.SourceData> data) {
        JsonNode easy = first(data.get(Service.EASY));
        JsonNode list = first(data.get(Service.PERMIT_LIST));
        JsonNode detail = first(data.get(Service.PERMIT_DETAIL));
        List<JsonNode> ingredients = data.get(Service.PERMIT_INGREDIENTS).records();
        if (hasText(list, "ITEM_NAME") && hasText(detail, "ITEM_NAME")
                && !value(list, "ITEM_NAME").equals(value(detail, "ITEM_NAME"))) {
            throw invalid("OFFICIAL_IDENTITY_CONFLICT", drug.itemSeq());
        }
        if (hasText(list, "CANCEL_NAME") && hasText(detail, "CANCEL_NAME")
                && !value(list, "CANCEL_NAME").equals(value(detail, "CANCEL_NAME"))) {
            throw invalid("AUTHORIZATION_CONFLICT", drug.itemSeq());
        }
        String officialName = coalesce(value(list, "ITEM_NAME"), value(detail, "ITEM_NAME"), value(easy, "itemName"));
        String classCode = null;
        String className = null;
        String classification = value(list, "PRDUCT_TYPE");
        if (classification != null) {
            Matcher match = CLASSIFICATION.matcher(classification);
            if (!match.matches() || match.group(2).isBlank()) {
                throw invalid("CLASSIFICATION_INVALID", drug.itemSeq());
            }
            classCode = match.group(1);
            className = match.group(2).trim();
        }
        String cancelName = coalesce(value(list, "CANCEL_NAME"), value(detail, "CANCEL_NAME"));
        String authorizationStatus = list == null && detail == null ? "NO_OFFICIAL_RECORD"
                : cancelName == null ? "UNKNOWN" : "정상".equals(cancelName) ? "NORMAL" : "ABNORMAL";
        String infoStatus = list == null && detail == null && easy == null && ingredients.isEmpty()
                ? "NO_OFFICIAL_RECORD" : list != null && detail != null && !ingredients.isEmpty()
                ? "COMPLETE" : "PARTIAL";
        List<String> ingredientNames = ingredients.stream().map(row -> value(row, "MTRAL_NM"))
                .filter(s -> s != null).distinct().toList();
        String ingredientDisplay = ingredientNames.isEmpty() ? coalesce(value(list, "ITEM_INGR_NAME"),
                value(detail, "MAIN_ITEM_INGR")) : String.join(", ", ingredientNames);
        LocalDateTime fetchedAt = data.values().stream().map(MfdsClient.SourceData::fetchedAt)
                .max(LocalDateTime::compareTo).orElseThrow();
        return new ReferenceBatch.DrugReference(drug.itemSeq(), drug.modelName(), drug.productCode(),
                officialName, classCode, className, ingredientDisplay, authorizationStatus, infoStatus,
                coalesce(value(easy, "efcyQesitm"), documentText(value(detail, "EE_DOC_DATA"))),
                coalesce(value(easy, "useMethodQesitm"), documentText(value(detail, "UD_DOC_DATA"))),
                coalesce(value(easy, "depositMethodQesitm"), value(detail, "STORAGE_METHOD")),
                value(easy, "seQesitm"), selectedSourcesJson(data), fetchedAt);
    }

    private String selectedSourcesJson(Map<Service, MfdsClient.SourceData> data) {
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        ArrayNode sources = root.putArray("sources");
        for (Service service : ITEM_SERVICES) {
            MfdsClient.SourceData source = data.get(service);
            ObjectNode entry = sources.addObject();
            entry.put("source", service.name());
            entry.put("queryKey", source.queryKey());
            entry.put("fetchedAt", source.fetchedAt().toString());
            entry.put("status", source.records().isEmpty() ? "NO_DATA" : "HAS_DATA");
            entry.put("recordCount", source.records().size());
            ArrayNode records = entry.putArray("records");
            source.records().stream()
                    .filter(row -> service != Service.DUR_PRODUCT
                            || isApprovedDirectPair(source.queryKey(), value(row, "MIXTURE_ITEM_SEQ")))
                    .forEach(row -> records.add(select(row, service)));
        }
        return json(root);
    }

    private List<ReferenceBatch.InteractionReference> assembleInteractions(
            Map<String, Map<Service, MfdsClient.SourceData>> sources,
            MfdsClient.SourceData ingredientRules) {
        Map<String, List<ObjectNode>> direct = new HashMap<>();
        for (var source : sources.entrySet()) {
            for (JsonNode row : source.getValue().get(Service.DUR_PRODUCT).records()) {
                String other = value(row, "MIXTURE_ITEM_SEQ");
                if (other == null) throw invalid("DUR_ITEM_MISSING", source.getKey());
                if (source.getKey().equals(other) || !sources.containsKey(other)) continue;
                String pair = pairKey(source.getKey(), other);
                if (!ApprovedInteractionPairs.ALL.stream().anyMatch(p -> p.kind() == ApprovedInteractionPairs.EvidenceKind.DIRECT_ITEM
                        && pair.equals(pairKey(p.firstItemSeq(), p.secondItemSeq())))) {
                    throw invalid("UNAPPROVED_DIRECT_PAIR", pair);
                }
                if (!"병용금기".equals(value(row, "TYPE_NAME"))) {
                    throw invalid("DIRECT_EVIDENCE_CONFLICT", pair);
                }
                validateDirectSemantics(pair, row, source.getKey(), other,
                        sources.get(source.getKey()).get(Service.PERMIT_INGREDIENTS).records(),
                        sources.get(other).get(Service.PERMIT_INGREDIENTS).records());
                ObjectNode evidence = mapper.createObjectNode();
                evidence.put("kind", "DIRECT_ITEM");
                evidence.put("queryItemSeq", source.getKey());
                evidence.set("row", select(row, Service.DUR_PRODUCT));
                direct.computeIfAbsent(pair, ignored -> new ArrayList<>()).add(evidence);
            }
        }
        List<ReferenceBatch.InteractionReference> result = new ArrayList<>(4);
        for (ApprovedInteractionPairs.Pair pair : ApprovedInteractionPairs.ALL) {
            String key = pairKey(pair.firstItemSeq(), pair.secondItemSeq());
            List<ObjectNode> evidence;
            if (pair.kind() == ApprovedInteractionPairs.EvidenceKind.DIRECT_ITEM) {
                evidence = direct.get(key);
                if (evidence == null || evidence.isEmpty()) throw invalid("APPROVED_EVIDENCE_MISSING", key);
            } else {
                evidence = ingredientEvidence(pair, sources, ingredientRules);
            }
            evidence.sort(Comparator.comparing(this::json));
            ArrayNode evidenceArray = mapper.createArrayNode();
            evidence.forEach(evidenceArray::add);
            String reason = checkedReason(pair, evidence);
            String evidenceJson = json(evidenceArray);
            String hash = sha256(canonical(evidenceArray));
            result.add(new ReferenceBatch.InteractionReference(pair.firstItemSeq(), pair.secondItemSeq(),
                    evidenceJson, reason, reviewedSummary(pair), hash, hash));
        }
        return result;
    }

    private static void validateDirectSemantics(String pair, JsonNode row, String queryItemSeq,
            String otherItemSeq, List<JsonNode> ownIngredients, List<JsonNode> otherIngredients) {
        String firstDur;
        String secondDur;
        String firstM;
        String secondM;
        switch (pair) {
            case "197500541|200001783" -> {
                firstDur = "D000731"; secondDur = "D000565";
                firstM = "M099796"; secondM = "M040438";
            }
            case "199906953|201401986" -> {
                firstDur = "D000373"; secondDur = "D000342";
                firstM = "M223198"; secondM = "M230909";
            }
            case "199906953|201606824" -> {
                firstDur = "D000373"; secondDur = "D000582";
                firstM = "M223198"; secondM = "M091703";
            }
            default -> throw invalid("UNREVIEWED_PAIR", pair);
        }
        boolean forward = queryItemSeq.compareTo(otherItemSeq) < 0;
        String ownDur = forward ? firstDur : secondDur;
        String otherDur = forward ? secondDur : firstDur;
        String ownM = forward ? firstM : secondM;
        String otherM = forward ? secondM : firstM;
        if (!"A".equals(value(row, "TYPE_CODE"))
                || !ownDur.equals(value(row, "INGR_CODE"))
                || !otherDur.equals(value(row, "MIXTURE_INGR_CODE"))
                || !"단일".equals(value(row, "MIX"))
                || !"단일".equals(value(row, "MIXTURE_MIX"))
                || !blank(value(row, "REMARK"))
                || !ingredientCodes(ownIngredients).contains(ownM)
                || !ingredientCodes(otherIngredients).contains(otherM)
                || !codesIn(value(row, "MAIN_INGR")).contains(ownM)
                || !codesIn(value(row, "MIXTURE_MAIN_INGR")).contains(otherM)) {
            throw invalid("DIRECT_EVIDENCE_CONFLICT", pair);
        }
    }

    private List<ObjectNode> ingredientEvidence(ApprovedInteractionPairs.Pair pair,
            Map<String, Map<Service, MfdsClient.SourceData>> sources,
            MfdsClient.SourceData rules) {
        String left = pair.firstItemSeq();
        String right = pair.secondItemSeq();
        Set<String> leftCodes = ingredientCodes(sources.get(left).get(Service.PERMIT_INGREDIENTS).records());
        Set<String> rightCodes = ingredientCodes(sources.get(right).get(Service.PERMIT_INGREDIENTS).records());
        if (!leftCodes.contains("M203364") || !rightCodes.contains("M257939")) {
            throw invalid("INGREDIENT_MAPPING_CHANGED", pairKey(left, right));
        }
        List<ObjectNode> result = new ArrayList<>();
        for (JsonNode row : rules.records()) {
            boolean forward = "D000448".equals(value(row, "INGR_CODE"))
                    && "D000698".equals(value(row, "MIXTURE_INGR_CODE"));
            boolean reverse = "D000698".equals(value(row, "INGR_CODE"))
                    && "D000448".equals(value(row, "MIXTURE_INGR_CODE"));
            if (!forward && !reverse) continue;
            String leftOri = value(row, forward ? "ORI" : "MIXTURE_ORI");
            String rightOri = value(row, forward ? "MIXTURE_ORI" : "ORI");
            if (!codesIn(leftOri).contains("M203364") || !codesIn(rightOri).contains("M257939")) continue;
            if (!"정상".equals(value(row, "DEL_YN")) || !"병용금기".equals(value(row, "TYPE_NAME"))
                    || !"단일".equals(value(row, "MIX_TYPE"))
                    || !"단일".equals(value(row, "MIXTURE_MIX_TYPE"))
                    || !blank(value(row, "MIX")) || !blank(value(row, "MIXTURE_MIX"))
                    || !blank(value(row, "REMARK"))) {
                throw invalid("INGREDIENT_RULE_CHANGED", pairKey(left, right));
            }
            ObjectNode evidence = mapper.createObjectNode();
            evidence.put("kind", "INGREDIENT_LINK");
            evidence.put("leftItemSeq", left);
            evidence.put("rightItemSeq", right);
            evidence.put("leftMatchedCode", "M203364");
            evidence.put("rightMatchedCode", "M257939");
            evidence.set("rule", select(row, Service.DUR_INGREDIENT));
            result.add(evidence);
        }
        if (result.isEmpty()) throw invalid("APPROVED_EVIDENCE_MISSING", pairKey(left, right));
        return result;
    }

    private String checkedReason(ApprovedInteractionPairs.Pair pair, List<ObjectNode> evidence) {
        String key = pairKey(pair.firstItemSeq(), pair.secondItemSeq());
        String expected = switch (key) {
            case "197500541|200001783" -> "고칼륨혈증";
            case "199906953|201401986" -> "실로도신의 혈중농도 증가";
            case "199906953|201606824" -> "콜키신에 대한 노출 증가 가능";
            case "200811061|201309347" -> "고혈압, 빈맥 유발";
            default -> throw invalid("UNREVIEWED_PAIR", key);
        };
        Set<String> reasons = evidence.stream()
                .map(node -> node.path(pair.kind() == ApprovedInteractionPairs.EvidenceKind.DIRECT_ITEM ? "row" : "rule"))
                .map(node -> value(node, "PROHBT_CONTENT")).filter(s -> s != null)
                .map(String::trim).collect(Collectors.toSet());
        if (!reasons.equals(Set.of(expected))) {
            throw invalid("REVIEW_REQUIRED", key);
        }
        return expected;
    }

    private static String reviewedSummary(ApprovedInteractionPairs.Pair pair) {
        return switch (pairKey(pair.firstItemSeq(), pair.secondItemSeq())) {
            case "197500541|200001783" -> "두 약을 함께 복용하면 고칼륨혈증이 생길 수 있어 병용금기입니다.";
            case "199906953|201401986" -> "두 약을 함께 복용하면 실로도신의 혈중농도가 증가할 수 있어 병용금기입니다.";
            case "199906953|201606824" -> "두 약을 함께 복용하면 콜키신 노출이 증가할 수 있어 병용금기입니다.";
            case "200811061|201309347" -> "두 약의 성분 조합은 고혈압이나 빈맥을 유발할 수 있어 병용금기입니다.";
            default -> throw invalid("UNREVIEWED_PAIR", pair.firstItemSeq());
        };
    }

    private ObjectNode select(JsonNode row, Service service) {
        ObjectNode selected = mapper.createObjectNode();
        for (String field : FIELDS.get(service)) {
            if (row.has(field)) selected.set(field, row.get(field));
        }
        return selected;
    }

    private static String documentText(String xml) {
        if (xml == null) return null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            StringBuilder text = new StringBuilder();
            appendDocumentText(document.getDocumentElement(), text);
            return text.toString().strip();
        } catch (Exception ex) {
            throw invalid("OFFICIAL_DOCUMENT_INVALID", "Selected permit document could not be parsed");
        }
    }

    private static void appendDocumentText(Node node, StringBuilder text) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            String title = node.getAttributes().getNamedItem("title") == null ? null
                    : node.getAttributes().getNamedItem("title").getNodeValue();
            if (title != null && !title.isBlank()) text.append(title.strip()).append('\n');
        }
        if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
            String content = node.getNodeValue();
            // Keep spaces around inline markup; stripping each text node can join separate words.
            if (content != null && !content.isBlank()) text.append(content);
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            appendDocumentText(child, text);
        }
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            String tag = node.getNodeName().toUpperCase(java.util.Locale.ROOT);
            if (Set.of("TD", "TH").contains(tag)) text.append(" | ");
            if (Set.of("PARAGRAPH", "ARTICLE", "TABLE", "TR", "SECTION", "LI").contains(tag)) text.append('\n');
        }
    }

    private static Set<String> ingredientCodes(List<JsonNode> rows) {
        return rows.stream().map(row -> value(row, "MTRAL_CODE")).filter(s -> s != null)
                .collect(Collectors.toSet());
    }

    private static Set<String> codesIn(String ori) {
        Set<String> codes = new HashSet<>();
        if (ori != null) {
            Matcher matcher = INGREDIENT_CODE.matcher(ori);
            while (matcher.find()) codes.add(matcher.group(1));
        }
        return codes;
    }

    private static JsonNode first(MfdsClient.SourceData source) {
        return source.records().isEmpty() ? null : source.records().getFirst();
    }

    private static String value(JsonNode row, String key) {
        if (row == null || !row.hasNonNull(key)) return null;
        String value = row.get(key).asText();
        return value.isBlank() ? null : value;
    }

    private static boolean hasText(JsonNode row, String key) { return value(row, key) != null; }
    private static boolean sameWhenPresent(String source, String reference) {
        return source == null || reference == null || source.strip().equals(reference.strip());
    }
    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private static String coalesce(String... options) {
        for (String option : options) if (option != null && !option.isBlank()) return option;
        return null;
    }

    private static String pairKey(String a, String b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    private static boolean isApprovedDirectPair(String first, String second) {
        if (second == null || first.equals(second)) return false;
        String key = pairKey(first, second);
        return ApprovedInteractionPairs.ALL.stream().anyMatch(p ->
                p.kind() == ApprovedInteractionPairs.EvidenceKind.DIRECT_ITEM
                        && key.equals(pairKey(p.firstItemSeq(), p.secondItemSeq())));
    }

    private String json(JsonNode node) {
        try { return mapper.writeValueAsString(node); }
        catch (JsonProcessingException e) { throw invalid("JSON_SERIALIZATION_FAILED", "Selected source"); }
    }

    private String canonical(JsonNode node) {
        if (node.isObject()) {
            Map<String, String> sorted = new TreeMap<>();
            node.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), canonical(entry.getValue())));
            return sorted.entrySet().stream().map(e -> json(mapper.valueToTree(e.getKey())) + ":" + e.getValue())
                    .collect(Collectors.joining(",", "{", "}"));
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            node.forEach(child -> values.add(canonical(child)));
            values.sort(String::compareTo);
            return String.join(",", values).transform(s -> "[" + s + "]");
        }
        return json(node);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static ReferenceValidationException invalid(String code, String detail) {
        return new ReferenceValidationException(code, detail);
    }
}
