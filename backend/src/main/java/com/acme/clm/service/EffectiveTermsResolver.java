package com.acme.clm.service;

import com.acme.clm.domain.Contract;
import com.acme.clm.domain.ContractTerm;
import com.acme.clm.repo.Repos;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Resolves the effective value of each term for a contract as of a date, walking the
 * parent chain (CHILD_OF) and applying AMENDS contracts in effective-date order (plan §5.3).
 */
@Service
public class EffectiveTermsResolver {

    private final Repos.Contracts contracts;
    private final Repos.ContractTerms terms;

    public EffectiveTermsResolver(Repos.Contracts contracts, Repos.ContractTerms terms) {
        this.contracts = contracts;
        this.terms = terms;
    }

    public record ResolvedTerm(String key, Object value, String termType, UUID sourceContractId,
                               String sourceContractNumber, boolean inherited, boolean overridden,
                               Double extractionConfidence, boolean verified) {}

    public Map<String, ResolvedTerm> resolve(UUID contractId, LocalDate asOf) {
        LocalDate when = asOf == null ? LocalDate.now() : asOf;

        // 1. ancestor chain, root first
        List<Contract> chain = new ArrayList<>();
        Contract c = contracts.findById(contractId).orElse(null);
        int guard = 0;
        while (c != null && guard++ < 8) {
            chain.add(0, c);
            c = c.parentContractId == null ? null : contracts.findById(c.parentContractId).orElse(null);
        }
        if (chain.isEmpty()) return Map.of();

        Set<UUID> chainIds = new HashSet<>();
        for (Contract n : chain) chainIds.add(n.id);

        // 2/3. own + inherited terms down the chain
        Map<String, ResolvedTerm> out = new LinkedHashMap<>();
        for (Contract node : chain) {
            boolean isSelf = node.id.equals(contractId);
            for (ContractTerm t : activeTerms(node.id, when)) {
                boolean existed = out.containsKey(t.termKey);
                out.put(t.termKey, toResolved(t, node, !isSelf, existed));
            }
        }

        // 4. AMENDS contracts pointing at any node in the chain, oldest first
        List<Contract> amendments = new ArrayList<>();
        for (Contract node : chain) {
            for (Contract child : contracts.findByParentContractId(node.id)) {
                if ("AMENDS".equals(child.relationshipType)) amendments.add(child);
            }
        }
        amendments.sort(Comparator.comparing(a -> a.effectiveDate == null ? LocalDate.MIN : a.effectiveDate));
        for (Contract amend : amendments) {
            for (ContractTerm t : activeTerms(amend.id, when)) {
                boolean existed = out.containsKey(t.termKey);
                out.put(t.termKey, toResolved(t, amend, false, existed));
            }
        }
        return out;
    }

    private List<ContractTerm> activeTerms(UUID cid, LocalDate when) {
        List<ContractTerm> all = terms.findByContractId(cid);
        List<ContractTerm> active = new ArrayList<>();
        for (ContractTerm t : all) {
            boolean fromOk = t.effectiveFrom == null || !when.isBefore(t.effectiveFrom);
            boolean toOk = t.effectiveTo == null || when.isBefore(t.effectiveTo);
            if (fromOk && toOk) active.add(t);
        }
        return active;
    }

    private ResolvedTerm toResolved(ContractTerm t, Contract source, boolean inherited, boolean overridesPrevious) {
        Object value = com.acme.clm.common.Json.read(t.termValue);
        // unwrap JsonNode scalar to a plain Java value for the API
        Object plain = com.acme.clm.common.Json.mapper().convertValue(value, Object.class);
        return new ResolvedTerm(
                t.termKey, plain, t.termType, source.id, source.contractNumber,
                inherited, overridesPrevious,
                t.extractionConfidence == null ? null : t.extractionConfidence.doubleValue(),
                t.verifiedBy != null);
    }
}
