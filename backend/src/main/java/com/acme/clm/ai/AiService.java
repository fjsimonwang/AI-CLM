package com.acme.clm.ai;

import com.acme.clm.common.Json;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * High-level, grounded AI capabilities. Every method:
 *  - embeds a [[capability:X]] marker so the mock stays functional,
 *  - constrains output to JSON and validates it,
 *  - logs an AI_INTERACTION row,
 *  - degrades gracefully (honest error, never a confident guess) when the model is unavailable.
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final String PROMPT_VERSION = "2025-08-01";

    private final LlmClient llm;
    private final AiInteractionLog aiLog;

    public AiService(LlmClient llm, AiInteractionLog aiLog) {
        this.llm = llm;
        this.aiLog = aiLog;
    }

    public boolean modelIsLive() { return llm.isLive(); }
    public String modelId() { return llm.modelId(); }

    // ---------------- Intake conversation ----------------

    public record IntakeTurn(String assistantReply, Map<String, Object> capturedFields,
                             Map<String, Object> fieldConfidence, boolean readyToSubmit,
                             String clarifyingQuestion, List<Map<String, Object>> questions,
                             UUID interactionId) {}

    public IntakeTurn intakeTurn(UUID sessionId, UUID userId, String history, String userMessage,
                                 String fieldGuide, List<String> stillNeeded, Map<String, Object> alreadyCaptured) {
        String sys = """
            [[capability:INTAKE_TURN]]
            You are the contract intake assistant for an enterprise CLM platform.
            Ask questions in plain business language; store normalized legal attributes.
            Infer before asking. Target: a routine NDA in <= 3 questions.

            captured_fields MUST use ONLY these exact keys, with values as described:
            %s

            CRITICAL: If anything the user has said lets you determine a field's value, you MUST
            put it in captured_fields as a real key/value. Do NOT merely describe a value in
            assistant_reply while leaving it out of captured_fields. Extract aggressively.
            - Enum fields: use one of the listed allowed values verbatim.
            - contracting_entity_id: output the entity name/code from the allowed list when the user
              names a location or entity (e.g. "our German entity" -> ACME_GMBH).
            - Booleans: true / false (e.g. "mutual" -> mutual: true; "no personal data" -> data_processing: false).
            - Numbers: plain number (e.g. "18 months" -> term_months: 18; "2 years" -> term_months: 24).
            - Omit only fields you genuinely cannot determine yet.

            Example — user says "mutual NDA with Acme Corp, 2 years, involves customer data":
            captured_fields: {"contract_type_code":"NDA","counterparty_name":"Acme Corp","mutual":true,
                              "term_months":24,"data_processing":true}

            Still missing (required): %s
            Fields already captured: %s

            Ask a short clarifying question ONLY for a still-missing required field. If nothing
            required is missing, set ready_to_submit true and confirm — but make clear the user
            must review every field in the form panel and confirm the contents (especially the
            AI-suggested ones) before moving to the next step.

            For EVERY question you ask in assistant_reply, also return one entry in "questions":
            {"field": the exact field key the question asks about, "quick_options": up to 3
            concrete suggested answers the user could tap (same type the field expects, e.g.
            [12, 36] for a month count, [] for plain free-text like a party name)}.
            Number the questions in assistant_reply as (1) ... (2) ... matching the entries.

            Respond with a single JSON object:
            {"assistant_reply": string, "captured_fields": object, "field_confidence": object (0..1 per key),
             "ready_to_submit": boolean, "clarifying_question": string, "questions": array}
            """.formatted(fieldGuide,
                stillNeeded == null || stillNeeded.isEmpty() ? "(none)" : String.join(", ", stillNeeded),
                Json.write(alreadyCaptured));

        List<LlmClient.Message> msgs = new ArrayList<>();
        msgs.add(LlmClient.Message.system(sys));
        if (history != null && !history.isBlank()) msgs.add(LlmClient.Message.assistant("Conversation so far:\n" + history));
        msgs.add(LlmClient.Message.user(userMessage));

        long t0 = System.currentTimeMillis();
        LlmClient.ChatResult r = llm.chatJson(msgs, false);
        JsonNode j = safeJson(r.text());

        Map<String, Object> fields = j.has("captured_fields") ? Json.convert(j.get("captured_fields"), Map.class) : Map.of();
        Map<String, Object> conf = j.has("field_confidence") ? Json.convert(j.get("field_confidence"), Map.class) : Map.of();
        String reply = j.path("assistant_reply").asText("Could you tell me a bit more about the agreement you need?");
        boolean ready = j.path("ready_to_submit").asBoolean(false);
        String question = j.path("clarifying_question").asText("");
        List<Map<String, Object>> questions = new ArrayList<>();
        if (j.path("questions").isArray()) {
            j.path("questions").forEach(qn -> {
                String qf = qn.path("field").asText("");
                if (qf.isBlank()) return;
                List<Object> qo = new ArrayList<>();
                qn.path("quick_options").forEach(v -> qo.add(Json.mapper().convertValue(v, Object.class)));
                questions.add(Map.of("field", qf, "quickOptions", qo));
            });
        }

        var ai = aiLog.record("INTAKE", "INTAKE_TURN", r.modelId(), "intake_turn", PROMPT_VERSION,
                Map.of("message", userMessage), j, null, null,
                (int) (System.currentTimeMillis() - t0), r.promptTokens() + r.completionTokens(),
                userId, null, sessionId);

        return new IntakeTurn(reply, fields, conf, ready, question, questions, ai.id);
    }

    // ---------------- 3rd-party paper extraction ----------------

    public record PaperExtraction(String assistantReply, Map<String, Object> capturedFields,
                                  Map<String, Object> fieldConfidence, UUID interactionId) {}

    public PaperExtraction extractPaperContract(String paperText, String fieldGuide,
                                                String alreadyCaptured, UUID userId, UUID sessionId) {
        String sys = """
            [[capability:EXTRACT_PAPER]]
            The user has uploaded a third-party contract (their paper). Read it and extract the
            intake fields so the platform can create the contract record.

            captured_fields MUST use ONLY these exact keys, with values as described:
            %s

            Rules:
            - Identify the agreement's type from its title/body and output it as contract_type_code.
            - Extract every field the text actually states (term, governing law, value, payment
              terms, data processing, etc.). Never guess: if a field is not in the paper, omit it.
            - contracting_entity_id and participants describe OUR side; the paper will not name
              them, so omit both.
            - counterparty_name is the party on the paper that is not us (usually the one whose
              name appears most prominently in the signature blocks' "the Company/Supplier/Customer"
              definition) — if both parties look like external companies, pick the second-listed one.
            - Also include requested_by from the captured defaults below if not superseded.
            - title: a short descriptive name if the paper's title is suitable.

            Fields already captured (defaults — keep them unless the paper contradicts them):
            %s

            assistant_reply must be structured and concise, using short markdown-ish sections on
            separate lines:
            **What this is** — one sentence: the agreement type and the parties.
            **Key terms** — a short bulleted list ("- 24-month term", "- governed by England and Wales", …)
            of the terms you extracted.
            **Needs your attention** — one or two bullets on what the user should check/confirm
            (fields the AI inferred, values missing from the paper, or conflicting defaults).
            Keep the whole reply under 120 words. Do not restate pre-filled defaults like the
            contracting entity when the paper says nothing new about them.

            Respond with a single JSON object:
            {"assistant_reply": string, "captured_fields": object, "field_confidence": object (0..1 per key)}
            """.formatted(fieldGuide, alreadyCaptured == null || alreadyCaptured.isBlank()
                ? "(none)" : alreadyCaptured);

        long t0 = System.currentTimeMillis();
        LlmClient.ChatResult r = llm.chatJson(List.of(LlmClient.Message.system(sys),
                LlmClient.Message.user(paperText)), false);
        JsonNode j = safeJson(r.text());
        PaperExtraction out = new PaperExtraction(
                j.path("assistant_reply").asText("I've read the uploaded contract — please review the extracted fields."),
                j.has("captured_fields") ? Json.convert(j.get("captured_fields"), Map.class) : Map.of(),
                j.has("field_confidence") ? Json.convert(j.get("field_confidence"), Map.class) : Map.of(),
                null);
        var ai = aiLog.record("INTAKE", "EXTRACT_PAPER", r.modelId(), "extract_paper", PROMPT_VERSION,
                Map.of("chars", paperText.length()), j, null, null,
                (int) (System.currentTimeMillis() - t0), r.promptTokens() + r.completionTokens(),
                userId, null, sessionId);
        return new PaperExtraction(out.assistantReply, out.capturedFields, out.fieldConfidence, ai.id);
    }

    // ---------------- Classification ----------------

    public Map<String, Object> classify(String text, UUID userId) {
        String sys = """
            [[capability:CLASSIFY]]
            Classify the following contract text. Return JSON:
            {"contract_type_code": string, "language": string, "governing_law": string,
             "counterparty_name": string, "confidence": number}
            """;
        long t0 = System.currentTimeMillis();
        LlmClient.ChatResult r = llm.chatJson(List.of(LlmClient.Message.system(sys), LlmClient.Message.user(text)), false);
        JsonNode j = safeJson(r.text());
        Map<String, Object> out = Json.convert(j, Map.class);
        aiLog.record("MIGRATION", "CLASSIFY", r.modelId(), "classify", PROMPT_VERSION,
                Map.of("chars", text.length()), j, j.path("confidence").asDouble(0), null,
                (int) (System.currentTimeMillis() - t0), r.promptTokens() + r.completionTokens(), userId, null, null);
        return out;
    }

    // ---------------- Metadata extraction ----------------

    public record ExtractedField(Object value, double confidence, String sourceSpan) {}

    public Map<String, ExtractedField> extractTerms(String text, List<String> fields, UUID userId, UUID contractId) {
        String sys = """
            [[capability:EXTRACT]]
            Extract the requested fields from the contract text. For each field return value, a
            confidence between 0 and 1, and the source_span (a short quote you based it on).
            Never guess: if a field is absent, omit it. Return JSON:
            {"fields": {"<name>": {"value": any, "confidence": number, "source_span": string}}}
            Requested fields: %s
            """.formatted(String.join(", ", fields));
        long t0 = System.currentTimeMillis();
        LlmClient.ChatResult r = llm.chatJson(List.of(LlmClient.Message.system(sys), LlmClient.Message.user(text)), false);
        JsonNode j = safeJson(r.text()).path("fields");
        Map<String, ExtractedField> out = new java.util.LinkedHashMap<>();
        j.fields().forEachRemaining(e -> {
            JsonNode v = e.getValue();
            out.put(e.getKey(), new ExtractedField(
                    Json.mapper().convertValue(v.get("value"), Object.class),
                    v.path("confidence").asDouble(0.0),
                    v.path("source_span").asText("")));
        });
        aiLog.record("MIGRATION", "EXTRACT", r.modelId(), "extract", PROMPT_VERSION,
                Map.of("fields", fields), out, null, null,
                (int) (System.currentTimeMillis() - t0), r.promptTokens() + r.completionTokens(), userId, contractId, null);
        return out;
    }

    // ---------------- Summaries / briefings ----------------

    public record StructuredSummary(String summary, List<Map<String, Object>> differences,
                                    List<String> approverAttention) {}

    public StructuredSummary summarize(String contractContext, String precedentContext,
                                       String diffContext, UUID userId, UUID contractId) {
        String sys = """
            [[capability:SUMMARIZE]]
            You are briefing an approver on a contract. You get the contract record, its precedent
            (the contract it was modelled on), and a deterministic field-by-field diff of the two.
            Produce JSON:
            {"summary": "3-5 sentence plain-language summary a non-lawyer understands: who the
                         parties are, what it is for, key dates, key numbers, and status",
             "differences": [{"topic": string, "precedent": string, "current": string,
                              "note": string why this difference matters}]
                            — every real difference between the current contract and its precedent;
                              empty list when there is no precedent or no difference,
             "approver_attention": ["concrete item an approver must check before approving"]}
            Never invent values; use only what the record states. Return a single JSON object.
            """;
        List<LlmClient.Message> msgs = new ArrayList<>();
        msgs.add(LlmClient.Message.system(sys));
        msgs.add(LlmClient.Message.user((contractContext + "\n\n" + precedentContext + "\n\n" + diffContext).trim()));
        long t0 = System.currentTimeMillis();
        LlmClient.ChatResult r = llm.chatJson(msgs, false);
        JsonNode j = safeJson(r.text());
        StructuredSummary s = new StructuredSummary(
                j.path("summary").asText(""),
                toListOfMaps(j.path("differences")),
                toListOfStrings(j.path("approver_attention")));
        aiLog.record("CONTRACT_VIEW", "SUMMARIZE", r.modelId(), "summarize", PROMPT_VERSION,
                Map.of("chars", msgs.get(1).content().length()), j, null, null,
                (int) (System.currentTimeMillis() - t0), r.promptTokens() + r.completionTokens(), userId, contractId, null);
        return s;
    }

    public record RelationCandidate(int index, String label) {}

    public record RelationSuggestion(String relationType, double confidence, List<String> reasons) {}

    public Map<Integer, RelationSuggestion> classifyRelations(String currentDesc, String candidatesText,
                                                              UUID userId, UUID contractId) {
        String sys = """
            [[capability:RELATION_CLASSIFY]]
            You identify relationships between contracts. Given the current contract and a numbered
            list of candidate contracts, decide for each candidate whether a real relationship
            exists and of what type. Allowed relation types:
            AMENDS (candidate is amended by the current contract),
            SUPERSEDES (candidate is replaced by the current contract),
            PRECEDES (candidate was negotiated earlier in the same deal, e.g. an NDA before an MSA),
            MASTER (candidate is a master agreement the current contract sits under, e.g. MSA -> SOW),
            SIMILAR_FAMILY (same parties and period but no direct legal dependency).
            Only assign a type when the evidence (shared parties, entity, dates, titles) supports it;
            otherwise use SIMILAR_FAMILY with low confidence. Return JSON:
            {"relations": [{"index": <candidate index>, "relation_type": "AMENDS|SUPERSEDES|PRECEDES|MASTER|SIMILAR_FAMILY",
                            "confidence": 0.0-1.0, "reasons": [short strings citing parties/dates/titles]}]}
            """;
        List<LlmClient.Message> msgs = new ArrayList<>();
        msgs.add(LlmClient.Message.system(sys));
        msgs.add(LlmClient.Message.user("Current contract:\n" + currentDesc + "\n\nCandidates:\n" + candidatesText));
        long t0 = System.currentTimeMillis();
        LlmClient.ChatResult r = llm.chatJson(msgs, false);
        JsonNode j = safeJson(r.text());
        Map<Integer, RelationSuggestion> out = new LinkedHashMap<>();
        JsonNode arr = j.path("relations");
        if (arr.isArray()) {
            for (JsonNode n : arr) {
                int idx = n.path("index").asInt(-1);
                if (idx < 0) continue;
                String type = n.path("relation_type").asText("SIMILAR_FAMILY").toUpperCase();
                double conf = n.path("confidence").asDouble(0.5);
                out.put(idx, new RelationSuggestion(type, conf, toListOfStrings(n.path("reasons"))));
            }
        }
        aiLog.record("CONTRACT_VIEW", "RELATION_CLASSIFY", r.modelId(), "relation_classify", PROMPT_VERSION,
                Map.of("candidates", out.size()), j, null, null,
                (int) (System.currentTimeMillis() - t0), r.promptTokens() + r.completionTokens(), userId, contractId, null);
        return out;
    }

    public record ReviewResult(String overall, List<Map<String, Object>> findings) {}

    public ReviewResult reviewDocument(String bodyText, String rulesText, String playbookText,
                                       UUID userId, UUID contractId) {
        String sys = """
            [[capability:DOC_REVIEW]]
            You are a contract review assistant. Review the document text below and report findings.
            Check EVERY item on the rule checklist. If playbook positions are supplied, also compare
            the relevant clauses against each tier (PREFERRED|ACCEPTABLE|FALLBACK|UNACCEPTABLE) and
            report when the document deviates from the preferred position or is unacceptable.
            Only report concrete, verifiable findings tied to the document text — never invent issues.
            Return JSON:
            {"overall": "CLEAN" | "ISSUES",
             "findings": [{"severity": "CRITICAL"|"HIGH"|"MEDIUM"|"LOW",
                           "title": short title, "detail": what is wrong and why it matters,
                           "location": short quote from the document, "rule": rule code or null,
                           "playbook": playbook name or null}]}
            Rules:
            %s
            %s
            """.formatted(rulesText,
                playbookText == null || playbookText.isBlank() ? "" : "Playbook positions:\n" + playbookText);
        List<LlmClient.Message> msgs = new ArrayList<>();
        msgs.add(LlmClient.Message.system(sys));
        msgs.add(LlmClient.Message.user(bodyText));
        long t0 = System.currentTimeMillis();
        LlmClient.ChatResult r = llm.chatJson(msgs, false);
        JsonNode j = safeJson(r.text());
        List<Map<String, Object>> findings = toListOfMaps(j.path("findings"));
        String overall = j.path("overall").asText(
                findings.isEmpty() ? "CLEAN" : "ISSUES");
        ReviewResult res = new ReviewResult(overall, findings);
        aiLog.record("CONTRACT_VIEW", "DOC_REVIEW", r.modelId(), "doc_review", PROMPT_VERSION,
                Map.of("chars", bodyText.length()), j, null, null,
                (int) (System.currentTimeMillis() - t0), r.promptTokens() + r.completionTokens(), userId, contractId, null);
        return res;
    }

    private static List<Map<String, Object>> toListOfMaps(JsonNode node) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (node.isArray()) node.forEach(n -> out.add(Json.convert(n, Map.class)));
        return out;
    }

    private static List<String> toListOfStrings(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node.isArray()) node.forEach(n -> out.add(n.asText()));
        return out;
    }

    public record ApproverBriefing(String summary, List<String> changes, List<String> nonStandard, String decision) {}

    public ApproverBriefing approverBriefing(String context, UUID userId, UUID contractId) {
        String sys = """
            [[capability:APPROVER_BRIEF]]
            Produce an approver briefing for the contract below. Cover: what changed (vs the precedent
            or the last version), what deviates from standard playbook positions, and the specific
            decision being requested. The context may include an INTAKE REQUEST HISTORY transcript —
            use it to reflect what the requester actually asked for (their stated purpose, priorities
            and negotiated points) alongside the contract's fields, and flag where the drafted terms
            differ from that request. Keep each item short — the whole briefing under 120 words.
            Only state facts found in the context. Return JSON:
            {"summary": "2-3 sentence overview",
             "changes": [short strings],
             "non_standard": [short strings],
             "decision": "the decision being requested"}
            """;
        long t0 = System.currentTimeMillis();
        LlmClient.ChatResult r = llm.chatJson(List.of(LlmClient.Message.system(sys), LlmClient.Message.user(context)), false);
        JsonNode j = safeJson(r.text());
        ApproverBriefing b = new ApproverBriefing(
                j.path("summary").asText(""),
                toListOfStrings(j.path("changes")),
                toListOfStrings(j.path("non_standard")),
                j.path("decision").asText(""));
        aiLog.record("APPROVAL", "APPROVER_BRIEF", r.modelId(), "approver_brief", PROMPT_VERSION,
                Map.of("chars", context.length()), j, null, null,
                (int) (System.currentTimeMillis() - t0), r.promptTokens() + r.completionTokens(), userId, contractId, null);
        return b;
    }

    // ---------------- Natural language query translation ----------------

    public record NlQuery(String interpreted, Map<String, Object> filters, String metric, String groupBy, boolean answerable) {}

    private static final String NL_QUERY_PROMPT = """
        [[capability:NL_QUERY]]
        Translate the user's question into a structured query over the contract portfolio.
        Available filters (use only those needed):
        - entity_region: EU|EMEA|US|UK (contracting entity's region)
        - entity_name: string substring (e.g. "Acme Ltd", "Acme Inc")
        - contract_type_code: e.g. NDA, MSA, DPA, SOW, EMPLOYMENT, VENDOR_PURCHASE
        - status: DRAFT|IN_REVIEW|ACTIVE|EXPIRED|CLOSED_REJECTED (or similar workflow states)
        - counterparty_name: e.g. "Northwind Traders"
        - governing_law: e.g. "England and Wales", "Delaware"
        - currency: e.g. USD, GBP, EUR
        - min_value / max_value: contract value numbers
        - expiring_within_days: int, expiry date falls in the next N days
        - effective_within_days: int, effective date falls in the next N days
        - created_within_days: int, contract created in the last N days
        - risk_tier: LOW|MEDIUM|HIGH
        - min_risk_score: int 0-100
        - auto_renew: true|false
        - is_amendment: true|false (contract has a parent contract, e.g. amendments/SOWs)
        - keyword: free-text matched against title, summary and contract number
        Additionally support statistics: summaries, calculations, aggregations and group summaries.
        Return JSON: {"interpreted": string (plain English restatement), "filters": object,
        "metric": "count" | "count_and_value" | "list" | "sum_value" | "avg_value" | "min_value" | "max_value"
                   (count = how many; count_and_value = how many + total value; list = show the matching contracts;
                    sum_value = total value; avg_value = average value; min_value/max_value = smallest/largest value),
        "group_by": "status" | "type" | "entity" | "currency" | "risk_tier" | null
                   (only when the question asks for a per-group breakdown, e.g. "by status", "per entity",
                    "grouped by type", "value by currency"; null otherwise),
        "answerable": boolean}.
        Only set filters the question actually implies; leave the rest out.
        Examples: "How many NDAs are active?" -> metric count, filters {contract_type_code: NDA, status: ACTIVE};
        "Total value of EMEA contracts" -> metric sum_value, filters {entity_region: EMEA};
        "NDAs statistics by status" -> metric count_and_value, group_by status, filters {contract_type_code: NDA};
        "Show high-risk contracts by status" -> metric list, group_by status, filters {risk_tier: HIGH};
        "Show EMEA vendor contracts by value" -> metric list.
        If the question cannot be mapped to these filters/metrics at all, set answerable=false and interpret.

        DATE RULES (strict):
        - NEVER approximate or guess day counts. Any relative or calendar expression
          ("before year end", "by Q3", "next month", "in 6 weeks", "by 30 June", "this quarter")
          must be resolved to an exact calendar date, then converted to an exact number of days
          from Today's date — use the DATE CONTEXT below, which gives the pre-computed day counts.
        - "Year end" always means December 31 of the current year; "quarter end"/"end of Qx"
          means the last day of that quarter; "month end" means the last day of the current month.
          For other phrases compute Today + N days/weeks/months and use the resulting exact count.
        - "Up for renewal" and "renewing" mean contracts whose expiry falls in the window —
          use expiring_within_days with the exact computed day count.
        - In `interpreted`, always state the resolved window explicitly, e.g.
          "... expiring on or before 2026-12-31, i.e. within 123 days of today".
        - The DATE CONTEXT pre-computes month/quarter/year end: use those values verbatim,
          do not recompute or round.
        """;

    private static String dateContext() {
        LocalDate today = LocalDate.now();
        LocalDate monthEnd = today.with(TemporalAdjusters.lastDayOfMonth());
        int qEndMonth = ((today.getMonthValue() - 1) / 3) * 3 + 3;
        LocalDate quarterEnd = today.withMonth(qEndMonth).with(TemporalAdjusters.lastDayOfMonth());
        LocalDate yearEnd = today.with(TemporalAdjusters.lastDayOfYear());
        return """
            DATE CONTEXT (authoritative, computed by the system):
            - Today is %s.
            - End of this month: %s (%d days from today).
            - End of this quarter: %s (%d days from today).
            - End of this year (year end): %s (%d days from today).
            """
            .formatted(today, monthEnd, ChronoUnit.DAYS.between(today, monthEnd),
                    quarterEnd, ChronoUnit.DAYS.between(today, quarterEnd),
                    yearEnd, ChronoUnit.DAYS.between(today, yearEnd));
    }

    public NlQuery translateQuery(String question, UUID userId) {
        String sys = NL_QUERY_PROMPT + "\n" + dateContext();
        long t0 = System.currentTimeMillis();
        LlmClient.ChatResult r = llm.chatJson(List.of(LlmClient.Message.system(sys), LlmClient.Message.user(question)), false);
        JsonNode j = safeJson(r.text());
        String groupBy = normalizeGroupBy(j.path("group_by").asText(null));
        NlQuery q = new NlQuery(
                j.path("interpreted").asText("Could not interpret the question."),
                j.has("filters") ? Json.convert(j.get("filters"), Map.class) : Map.of(),
                j.path("metric").asText("count_and_value"),
                groupBy,
                j.path("answerable").asBoolean(false));
        aiLog.record("INQUIRY", "NL_QUERY", r.modelId(), "nl_query", PROMPT_VERSION,
                Map.of("question", question), j, null, null,
                (int) (System.currentTimeMillis() - t0), r.promptTokens() + r.completionTokens(), userId, null, null);
        return q;
    }

    private static String normalizeGroupBy(String gb) {
        if (gb == null) return null;
        return switch (gb.trim().toLowerCase()) {
            case "", "null", "none" -> null;
            case "contract_type_code", "contract_type", "type" -> "type";
            case "risk_tier", "risktier", "risk" -> "riskTier";
            case "status" -> "status";
            case "entity", "entity_name", "contracting_entity" -> "entity";
            case "currency" -> "currency";
            default -> null;
        };
    }

    // ---------------- Deviation analysis ----------------

    public JsonNode analyzeDeviation(String proposedClause, String playbookContext, UUID userId, UUID contractId) {
        String sys = """
            [[capability:DEVIATION]]
            Compare the proposed clause against the playbook positions provided. For each concept it
            touches, identify the closest playbook tier (PREFERRED|ACCEPTABLE|FALLBACK|UNACCEPTABLE),
            a severity, a plain-language explanation of the delta and its risk, and suggested fallback
            language drawn from the playbook. Return JSON:
            {"deviations":[{"clause_concept":string,"closest_tier":string,"severity":string,
              "explanation":string,"suggested_fallback":string}], "overall_risk":string}
            Playbook:
            %s
            """.formatted(playbookContext);
        long t0 = System.currentTimeMillis();
        LlmClient.ChatResult r = llm.chatJson(List.of(LlmClient.Message.system(sys), LlmClient.Message.user(proposedClause)), true);
        JsonNode j = safeJson(r.text());
        aiLog.record("REVIEW", "DEVIATION", r.modelId(), "deviation", PROMPT_VERSION,
                Map.of("chars", proposedClause.length()), j, null, null,
                (int) (System.currentTimeMillis() - t0), r.promptTokens() + r.completionTokens(), userId, contractId, null);
        return j;
    }

    // ---------------- Dashboard insight ----------------

    public record Insight(List<Map<String, Object>> highlights, List<Map<String, Object>> suspicious,
                          List<Map<String, Object>> suggestions) {}

    public Insight insight(String context, UUID userId) {
        String sys = """
            [[capability:INSIGHT]]
            You are the user's work assistant inside an enterprise CLM (contract lifecycle
            management) platform. You are given a trace of THEIR recent work and AI activity:
            their open tasks, their contracts, the risks and obligations on those contracts,
            their audit trail, and the log of AI interactions they have run.
            Like a attentive chief-of-staff, analyze it and produce:

            - highlights: the few things that genuinely matter most right now for THIS user —
              priorities, deadlines, blocked work. severity: HIGH|MEDIUM|LOW.
            - suspicious: anything that looks off or risky in the data — repeated failed or
              reverted AI suggestions, rejected approvals, unusually low confidence extractions,
              stale drafts, overdue obligations, risks that keep being raised. Empty list if
              nothing is suspicious. Never fabricate; cite what in the data triggered it.
            - suggestions: concrete, actionable guidance. Each has 3-6 step items in "steps".
              When the resolution is inside this system, name the exact page or action to use
              (e.g. "Open the contract's Risks tab and close resolved items", "Run AI review on
              the Document tab", "Answer the discussion thread from the Dashboard"). When the
              resolution is outside the system, say so (in_system=false) — e.g. "email the
              counterparty to confirm the revised payment terms".

            Ground EVERY statement in the provided data; never invent events or numbers. Be
            specific (name contracts, numbers, dates). Return JSON:
            {"highlights": [{"title": string, "detail": string, "severity": "HIGH"|"MEDIUM"|"LOW"}],
             "suspicious": [{"title": string, "detail": string}],
             "suggestions": [{"title": string, "detail": string, "steps": [string], "in_system": boolean}]}
            """;
        long t0 = System.currentTimeMillis();
        LlmClient.ChatResult r = llm.chatJson(List.of(LlmClient.Message.system(sys), LlmClient.Message.user(context)), false);
        JsonNode j = safeJson(r.text());
        Insight out = new Insight(
                toListOfMaps(j.path("highlights")),
                toListOfMaps(j.path("suspicious")),
                toListOfMaps(j.path("suggestions")));
        aiLog.record("DASHBOARD", "INSIGHT", r.modelId(), "insight", PROMPT_VERSION,
                Map.of("chars", context.length()), j, null, null,
                (int) (System.currentTimeMillis() - t0), r.promptTokens() + r.completionTokens(), userId, null, null);
        return out;
    }

    // ---------------- In-product help chat ----------------

    /**
     * Scales only for Q&A about USING the platform: navigation, operations, support channels and
     * the contract lifecycle. It never performs edits and says so when asked.
     */
    public record HelpTurn(String reply, UUID interactionId) {}

    public HelpTurn helpChat(String question, List<LlmClient.Message> history, String page, String screenContext, UUID userId) {
        String guide = PageDocs.guideFor(page);
        String screen = screenContext == null || screenContext.isBlank() ? "" : """

            LIVE SCREEN CONTEXT (what the user sees on this exact screen right now — sections,
            visible controls, their roles/permissions). Use it to tailor advice to their situation:
            %s
            """.formatted(screenContext);
        String sys = """
            [[capability:HELP_CHAT]]
            You are the CLM help assistant embedded in this contract lifecycle management platform
            (a floating chat bubble available on every page). Your scope is strictly:

            1. How to USE the system — where features live and how to operate them.
            2. How to NAVIGATE — which page to open for which task.
            3. Who can SUPPORT — which role or team owns what (e.g. platform admins manage users,
               grants and master data under Administration; contract owners are shown on each
               contract record; permission requests go through the Access page).
            4. The CONTRACT LIFECYCLE process — intake request → AI drafting/review → internal
               approvals with playbooks → active/obligations tracking → amendment/renewal → expiry.

            Navigation map of the product:
            - Dashboard ("Work"): your contracts, open tasks, AI suggestions for what needs attention.
            - New request ("work > New request""): conversational intake that captures fields, optionally
              from uploaded third-party paper, then submits for drafting.
            - Contracts: list and detail pages with Document, Risks, Obligations, Relations, Audit tabs.
            - Approvals: tasks for approvers, with briefings comparing the contract to its precedent/playbook.
            - Obligations: things that must happen during an active contract (renewals, notices).
            - Access: request/preview access to entities and decide pending requests.
            - Clause library / Template library (Knowledge section): browse precedented clause variants
              and templates.
            - AI activity: log of AI interactions, their outcomes and reverts.
            - Administration (Configure section, admins only): entities, teams, users, parties, contract
              types, clause concepts/variants, templates, playbooks, signing authority, workflows, access.

            HARD RULES:
            - You are advisory only. You NEVER create, edit, approve, reject or delete anything.
              If the user asks you to make a change, point them to the exact page/action instead.
            - Keep answers short, plain-language and specific. Use short bullets.
            - If a question is outside your scope (contract-specific legal advice, external systems),
              say so and tell them who to contact.

            PAGE GUIDE (compiled from the page's actual implementation — its sections, controls,
            data and AI features). Ground your guidance in it: name the exact visible
            buttons/sections and explain precisely what they do:
            %s
            %s
            HIGHLIGHTS: when your guidance references a visible UI component, emit a highlight
            marker directly AFTER the component mention so the UI can outline it on screen,
            e.g. "Open Approvals [[hl:nav_approvals]] in the Work section.".
            Available keys: nav_dashboard, nav_new_request, nav_contracts, nav_approvals,
            nav_obligations, nav_access, nav_clauses, nav_templates, nav_ai_log, nav_admin,
            theme_selector, sign_out.
            Use markers for the 1-2 most relevant components per reply, only for those components
            (sidebar items and header controls, which exist on every page). Never put markers
            inside a bold/code span, and don't let a marker break a sentence.

            Answer in the same language as the user's question.
            """.formatted(guide, screen);
        List<LlmClient.Message> msgs = new ArrayList<>();
        msgs.add(LlmClient.Message.system(sys));
        msgs.add(LlmClient.Message.user("You are now on page: " + (page == null || page.isBlank() ? "(unknown)" : page)));
        if (history != null) msgs.addAll(history);
        msgs.add(LlmClient.Message.user(question));

        long t0 = System.currentTimeMillis();
        LlmClient.ChatResult r = llm.chat(msgs, false);
        var ai = aiLog.record("HELP", "HELP_CHAT", r.modelId(), "help_chat", PROMPT_VERSION,
                Map.of("page", String.valueOf(page), "question", question), Map.of("reply", r.text()), null, null,
                (int) (System.currentTimeMillis() - t0), r.promptTokens() + r.completionTokens(),
                userId, null, null);
        return new HelpTurn(r.text(), ai.id);
    }

    // ---------------- helpers ----------------

    private JsonNode safeJson(String text) {
        try {
            String t = text.trim();
            int a = t.indexOf('{');
            int b = t.lastIndexOf('}');
            if (a >= 0 && b > a) t = t.substring(a, b + 1);
            return Json.read(t);
        } catch (Exception e) {
            log.warn("AI returned non-JSON, using empty object: {}", e.getMessage());
            return Json.mapper().createObjectNode();
        }
    }

}
