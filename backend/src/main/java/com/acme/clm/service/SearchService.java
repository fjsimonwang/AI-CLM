package com.acme.clm.service;

import com.acme.clm.ai.AiService;
import com.acme.clm.common.Json;
import com.acme.clm.domain.SavedReport;
import com.acme.clm.repo.Repos;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Natural-language inquiry (plan §10A). The LLM only translates intent to a bounded structured
 * query; every figure is computed from the database. The interpreted query is always returned.
 */
@Service
public class SearchService {

    private final AiService ai;
    private final ContractService contracts;
    private final Repos.SavedReports savedReports;

    public SearchService(AiService ai, ContractService contracts, Repos.SavedReports savedReports) {
        this.ai = ai;
        this.contracts = contracts;
        this.savedReports = savedReports;
    }

    public record Answer(String question, String interpreted, Map<String, Object> filters, boolean answerable,
                         boolean modelLive, int count, BigDecimal totalValue, String primaryCurrency,
                         List<Map<String, Object>> contracts, List<Map<String, Object>> stats,
                         String metric, String groupBy, String refusal) {}

    public Answer ask(String question, UUID userId) {
        AiService.NlQuery q = ai.translateQuery(question, userId);
        if (!q.answerable()) {
            return new Answer(question, q.interpreted(), q.filters(), false, ai.modelIsLive(), 0,
                    BigDecimal.ZERO, null, List.of(), List.of(), "count", null,
                    "I can't map that to the available data. Try filtering by entity region, contract type, "
                            + "status, counterparty, value or an expiry window.");
        }
        return run(question, q.interpreted(), q.filters(), userId, q.metric(), q.groupBy());
    }

    /** Re-run with filters the user edited directly — no AI translation step. */
    public Answer runEdited(String question, String interpreted, Map<String, Object> filters, UUID userId) {
        String desc = interpreted == null || interpreted.isBlank()
                ? "Contracts matching your filters" : interpreted;
        return run(question, desc, filters == null ? Map.of() : filters, userId, "count_and_value", null);
    }

    private Answer run(String question, String interpreted, Map<String, Object> filters, UUID userId,
                       String metric, String groupBy) {
        Map<String, Object> f = filters == null ? Map.of() : filters;
        List<Map<String, Object>> rows = contracts.listFiltered(userId, f);

        // summary / aggregation over the matching rows (per group when group_by is set)
        List<Map<String, Object>> stats = summarize(rows, groupBy);

        BigDecimal total = BigDecimal.ZERO;
        Map<String, Integer> currencyCounts = new HashMap<>();
        for (Map<String, Object> r : rows) {
            Object v = r.get("valueAmount");
            if (v instanceof Number n) total = total.add(BigDecimal.valueOf(n.doubleValue()));
            else if (v instanceof BigDecimal bd) total = total.add(bd);
            Object cur = r.get("currency");
            if (cur != null) currencyCounts.merge(String.valueOf(cur), 1, Integer::sum);
        }
        String primaryCurrency = currencyCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);

        // a value metric (sum/avg/min/max without grouping) surfaces as the headline total
        if (!rows.isEmpty() && groupBy == null && metric != null && List.of("sum_value", "avg_value", "min_value", "max_value").contains(metric)) {
            Map<String, Object> one = stats.get(0);
            BigDecimal v = switch (metric) {
                case "sum_value" -> (BigDecimal) one.get("totalValue");
                case "avg_value" -> (BigDecimal) one.get("avgValue");
                case "min_value" -> (BigDecimal) one.get("minValue");
                default -> (BigDecimal) one.get("maxValue");
            };
            if (v != null) total = v;
        }

        return new Answer(question, interpreted, f, true, ai.modelIsLive(), rows.size(),
                total, primaryCurrency, rows, stats,
                metric == null ? "count_and_value" : metric, groupBy, null);
    }

    /** count, total/average/min/max value over the matching contracts — one row per group (or one overall row). */
    private List<Map<String, Object>> summarize(List<Map<String, Object>> rows, String groupBy) {
        String rowKey = switch (groupBy == null ? "" : groupBy) {
            case "type" -> "type";
            case "entity" -> "entity";
            case "currency" -> "currency";
            case "riskTier" -> "riskTier";
            case "status" -> "status";
            default -> null;
        };
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String k = rowKey == null
                    ? "All matching contracts"
                    : (r.get(rowKey) == null || String.valueOf(r.get(rowKey)).isBlank() ? "—" : String.valueOf(r.get(rowKey)));
            groups.computeIfAbsent(k, x -> new ArrayList<>()).add(r);
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : groups.entrySet()) {
            BigDecimal sum = BigDecimal.ZERO, min = null, max = null;
            Map<String, Integer> ccy = new HashMap<>();
            for (Map<String, Object> r : e.getValue()) {
                Object v = r.get("valueAmount");
                BigDecimal bd = v instanceof Number n ? BigDecimal.valueOf(n.doubleValue())
                        : v instanceof BigDecimal b ? b : null;
                if (bd != null) {
                    sum = sum.add(bd);
                    if (min == null || bd.compareTo(min) < 0) min = bd;
                    if (max == null || bd.compareTo(max) > 0) max = bd;
                }
                if (r.get("currency") != null) ccy.merge(String.valueOf(r.get("currency")), 1, Integer::sum);
            }
            int n = e.getValue().size();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", e.getKey());
            m.put("count", n);
            m.put("totalValue", min == null ? null : sum);
            m.put("avgValue", min == null || n == 0 ? null : sum.divide(BigDecimal.valueOf(n), 2, java.math.RoundingMode.HALF_UP));
            m.put("minValue", min);
            m.put("maxValue", max);
            m.put("currency", ccy.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null));
            out.add(m);
        }
        return out;
    }

    public SavedReport save(String name, String question, Map<String, Object> interpreted, UUID userId) {
        SavedReport r = new SavedReport();
        r.ownerUserId = userId;
        r.name = (name == null || name.isBlank()) ? "Saved inquiry" : name;
        r.question = question == null ? "" : question;
        r.interpretedQuery = Json.write(interpreted);
        return savedReports.save(r);
    }

    public List<Map<String, Object>> listSaved(UUID userId) {
        return savedReports.findByOwnerUserIdOrderByCreatedAtDesc(userId).stream().map(r -> Map.<String, Object>of(
                "id", r.id, "name", r.name, "question", r.question, "createdAt", r.createdAt)).toList();
    }
}
