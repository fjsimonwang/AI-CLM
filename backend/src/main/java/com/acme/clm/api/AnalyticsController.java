package com.acme.clm.api;

import com.acme.clm.config.CurrentUser;
import com.acme.clm.domain.Contract;
import com.acme.clm.domain.LegalEntity;
import com.acme.clm.domain.WorkflowTask;
import com.acme.clm.repo.Repos;
import com.acme.clm.service.AccessService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import org.springframework.web.bind.annotation.*;

/**
 * Portfolio analytics for the dashboard. Every figure is scoped to the contracts the
 * current user may see (role bypass, involvement or access grants).
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final Repos.Contracts contracts;
    private final Repos.LegalEntities entities;
    private final Repos.Obligations obligations;
    private final Repos.WorkflowTasks tasks;
    private final Repos.WorkflowInstances instances;
    private final Repos.ContractTerms terms;
    private final AccessService access;
    private final CurrentUser current;

    public AnalyticsController(Repos.Contracts contracts, Repos.LegalEntities entities,
                               Repos.Obligations obligations, Repos.WorkflowTasks tasks,
                               Repos.WorkflowInstances instances, Repos.ContractTerms terms,
                               AccessService access, CurrentUser current) {
        this.contracts = contracts;
        this.entities = entities;
        this.obligations = obligations;
        this.tasks = tasks;
        this.instances = instances;
        this.terms = terms;
        this.access = access;
        this.current = current;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        List<Contract> all = access.filterVisible(current.id(), contracts.findAll());
        Set<UUID> visible = all.stream().map(c -> c.id).collect(java.util.stream.Collectors.toSet());
        Map<UUID, LegalEntity> entityById = entities.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(e -> e.id, e -> e));
        LocalDate now = LocalDate.now();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalContracts", all.size());
        out.put("byStatus", grouped(all, c -> c.status, false));
        out.put("byEntity", grouped(all, c -> {
            LegalEntity e = entityById.get(c.contractingEntityId);
            return e == null ? "(none)" : e.shortName;
        }, true));
        out.put("byType", grouped(all, c -> c.contractTypeCode, true));

        // portfolio value by currency
        Map<String, BigDecimal> valueByCcy = new TreeMap<>();
        for (Contract c : all) {
            if (c.valueAmount == null || c.currency == null) continue;
            valueByCcy.merge(c.currency, c.valueAmount, BigDecimal::add);
        }
        out.put("portfolioValueByCurrency", valueByCcy);

        // expiry pipeline
        Map<String, Long> expiry = new LinkedHashMap<>();
        expiry.put("30", expiringWithin(all, now, 30));
        expiry.put("60", expiringWithin(all, now, 60));
        expiry.put("90", expiringWithin(all, now, 90));
        expiry.put("180", expiringWithin(all, now, 180));
        out.put("expiryPipeline", expiry);

        // risk tiers
        Map<String, Long> risk = new LinkedHashMap<>();
        for (String tier : List.of("LOW", "MEDIUM", "HIGH")) {
            risk.put(tier, all.stream().filter(c -> tier.equalsIgnoreCase(c.riskTier)).count());
        }
        out.put("riskTiers", risk);

        // migration confidence distribution
        long migrated = all.stream().filter(c -> "MIGRATED".equals(c.source)).count();
        long unverifiedTerms = terms.findAll().stream()
                .filter(t -> visible.contains(t.contractId))
                .filter(t -> t.verifiedBy == null && t.extractionConfidence != null && t.extractionConfidence.doubleValue() < 0.75)
                .count();
        out.put("migration", Map.of("migratedContracts", migrated, "unverifiedLowConfidenceTerms", unverifiedTerms));

        // obligations
        long overdueOb = obligations.findAll().stream()
                .filter(o -> visible.contains(o.contractId))
                .filter(o -> !"CLOSED".equals(o.status) && o.dueDate != null && o.dueDate.isBefore(now)).count();
        out.put("obligationsOverdue", overdueOb);

        // workload (tasks on contracts I can see)
        Map<String, Long> openTasksByType = new LinkedHashMap<>();
        for (WorkflowTask t : tasks.findByStatus("OPEN")) {
            UUID cid = instances.findById(t.workflowInstanceId).map(i -> i.contractId).orElse(null);
            if (cid == null || !visible.contains(cid)) continue;
            openTasksByType.merge(t.taskType, 1L, Long::sum);
        }
        out.put("openWorkflowTasks", openTasksByType);
        out.put("openWorkflowTaskTotal", openTasksByType.values().stream().mapToLong(Long::longValue).sum());

        return out;
    }

    private long expiringWithin(List<Contract> all, LocalDate now, int days) {
        LocalDate h = now.plusDays(days);
        return all.stream().filter(c -> c.expiryDate != null
                && !c.expiryDate.isBefore(now) && !c.expiryDate.isAfter(h)).count();
    }

    private List<Map<String, Object>> grouped(List<Contract> all, Function<Contract, String> key, boolean byCountDesc) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Contract c : all) {
            String k = key.apply(c);
            counts.merge(k == null || k.isBlank() ? "(none)" : k, 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted(byCountDesc ? Map.Entry.<String, Long>comparingByValue().reversed() : Map.Entry.comparingByKey())
                .map(e -> Map.<String, Object>of("key", e.getKey(), "value", e.getValue()))
                .toList();
    }
}