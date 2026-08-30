package com.acme.clm.ai;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic offline stand-in used when no LLM is configured. It keys off a
 * {@code [[capability:X]]} marker that {@link AiService} embeds in the system prompt,
 * so every AI-first surface stays functional without network access.
 */
public class MockLlmClient implements LlmClient {

    private static final Pattern CAP = Pattern.compile("\\[\\[capability:([A-Z_]+)]]");

    @Override public boolean isLive() { return false; }
    @Override public String modelId() { return "mock-deterministic-v1"; }

    @Override
    public ChatResult chat(List<Message> messages, boolean reasoning) {
        return new ChatResult(text(messages, false), 0, 0, modelId());
    }

    @Override
    public ChatResult chatJson(List<Message> messages, boolean reasoning) {
        return new ChatResult(text(messages, true), 0, 0, modelId());
    }

    @Override
    public float[] embed(String text) { return new float[0]; }

    private String capability(List<Message> messages) {
        for (Message m : messages) {
            Matcher mt = CAP.matcher(m.content());
            if (mt.find()) return mt.group(1);
        }
        return "GENERIC";
    }

    private String lastUser(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).role())) return messages.get(i).content();
        }
        return "";
    }

    private String text(List<Message> messages, boolean json) {
        String cap = capability(messages);
        String user = lastUser(messages).toLowerCase();
        return switch (cap) {
            case "INTAKE_TURN" -> intakeTurn(user);
            case "CLASSIFY" -> classify(user);
            case "EXTRACT" -> extract(user);
            case "EXTRACT_PAPER" -> paperExtraction(user);
            case "SUMMARIZE" -> "\"" + esc("This agreement covers the core commercial terms between the parties. "
                    + "Key dates, values and the responsible owner are set out in the structured record. "
                    + "No unusual deviations were detected in this summary pass.") + "\"";
            case "NL_QUERY" -> nlQuery(user);
            case "DEVIATION" -> deviation();
            case "APPROVER_BRIEF" -> "{\"summary\":\"(mock) Mutual NDA on standard terms against the negotiated precedent\","
                + "\"changes\":[\"Term shortened from 36 to 24 months\"],"
                + "\"non_standard\":[],"
                + "\"decision\":\"Approve so the contract can proceed to signature\"}";
            case "RELATION_CLASSIFY" -> relationClassify(user);
            default -> json ? "{\"answer\":\"(mock) No live model configured.\"}"
                            : "I don't have a live model configured, but the deterministic path is working.";
        };
    }

    private String intakeTurn(String user) {
        String type = user.contains("nda") || user.contains("non-disclosure") || user.contains("nondisclosure")
                    || user.contains("confidentiality agreement") ? "NDA"
                : user.contains("msa") || user.contains("master services") || user.contains("services agreement") ? "MSA"
                : user.contains("employment") || user.contains("offer letter") ? "EMPLOYMENT"
                : user.contains("data processing") || user.contains("dpa") ? "DPA"
                : user.contains("sow") || user.contains("statement of work") ? "SOW"
                : user.contains("purchase") || user.contains("vendor agreement") || user.contains("supply") ? "VENDOR_PURCHASE"
                : "NDA";
        boolean mutual = !user.contains("one-way") && !user.contains("one way");
        String counterparty = guessCounterparty(user);
        Integer term = firstInt(user);
        StringBuilder fields = new StringBuilder("{");
        fields.append("\"contract_type_code\":\"").append(type).append("\"");
        if (counterparty != null) fields.append(",\"counterparty_name\":\"").append(esc(counterparty)).append("\"");
        if ("NDA".equals(type)) fields.append(",\"mutual\":").append(mutual);
        if (term != null) fields.append(",\"term_months\":").append(term);
        fields.append("}");

        String question = counterparty == null
                ? "Who is the counterparty for this " + type + "?"
                : (term == null && ("NDA".equals(type) || "MSA".equals(type))
                    ? "What term length do you need (in months)?"
                    : "Does this involve sharing any personal data?");
        String reply = "Got it — this looks like a" + ("AEIOU".indexOf(Character.toUpperCase(type.charAt(0))) >= 0 ? "n " : " ")
                + type + (counterparty != null ? " with " + counterparty : "") + ". " + question;
        return "{"
                + "\"assistant_reply\":\"" + esc(reply) + "\","
                + "\"captured_fields\":" + fields + ","
                + "\"field_confidence\":{\"contract_type_code\":0.8" + (counterparty != null ? ",\"counterparty_name\":0.7" : "") + "},"
                + "\"ready_to_submit\":" + (counterparty != null && term != null) + ","
                + "\"clarifying_question\":\"" + esc(question) + "\""
                + "}";
    }

    private String paperExtraction(String paper) {
        String type = paper.contains("non-disclosure") || paper.contains("nondisclosure")
                    || paper.contains("confidentiality agreement") ? "NDA"
                : paper.contains("master services") || paper.contains("services agreement") ? "MSA"
                : paper.contains("data processing") || paper.contains("dpa") ? "DPA"
                : paper.contains("statement of work") || paper.contains("sow") ? "SOW"
                : paper.contains("employment") || paper.contains("offer letter") ? "EMPLOYMENT"
                : paper.contains("purchase") || paper.contains("supply") || paper.contains("vendor") ? "VENDOR_PURCHASE"
                : "NDA";
        boolean mutual = paper.contains("mutual");
        String counterparty = guessCounterparty(paper);
        Integer term = firstInt(paper);
        StringBuilder fields = new StringBuilder("{");
        fields.append("\"contract_type_code\":\"").append(type).append("\"");
        if (counterparty != null) fields.append(",\"counterparty_name\":\"").append(esc(counterparty)).append("\"");
        if ("NDA".equals(type)) fields.append(",\"mutual\":").append(mutual);
        if (term != null) fields.append(",\"term_months\":").append(term);
        String law = paper.contains("germany") || paper.contains("german") ? "Germany"
                : paper.contains("delaware") ? "Delaware" : "England and Wales";
        fields.append(",\"governing_law\":\"").append(esc(law)).append("\"");
        fields.append("}");
        String cp = counterparty == null ? "the counterparty" : counterparty;
        String reply = "**What this is**\n"
            + "- A" + ("AEIOU".indexOf(Character.toUpperCase(type.charAt(0))) >= 0 ? "n " : " ") + type + " between " + cp + " and us.\n"
            + "\n**Key terms**\n"
            + (term != null ? "- " + term + "-month term\n" : "")
            + "- Governed by " + law + ".\n"
            + "\n**Needs your attention**\n"
            + "- Confirm the extracted values (highlighted below) and fill anything the paper omits, e.g. our contracting entity.";
        return "{"
                + "\"assistant_reply\":\"" + esc(reply) + "\","
                + "\"captured_fields\":" + fields + ","
                + "\"field_confidence\":{\"contract_type_code\":0.75,\"counterparty_name\":0.65,\"governing_law\":0.6}"
                + "}";
    }

    private String classify(String user) {
        String type = user.contains("services") ? "MSA" : user.contains("employment") ? "EMPLOYMENT" : "NDA";
        return "{\"contract_type_code\":\"" + type + "\",\"language\":\"en\",\"governing_law\":\"England and Wales\","
                + "\"counterparty_name\":\"" + esc(orDefault(guessCounterparty(user), "Unknown Counterparty")) + "\","
                + "\"confidence\":0.62}";
    }

    private String extract(String user) {
        Integer term = firstInt(user);
        return "{\"fields\":{"
                + "\"term_months\":{\"value\":" + (term == null ? 24 : term) + ",\"confidence\":0.71,\"source_span\":\"(mock) term clause\"},"
                + "\"governing_law\":{\"value\":\"England and Wales\",\"confidence\":0.6,\"source_span\":\"(mock) governing law clause\"},"
                + "\"auto_renew\":{\"value\":false,\"confidence\":0.55,\"source_span\":\"(mock) term clause\"}"
                + "}}";
    }

    private String nlQuery(String user) {
        String status = user.contains("expir") || user.contains("renew") ? "expiring" : "all";
        String entityRegion = user.contains("emea") || user.contains("europe") ? "EU"
                : user.contains("us") || user.contains("america") ? "US" : null;
        String type = user.contains("vendor") || user.contains("supplier") ? "VENDOR_PURCHASE"
                : user.contains("nda") ? "NDA" : user.contains("msa") ? "MSA" : null;
        StringBuilder f = new StringBuilder("{");
        boolean first = true;
        if (entityRegion != null) { f.append("\"entity_region\":\"").append(entityRegion).append("\""); first = false; }
        if (type != null) { if (!first) f.append(","); f.append("\"contract_type_code\":\"").append(type).append("\""); first = false; }
        if (user.contains("high risk")) { if (!first) f.append(","); f.append("\"risk_tier\":\"HIGH\""); first = false; }
        if (user.contains("low risk")) { if (!first) f.append(","); f.append("\"risk_tier\":\"LOW\""); first = false; }
        if (user.contains("auto-renew") || user.contains("auto renew") || user.contains("automatically renew"))
            { if (!first) f.append(","); f.append("\"auto_renew\":true"); first = false; }
        if (user.contains("amendment") || user.contains("amendments")) { if (!first) f.append(","); f.append("\"is_amendment\":true"); first = false; }
        if ("expiring".equals(status)) { if (!first) f.append(","); f.append("\"expiring_within_days\":90"); }
        f.append("}");
        String human = "Contracts" + (type != null ? " of type " + type : "")
                + (entityRegion != null ? " in " + entityRegion : "")
                + (user.contains("high risk") ? " with HIGH risk tier" : "")
                + (user.contains("amendment") ? " that are amendments" : "")
                + ("expiring".equals(status) ? " expiring within 90 days" : "")
                + ", ordered by value.";
        return "{\"interpreted\":\"" + esc(human) + "\",\"filters\":" + f + ",\"metric\":\"count_and_value\","
                + "\"answerable\":true}";
    }

    private String deviation() {
        return "{\"deviations\":[{\"clause_concept\":\"LIMITATION_OF_LIABILITY\",\"closest_tier\":\"FALLBACK\","
                + "\"severity\":\"MEDIUM\",\"explanation\":\"(mock) Proposed 2x-fees cap is a fallback position; preferred is a 12-month fee cap.\","
                + "\"suggested_fallback\":\"Each party's aggregate liability shall not exceed the fees paid in the preceding 12 months.\"}],"
                + "\"overall_risk\":\"MEDIUM\"}";
    }

    private String relationClassify(String user) {
        // Deterministic mock: NDA before the current contract during the same deal -> PRECEDES,
        // same type -> SIMILAR_FAMILY, master-agreement wording -> MASTER.
        StringBuilder rels = new StringBuilder();
        String lower = user.toLowerCase();
        boolean currentIsMsa = lower.contains("current contract:") && lower.split("\n", 2)[0].toLowerCase().contains("msa");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[(\\d+)\\]\\s*([^\\n]*)").matcher(user);
        while (m.find()) {
            String idx = m.group(1);
            String blob = m.group(2).toLowerCase();
            String type;
            if (blob.contains("nda") && currentIsMsa) type = "PRECEDES";
            else if (blob.contains("master") || blob.contains("msa")) type = "MASTER";
            else type = "SIMILAR_FAMILY";
            if (rels.length() > 0) rels.append(",");
            rels.append("{\"index\":").append(idx)
                .append(",\"relation_type\":\"").append(type)
                .append("\",\"confidence\":0.6,\"reasons\":[\"(mock) matched on shared parties/entity and period\"]}");
        }
        return "{\"relations\":[" + rels + "]}";
    }

    // ---- helpers ----
    private static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static String orDefault(String s, String d) { return s == null || s.isBlank() ? d : s; }

    private static Integer firstInt(String s) {
        Matcher m = Pattern.compile("(\\d{1,3})\\s*(month|months|mo|year|years|yr)?").matcher(s);
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            String unit = m.group(2);
            if (unit != null && unit.startsWith("year")) n *= 12;
            if (n > 0 && n <= 240) return n;
        }
        return null;
    }

    private static String guessCounterparty(String s) {
        // input is lower-cased; scan for known demo counterparties
        String[] known = {"meridian", "northwind", "contoso", "globex"};
        for (String k : known) {
            if (s.contains(k)) return Character.toUpperCase(k.charAt(0)) + k.substring(1);
        }
        return null;
    }
}
