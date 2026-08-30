package com.acme.clm.service;

import com.acme.clm.common.Json;
import com.acme.clm.domain.ContractTypeDefinition;
import com.acme.clm.domain.LegalEntity;
import com.acme.clm.repo.Repos;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * The canonical intake field schema per contract type, plus deterministic normalization of
 * AI-inferred values (plan §6A.4: "re-validated by deterministic parsers after AI extraction",
 * "below-threshold values are never silently applied"). The AI proposes; this maps every value
 * to an allowed one and flags anything that needs a human to confirm.
 */
@Component
public class FieldCatalog {

    private final Repos.ContractTypes types;
    private final Repos.LegalEntities entities;
    private final Repos.Users users;

    public FieldCatalog(Repos.ContractTypes types, Repos.LegalEntities entities, Repos.Users users) {
        this.types = types;
        this.entities = entities;
        this.users = users;
    }

    public record Option(String value, String label) {}
    public record Field(String key, String label, String type, boolean required,
                        List<Option> options, String help, String group, boolean money) {
        public Field(String key, String label, String type, boolean required, List<Option> options, String help) {
            this(key, label, type, required, options, help, null, false);
        }
    }

    /** AI / free-text field names that map onto a canonical key. */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("contract_type", "contract_type_code"),
            Map.entry("type", "contract_type_code"),
            Map.entry("contract_type_name", "contract_type_code"),
            Map.entry("agreement_type", "contract_type_code"),
            Map.entry("counterparty", "counterparty_name"),
            Map.entry("other_party", "counterparty_name"),
            Map.entry("party_name", "counterparty_name"),
            Map.entry("vendor_name", "counterparty_name"),
            Map.entry("customer_name", "counterparty_name"),
            Map.entry("entity", "contracting_entity_id"),
            Map.entry("contracting_entity", "contracting_entity_id"),
            Map.entry("legal_entity", "contracting_entity_id"),
            Map.entry("our_entity", "contracting_entity_id"),
            Map.entry("mutuality", "mutual"),
            Map.entry("is_mutual", "mutual"),
            Map.entry("mutual_nda", "mutual"),
            Map.entry("includes_personal_data", "data_processing"),
            Map.entry("personal_data", "data_processing"),
            Map.entry("gdpr", "data_processing"),
            Map.entry("data_processing_involved", "data_processing"),
            Map.entry("duration_months", "term_months"),
            Map.entry("term", "term_months"),
            Map.entry("term_length", "term_months"),
            Map.entry("length_months", "term_months"),
            Map.entry("payment_terms", "payment_terms_days"),
            Map.entry("net_terms", "payment_terms_days"),
            Map.entry("payment_days", "payment_terms_days"),
            Map.entry("governing_law_code", "governing_law"),
            Map.entry("law", "governing_law"),
            Map.entry("jurisdiction", "governing_law"),
            Map.entry("purpose_of_disclosure", "purpose"),
            Map.entry("annual_contract_value", "annual_value"),
            Map.entry("value", "annual_value"));

    private static final Map<String, List<String>> REQUIRED_EXTRA = Map.of(
            "NDA", List.of("mutual", "term_months"),
            "MSA", List.of("term_months", "payment_terms_days"),
            "SOW", List.of("deliverables", "start_date"),
            "VENDOR_PURCHASE", List.of("annual_value"),
            "EMPLOYMENT", List.of("role_title", "start_date"),
            "DPA", List.of());

    private static final Set<String> BOOLEAN_KEYS = Set.of("mutual", "data_processing", "auto_renew");
    private static final Set<String> NUMBER_KEYS = Set.of("term_months", "payment_terms_days",
            "notice_period_days", "annual_value", "value_amount", "renewal_term_months");

    // ---------------------------------------------------------------- spec

    public List<Field> forType(String typeCode) {
        List<Field> out = new ArrayList<>();
        out.add(new Field("contract_type_code", "Contract type", "enum", true,
                types.findByIsActiveTrue().stream()
                        .map(t -> new Option(t.code, t.displayName)).toList(),
                "What kind of agreement is this?"));
        out.add(new Field("contracting_entity_id", "Our contracting entity", "enum", true,
                entities.findAll().stream()
                        .map(e -> new Option(e.id.toString(), e.legalName + " (" + e.shortName + ")")).toList(),
                "Which of our legal entities is signing?"));
        out.add(new Field("counterparty_name", "Counterparty", "text", true, null,
                "The other party to the agreement."));
        out.add(new Field("title", "Title", "text", false, null, "Optional — a short name for this contract."));
        out.add(new Field("governing_law", "Governing law", "text", false, null,
                "Defaults from the contracting entity."));
        out.add(new Field("participants", "Give access to", "users", false,
                users.findAll().stream()
                        .sorted(Comparator.comparing(u -> u.displayName))
                        .map(u -> new Option(u.id.toString(), u.displayName)).toList(),
                "Colleagues who should be able to view this contract (besides you and the assigned lawyer)."));

        ContractTypeDefinition def = typeCode == null ? null : types.findById(typeCode).orElse(null);
        if (def != null) {
            JsonNode schema = Json.read(def.fieldSchema).path("properties");
            List<String> schemaRequired = new ArrayList<>();
            Json.read(def.fieldSchema).path("required").forEach(n -> schemaRequired.add(n.asText()));
            List<String> required = schemaRequired.isEmpty()
                    ? REQUIRED_EXTRA.getOrDefault(typeCode, List.of()) : schemaRequired;

            schema.fields().forEachRemaining(e -> {
                String key = e.getKey();
                JsonNode f = e.getValue();
                String jsonType = f.path("type").asText("string");
                String fmt = f.path("format").asText("");
                boolean multiline = f.path("x-multiline").asBoolean(false);
                boolean money = f.path("x-money").asBoolean(false);
                String group = f.path("x-group").asText(null);
                List<Option> options = null;
                String uiType;
                if (f.has("enum")) {
                    uiType = "enum";
                    options = new ArrayList<>();
                    for (JsonNode v : f.get("enum")) options.add(new Option(v.asText(), prettify(v.asText())));
                } else uiType = switch (jsonType) {
                    case "boolean" -> "boolean";
                    case "integer", "number" -> "number";
                    default -> "date".equals(fmt) ? "date" : (multiline ? "textarea" : "text");
                };
                out.add(new Field(key, f.path("title").asText(prettify(key)), uiType,
                        required.contains(key), options, null, group, money));
            });
        }
        return out;
    }

    // ---------------------------------------------------------------- normalization

    public record Normalized(Map<String, Object> values, Map<String, String> provenance,
                             Set<String> needsConfirmation) {}

    /**
     * Merge raw (AI or user) values into the current captured map, remapping keys/values to the
     * canonical schema. {@code fromUser} values are trusted; AI values that were fuzzy-mapped or
     * left unresolved are added to {@code needsConfirmation}.
     */
    public Normalized normalize(String typeCode, Map<String, Object> current, Map<String, String> provenance,
                                Map<String, Object> raw, boolean fromUser) {
        Map<String, Object> values = new LinkedHashMap<>(current);
        Map<String, String> prov = new LinkedHashMap<>(provenance);
        Set<String> needs = new LinkedHashSet<>();

        // carry existing unconfirmed markers forward
        prov.forEach((k, v) -> { if (v != null && v.endsWith("_UNCONFIRMED")) needs.add(k); });

        // resolve contract type first so type-specific enums are known
        List<Field> spec = forType(effectiveType(typeCode, raw, values));
        Map<String, Field> byKey = new HashMap<>();
        spec.forEach(f -> byKey.put(f.key(), f));

        Set<String> touchedThisCall = new HashSet<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            if (e.getValue() == null) continue;
            String key = ALIASES.getOrDefault(e.getKey(), e.getKey());
            Field field = byKey.get(key);
            if (field == null && !key.equals("contract_type_code")) continue; // ignore keys not in the schema

            Object rawVal = e.getValue();
            MapResult r = coerce(key, field, rawVal);
            if (r == null) continue;

            touchedThisCall.add(key);
            values.put(key, r.value);
            if (r.fuzzy || r.unresolved) {
                // even a user edit that we could not parse (e.g. "three years" in a number field) needs a fix
                prov.put(key, (fromUser ? "USER_INPUT" : "AI_INFERRED") + "_UNCONFIRMED");
                needs.add(key);
            } else if (fromUser) {
                prov.put(key, "USER_INPUT");
                needs.remove(key);
            } else {
                prov.putIfAbsent(key, "AI_INFERRED");
            }
        }

        // resolve entity display + refresh the still-inferred governing law from the chosen entity
        Object entityId = values.get("contracting_entity_id");
        if (entityId != null) {
            UUID eid = safeUuid(String.valueOf(entityId));
            // a same-value echo of the existing law does not count as the user/model choosing it
            boolean lawChosenThisCall = touchedThisCall.contains("governing_law")
                    && !Objects.equals(String.valueOf(values.get("governing_law")),
                                       String.valueOf(current.get("governing_law")));
            if (eid != null) entities.findById(eid).ifPresent(le -> {
                values.put("contracting_entity", le.shortName);
                String lawProv = prov.get("governing_law");
                boolean lawIsInferred = lawProv == null || lawProv.startsWith("INFERRED") || lawProv.startsWith("PREFILLED")
                        || lawProv.startsWith("AI_INFERRED");
                if (le.defaultGoverningLaw != null && lawIsInferred && !lawChosenThisCall) {
                    values.put("governing_law", le.defaultGoverningLaw);
                    prov.put("governing_law", "INFERRED_FROM_CONTEXT");
                }
            });
        }
        return new Normalized(values, prov, needs);
    }

    /** Mark a set of fields as confirmed by the user. */
    public Map<String, String> confirm(Map<String, String> provenance, Collection<String> keys) {
        Map<String, String> p = new LinkedHashMap<>(provenance);
        for (String k : keys) p.put(k, "USER_CONFIRMED");
        return p;
    }

    public Set<String> needsConfirmation(Map<String, String> provenance) {
        Set<String> s = new LinkedHashSet<>();
        provenance.forEach((k, v) -> { if (v != null && v.endsWith("_UNCONFIRMED")) s.add(k); });
        return s;
    }

    public boolean isReady(String typeCode, Map<String, Object> values, Map<String, String> provenance) {
        for (Field f : forType(typeCode)) {
            if (!f.required()) continue;
            Object v = values.get(f.key());
            if (v == null || String.valueOf(v).isBlank()) return false;
        }
        return needsConfirmation(provenance).isEmpty();
    }

    // ---------------------------------------------------------------- coercion

    private static class MapResult {
        Object value; boolean fuzzy; boolean unresolved;
        MapResult(Object v) { this.value = v; }
    }

    private MapResult coerce(String key, Field field, Object raw) {
        if (field != null && "users".equals(field.type())) {
            List<String> tokens = new ArrayList<>();
            if (raw instanceof List<?> l) l.forEach(x -> tokens.add(String.valueOf(x).trim()));
            else for (String part : String.valueOf(raw).split("[,;]")) if (!part.isBlank()) tokens.add(part.trim());
            List<String> ids = new ArrayList<>();
            boolean anyFuzzy = false;
            for (String t : tokens) {
                if (t.isEmpty()) continue;
                var u = users.findAll().stream().filter(x ->
                        x.id.toString().equalsIgnoreCase(t) || x.email.equalsIgnoreCase(t)
                        || x.displayName.equalsIgnoreCase(t)).findFirst().orElse(null);
                if (u == null) {
                    String n = t.toLowerCase();
                    u = users.findAll().stream().filter(x ->
                            x.displayName.toLowerCase().contains(n) || x.email.toLowerCase().startsWith(n)).findFirst().orElse(null);
                    if (u != null) anyFuzzy = true;
                }
                if (u != null && !ids.contains(u.id.toString())) ids.add(u.id.toString());
            }
            if (ids.isEmpty()) return null;
            MapResult r = new MapResult(ids);
            r.fuzzy = anyFuzzy;
            return r;
        }

        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) return null;

        if (key.equals("contract_type_code")) {
            var match = closestType(s);
            MapResult r = new MapResult(match.value);
            r.fuzzy = match.fuzzy;
            r.unresolved = match.value == null;
            if (match.value == null) r.value = s.toUpperCase();
            return r;
        }
        if (key.equals("contracting_entity_id")) {
            var m = closestEntity(s);
            MapResult r = new MapResult(m.value);
            r.fuzzy = m.fuzzy;
            r.unresolved = m.value == null;
            if (m.value == null) return null; // don't store an unresolved entity id
            return r;
        }
        if (field != null && "enum".equals(field.type()) && field.options() != null) {
            for (Option o : field.options()) if (o.value().equalsIgnoreCase(s)) return new MapResult(o.value());
            String n = s.toLowerCase().replace('-', ' ').replace('_', ' ').trim();
            Set<String> nTokens = new HashSet<>(Arrays.asList(n.split("\\s+")));
            for (Option o : field.options()) {
                String ov = o.value().toLowerCase().replace('_', ' ');
                Set<String> oTokens = new HashSet<>(Arrays.asList(ov.split("\\s+")));
                boolean tokenMatch = !oTokens.isEmpty() && nTokens.containsAll(oTokens) && oTokens.containsAll(nTokens);
                if (n.equals(ov) || n.contains(ov) || ov.contains(n) || o.label().toLowerCase().contains(n) || tokenMatch) {
                    MapResult r = new MapResult(o.value());
                    r.fuzzy = true;
                    return r;
                }
            }
            MapResult r = new MapResult(s.toUpperCase().replace(' ', '_'));
            r.unresolved = true;
            return r;
        }
        if (BOOLEAN_KEYS.contains(key) || (field != null && "boolean".equals(field.type()))) {
            Boolean b = toBool(s);
            if (b == null) { MapResult r = new MapResult(s); r.unresolved = true; return r; }
            return new MapResult(b);
        }
        if (NUMBER_KEYS.contains(key) || (field != null && "number".equals(field.type()))) {
            Double d = firstNumber(s);
            if (d == null) { MapResult r = new MapResult(s); r.unresolved = true; return r; }
            return new MapResult(d == Math.rint(d) ? (Object) (long) (double) d : d);
        }
        return new MapResult(s);
    }

    private static class Choice { String value; boolean fuzzy; Choice(String v, boolean f) { value = v; fuzzy = f; } }

    private Choice closestType(String raw) {
        String n = raw.toLowerCase().replaceAll("[^a-z ]", " ").trim();
        List<ContractTypeDefinition> all = types.findByIsActiveTrue();
        for (var t : all) if (t.code.equalsIgnoreCase(raw)) return new Choice(t.code, false);
        for (var t : all) if (t.displayName.equalsIgnoreCase(raw)) return new Choice(t.code, false);
        // common phrasings
        if (n.contains("non disclosure") || n.contains("nondisclosure") || n.contains("nda") || n.contains("confidential"))
            return new Choice(has(all, "NDA"), true);
        if (n.contains("master service") || n.contains("msa")) return new Choice(has(all, "MSA"), true);
        if (n.contains("statement of work") || n.contains("sow")) return new Choice(has(all, "SOW"), true);
        if (n.contains("data processing") || n.contains("dpa")) return new Choice(has(all, "DPA"), true);
        if (n.contains("employ") || n.contains("offer letter")) return new Choice(has(all, "EMPLOYMENT"), true);
        if (n.contains("purchas") || n.contains("supply") || n.contains("vendor")) return new Choice(has(all, "VENDOR_PURCHASE"), true);
        for (var t : all) if (n.contains(t.displayName.toLowerCase())) return new Choice(t.code, true);
        return new Choice(null, false);
    }

    private static String has(List<ContractTypeDefinition> all, String code) {
        return all.stream().anyMatch(t -> t.code.equals(code)) ? code : null;
    }

    private Choice closestEntity(String raw) {
        UUID asId = safeUuid(raw);
        List<LegalEntity> all = entities.findAll();
        if (asId != null) {
            for (var e : all) if (e.id.equals(asId)) return new Choice(e.id.toString(), false);
        }
        for (var e : all) if (e.shortName.equalsIgnoreCase(raw) || e.legalName.equalsIgnoreCase(raw))
            return new Choice(e.id.toString(), false);
        String n = raw.toLowerCase();
        for (var e : all) if (n.contains(e.shortName.toLowerCase()) || e.legalName.toLowerCase().contains(n)
                || n.contains(e.legalName.toLowerCase()))
            return new Choice(e.id.toString(), true);
        return new Choice(null, false);
    }

    // ---------------------------------------------------------------- small helpers

    private String effectiveType(String sessionType, Map<String, Object> raw, Map<String, Object> current) {
        for (String k : List.of("contract_type_code", "contract_type", "type", "agreement_type")) {
            Object v = raw.get(k);
            if (v != null) { Choice c = closestType(String.valueOf(v)); if (c.value != null) return c.value; }
        }
        if (current.get("contract_type_code") != null) return String.valueOf(current.get("contract_type_code"));
        return sessionType;
    }

    private static Boolean toBool(String s) {
        String n = s.toLowerCase().trim();
        if (Set.of("true", "yes", "y", "1", "mutual", "bilateral", "both").contains(n)) return true;
        if (Set.of("false", "no", "n", "0", "one-way", "one way", "unilateral", "1-way").contains(n)) return false;
        if (n.contains("mutual") || n.contains("bilateral")) return true;
        if (n.contains("one-way") || n.contains("one way") || n.contains("unilateral")) return false;
        return null;
    }

    private static Double firstNumber(String s) {
        var m = java.util.regex.Pattern.compile("-?\\d+(?:[.,]\\d+)?").matcher(s.replace(",", ""));
        if (m.find()) { try { return Double.parseDouble(m.group()); } catch (Exception e) { return null; } }
        return null;
    }

    private static UUID safeUuid(String s) {
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }

    private static String prettify(String key) {
        if (key == null || key.isEmpty()) return key;
        String t = key.replace('_', ' ').replace('-', ' ');
        if (t.equals(t.toUpperCase())) t = t.toLowerCase();       // ENUM_VALUE -> enum value
        t = t.trim();
        return t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }
}
