package com.acme.clm.service;

import com.acme.clm.ai.AiService;
import com.acme.clm.common.ApiExceptions;
import com.acme.clm.common.Json;
import com.acme.clm.domain.AutoRejectRule;
import com.acme.clm.domain.AutoRejectSetting;
import com.acme.clm.domain.Contract;
import com.acme.clm.domain.LegalEntity;
import com.acme.clm.repo.Repos;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Approver auto-rejection: rules scoped by contract type / country / contracting entity whose
 * natural-language instructions have been interpreted into executable requirements. Evaluation
 * is fully deterministic (no LLM at runtime) so it can run inside the workflow transaction.
 */
@Service
public class AutoRejectService {

    private static final Set<String> FIELD_WHITELIST = Set.of(
            "value_amount", "annual_value_amount", "payment_terms_days",
            "notice_period_days", "renewal_term_months", "risk_score");

    private final Repos.AutoRejectRules rules;
    private final Repos.AutoRejectSettings settings;
    private final Repos.Contracts contracts;
    private final Repos.LegalEntities entities;
    private final Repos.ContractAttachments attachments;
    private final Repos.Users users;
    private final AiService ai;

    public AutoRejectService(Repos.AutoRejectRules rules, Repos.AutoRejectSettings settings,
                             Repos.Contracts contracts,
                             Repos.LegalEntities entities, Repos.ContractAttachments attachments,
                             Repos.Users users, AiService ai) {
        this.rules = rules;
        this.settings = settings;
        this.contracts = contracts;
        this.entities = entities;
        this.attachments = attachments;
        this.users = users;
        this.ai = ai;
    }

    /** An unmet-requirement evaluation of one rule against one contract. */
    public record Eval(String ruleId, String ruleName, boolean satisfied,
                       List<String> met, List<String> unmet, String summary) {}

    public record Decision(AutoRejectRule rule, Eval eval) {}

    // ----------------------------------------------------- rule CRUD

    public Map<String, Object> toMap(AutoRejectRule r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id);
        m.put("ownerUserId", r.ownerUserId);
        m.put("ownerName", users.findById(r.ownerUserId).map(u -> u.displayName).orElse(null));
        m.put("name", r.name);
        m.put("enabled", r.enabled);
        m.put("scope", Json.readMap(r.scope));
        m.put("instructions", r.instructions);
        m.put("structured", r.structured == null ? null : Json.readMap(r.structured));
        m.put("interpretedAt", r.interpretedAt);
        m.put("firedCount", r.firedCount);
        m.put("createdAt", r.createdAt);
        m.put("updatedAt", r.updatedAt);
        return m;
    }

    public AutoRejectRule owned(UUID ruleId, UUID actor) {
        AutoRejectRule r = rules.findById(ruleId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Rule not found"));
        if (!r.ownerUserId.equals(actor)) throw new ApiExceptions.ForbiddenException("This rule belongs to another approver.");
        return r;
    }

    public List<Map<String, Object>> listFor(UUID actor, boolean seesAll) {
        List<AutoRejectRule> list = seesAll ? rules.findAllByOrderByCreatedAtDesc()
                : rules.findByOwnerUserIdOrderByCreatedAtDesc(actor);
        return list.stream().map(this::toMap).collect(Collectors.toList());
    }

    // ----------------------------------------------------- global trigger timing

    /** The approver's global trigger setting; defaults to IMMEDIATE (reject as soon as a task arrives). */
    public AutoRejectSetting settingFor(UUID owner) {
        return settings.findById(owner).orElseGet(() -> {
            AutoRejectSetting d = new AutoRejectSetting();
            d.ownerUserId = owner;
            return d;
        });
    }

    public AutoRejectSetting updateSetting(UUID owner, String mode, Integer delayHours) {
        AutoRejectSetting s = settingFor(owner);
        if ("DELAYED".equals(mode)) {
            s.mode = AutoRejectSetting.Mode.DELAYED;
            int h = delayHours == null ? s.delayHours : delayHours;
            if (h < 1 || h > 720)
                throw new ApiExceptions.BadRequestException("Delay must be between 1 and 720 hours");
            s.delayHours = h;
        } else if ("IMMEDIATE".equals(mode)) {
            s.mode = AutoRejectSetting.Mode.IMMEDIATE;
            s.delayHours = 0;
        } else {
            throw new ApiExceptions.BadRequestException("Mode must be IMMEDIATE or DELAYED");
        }
        s.updatedAt = Instant.now();
        return settings.save(s);
    }

    // ----------------------------------------------------- interpretation

    /** Scope context fed to the interpreter so it knows what the selects constrain. */
    public String scopeContext(Map<String, Object> scope) {
        List<String> types = strList(scope.get("contractTypes"));
        List<String> countries = strList(scope.get("countries"));
        List<String> entityIds = strList(scope.get("entityIds"));
        String entityDesc = entityIds.isEmpty() ? "(any entity)"
                : entityIds.stream().map(id -> entities.findById(UUID.fromString(id))
                        .map(e -> e.shortName + " (" + e.legalName + ")").orElse(id)).collect(Collectors.joining(", "));
        String countryDesc = countries.isEmpty() ? "(any country)" : String.join(", ", countries);
        String typeDesc = types.isEmpty() ? "(any contract type)" : String.join(", ", types);
        return "Contract types: " + typeDesc + "\nCountries (contracting entity's country): " + countryDesc
                + "\nContracting entities: " + entityDesc;
    }

    /** Runs the LLM on the rule's instructions and stores the structured result. */
    public AutoRejectRule interpret(AutoRejectRule rule) {
        AiService.RejectionRuleSpec spec = ai.interpretRejectionRule(
                rule.instructions, scopeContext(Json.readMap(rule.scope)), rule.ownerUserId);
        if (spec.requirements().isEmpty()) {
            throw new ApiExceptions.BadRequestException(
                    "The model could not map these instructions to executable criteria. Try naming concrete documents or numbers (e.g. quote the exact form name).");
        }
        Map<String, Object> clean = new LinkedHashMap<>();
        clean.put("combinator", spec.combinator());
        clean.put("requirements", spec.requirements());
        clean.put("summary", spec.summary());
        if (spec.notes() != null && !spec.notes().isEmpty()) clean.put("notes", spec.notes());
        rule.structured = Json.write(clean);
        rule.interpretationModel = ai.modelId();
        rule.interpretedAt = Instant.now();
        rule.updatedAt = Instant.now();
        return rules.save(rule);
    }

    /** True when the stored structured form can be executed (has at least one known requirement). */
    public boolean isExecutable(AutoRejectRule rule) {
        Map<String, Object> s = rule.structured == null ? Map.of() : Json.readMap(rule.structured);
        List<?> reqs = s.get("requirements") instanceof List<?> l ? l : List.of();
        return !reqs.isEmpty();
    }

    // ----------------------------------------------------- evaluation

    /** Does the rule's scope (type / country / entity) cover this contract? */
    public boolean scopeMatches(AutoRejectRule rule, Contract c) {
        Map<String, Object> scope = Json.readMap(rule.scope);
        List<String> types = strList(scope.get("contractTypes"));
        if (!types.isEmpty() && !types.contains(c.contractTypeCode)) return false;
        List<String> entityIds = strList(scope.get("entityIds"));
        if (!entityIds.isEmpty() && !entityIds.contains(String.valueOf(c.contractingEntityId))) return false;
        List<String> countries = strList(scope.get("countries"));
        if (!countries.isEmpty()) {
            String cc = entities.findById(c.contractingEntityId).map(e -> e.countryCode).orElse(null);
            if (cc == null || !countries.contains(cc)) return false;
        }
        return true;
    }

    /** Deterministic requirement evaluation — safe to call inside the workflow transaction. */
    public Eval evaluate(AutoRejectRule rule, Contract c) {
        Map<String, Object> s = rule.structured == null ? Map.of() : Json.readMap(rule.structured);
        boolean allOf = "ALL_OF".equals(s.get("combinator"));
        List<Map<String, Object>> reqs = requirements(s);
        List<String> filenames = attachments.findByContractIdOrderByCreatedAtAsc(c.id).stream()
                .map(a -> a.filename == null ? "" : a.filename.toLowerCase())
                .toList();
        List<String> met = new ArrayList<>(), unmet = new ArrayList<>();
        for (Map<String, Object> r : reqs) {
            String label = String.valueOf(r.getOrDefault("label", "requirement"));
            List<String> keywords = strList(r.get("keywords"));
            switch (String.valueOf(r.get("kind"))) {
                case "ATTACHMENT" -> {
                    boolean hit = filenames.stream().anyMatch(f -> keywords.stream().anyMatch(f::contains));
                    (hit ? met : unmet).add(label + " (no attachment named " + keywords + ")");
                }
                case "TEXT" -> {
                    String hay = ("summary".equals(r.get("field"))
                            ? String.valueOf(c.summary == null ? "" : c.summary)
                            : String.valueOf(c.title == null ? "" : c.title)).toLowerCase();
                    boolean hit = keywords.stream().anyMatch(hay::contains);
                    (hit ? met : unmet).add(label);
                }
                case "FIELD" -> {
                    String field = String.valueOf(r.get("field"));
                    // Unknown field: fail open (hit) — it can never be satisfied, and treating it
                    // as unmet would auto-reject every contract the rule's scope matches.
                    boolean hit = !FIELD_WHITELIST.contains(field);
                    if (!hit) {
                        Object actual = contractField(c, field);
                        String op = String.valueOf(r.getOrDefault("op", "exists"));
                        hit = switch (op) {
                            case "exists" -> actual != null;
                            case "eq" -> actual != null && compareEq(actual, toNumber(r.get("value")));
                            case "gte" -> actual != null && cmp(actual, toNumber(r.get("value"))) >= 0;
                            case "lte" -> actual != null && cmp(actual, toNumber(r.get("value"))) <= 0;
                            default -> true; // unknown op: fail open
                        };
                    }
                    (hit ? met : unmet).add(label);
                }
                default -> { /* unknown kind: fail open (counted as met) */ }
            }
        }
        boolean satisfied = allOf ? unmet.isEmpty() : !met.isEmpty();
        if (reqs.isEmpty()) satisfied = true; // nothing executable: never reject
        return new Eval(rule.id.toString(), rule.name, satisfied, met, unmet,
                String.valueOf(s.getOrDefault("summary", rule.instructions)));
    }

    /**
     * Should this contract be auto-rejected now that it reached an APPROVAL task assigned to
     * {@code approverId}? Returns the first matching enabled rule whose unmet requirements
     * trigger rejection.
     */
    public Optional<Decision> rejectionFor(Contract c, String taskType, UUID approverId) {
        if (approverId == null || "REVISION".equals(taskType)) return Optional.empty();
        for (AutoRejectRule rule : rules.findByOwnerUserIdAndEnabledTrue(approverId)) {
            if (!isExecutable(rule) || !scopeMatches(rule, c)) continue;
            Eval ev = evaluate(rule, c);
            if (!ev.satisfied()) return Optional.of(new Decision(rule, ev));
        }
        return Optional.empty();
    }

    public Decision applyFired(Decision d) {
        d.rule().firedCount++;
        d.rule().updatedAt = Instant.now();
        rules.save(d.rule());
        return d;
    }

    public List<Map<String, Object>> entityCountryOptions() {
        return entities.findAll().stream()
                .sorted(Comparator.comparing(e -> e.shortName == null ? "" : e.shortName))
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", e.id);
                    m.put("shortName", e.shortName);
                    m.put("legalName", e.legalName);
                    m.put("countryCode", e.countryCode);
                    return m;
                }).toList();
    }

    public Set<String> knownCountries() {
        return entities.findAll().stream().map(e -> e.countryCode)
                .filter(Objects::nonNull).collect(Collectors.toCollection(TreeSet::new));
    }

    // ----------------------------------------------------- helpers

    @SuppressWarnings("unchecked")
    public static List<String> strList(Object v) {
        if (!(v instanceof List<?> l)) return List.of();
        return l.stream().map(String::valueOf).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> requirements(Map<String, Object> structured) {
        if (!(structured.get("requirements") instanceof List<?> l)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : l) if (o instanceof Map<?,?> m) out.add((Map<String, Object>) m);
        return out;
    }

    private static Object contractField(Contract c, String field) {
        return switch (field) {
            case "value_amount" -> c.valueAmount;
            case "annual_value_amount" -> c.annualValueAmount;
            case "payment_terms_days" -> c.paymentTermsDays;
            case "notice_period_days" -> c.noticePeriodDays;
            case "renewal_term_months" -> c.renewalTermMonths;
            case "risk_score" -> c.riskScore;
            default -> null;
        };
    }

    private static BigDecimal toNumber(Object v) {
        if (v == null) return null;
        try { return new BigDecimal(String.valueOf(v)); } catch (NumberFormatException e) { return null; }
    }

    // Null-safe against a missing/malformed comparison value: fail open (treat as met) rather
    // than throwing inside the workflow transaction.
    private static int cmp(Object actual, BigDecimal expected) {
        if (expected == null) return 0;
        try { return new BigDecimal(String.valueOf(actual)).compareTo(expected); }
        catch (NumberFormatException e) { return -1; }
    }

    private static boolean compareEq(Object actual, BigDecimal expected) {
        return toNumber(actual) != null && expected != null && toNumber(actual).compareTo(expected) == 0;
    }
}