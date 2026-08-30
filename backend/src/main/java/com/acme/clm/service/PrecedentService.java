package com.acme.clm.service;

import com.acme.clm.common.Json;
import com.acme.clm.domain.*;
import com.acme.clm.repo.Repos;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;

/** Precedent matching (plan §6B.2): hybrid structural scoring, recency decay, clean-precedent preference. */
@Service
public class PrecedentService {

    private final Repos.Contracts contracts;
    private final Repos.ContractParties contractParties;
    private final Repos.Parties parties;
    private final Repos.LegalEntities entities;

    public PrecedentService(Repos.Contracts contracts, Repos.ContractParties contractParties,
                            Repos.Parties parties, Repos.LegalEntities entities) {
        this.contracts = contracts;
        this.contractParties = contractParties;
        this.parties = parties;
        this.entities = entities;
    }

    public record Match(UUID contractId, String contractNumber, String title, String type,
                        double score, List<String> reasons, LocalDate effectiveDate,
                        String counterparty, String governingLaw) {}

    public List<Match> find(String contractTypeCode, UUID entityId, String counterpartyName,
                            UUID excludeContractId, int limit) {
        String cpNeedle = counterpartyName == null ? null : counterpartyName.toLowerCase().trim();
        LegalEntity targetEntity = entityId == null ? null : entities.findById(entityId).orElse(null);

        List<Match> out = new ArrayList<>();
        for (Contract c : contracts.findAll()) {
            if (excludeContractId != null && excludeContractId.equals(c.id)) continue;
            if (!"EXECUTED".equalsIgnoreCase(c.status)) continue;

            double score = 0;
            List<String> reasons = new ArrayList<>();

            String cpName = counterpartyOf(c.id);
            if (cpNeedle != null && cpName != null && cpName.toLowerCase().contains(cpNeedle)) {
                score += 0.35;
                reasons.add("Same counterparty (" + cpName + ") — strongest precedent");
            }
            if (contractTypeCode != null && contractTypeCode.equalsIgnoreCase(c.contractTypeCode)) {
                score += 0.25;
                reasons.add("Same contract type (" + c.contractTypeCode + ")");
            } else if (contractTypeCode != null && sameCategory(contractTypeCode, c.contractTypeCode)) {
                score += 0.08;
                reasons.add("Related contract type (" + c.contractTypeCode + ")");
            }
            if (targetEntity != null && targetEntity.id.equals(c.contractingEntityId)) {
                score += 0.12;
                reasons.add("Same contracting entity");
            } else if (targetEntity != null) {
                LegalEntity ce = entities.findById(c.contractingEntityId).orElse(null);
                if (ce != null && Objects.equals(ce.dataResidencyRegion, targetEntity.dataResidencyRegion)) {
                    score += 0.05;
                    reasons.add("Same region");
                }
            }
            if (targetEntity != null && c.governingLawCode != null
                    && c.governingLawCode.equalsIgnoreCase(targetEntity.defaultGoverningLaw)) {
                score += 0.08;
                reasons.add("Same governing law (" + c.governingLawCode + ")");
            }
            if (c.effectiveDate != null) {
                long months = ChronoUnit.MONTHS.between(c.effectiveDate, LocalDate.now());
                double recency = Math.max(0, 0.12 * (1.0 - months / 24.0));
                if (recency > 0.01) {
                    score += recency;
                    reasons.add(months <= 1 ? "Signed this month" : "Signed " + months + " months ago");
                }
                if (months > 18) reasons.add("⚠ Older than 18 months — staleness check advised");
            }
            boolean amended = contracts.findByParentContractId(c.id).stream()
                    .anyMatch(ch -> "AMENDS".equals(ch.relationshipType));
            if (!amended) {
                score += 0.08;
                reasons.add("Executed without amendment — clean precedent");
            }

            if (score <= 0) continue;
            score = Math.min(1.0, score);
            out.add(new Match(c.id, c.contractNumber, c.title, c.contractTypeCode,
                    round(score), reasons, c.effectiveDate, cpName, c.governingLawCode));
        }
        out.sort(Comparator.comparingDouble(Match::score).reversed());
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    private String counterpartyOf(UUID contractId) {
        for (ContractParty cp : contractParties.findByContractId(contractId)) {
            if ("COUNTERPARTY".equals(cp.role)) {
                return parties.findById(cp.partyId).map(p -> p.legalName).orElse(null);
            }
        }
        return contractParties.findByContractId(contractId).stream().findFirst()
                .flatMap(cp -> parties.findById(cp.partyId)).map(p -> p.legalName).orElse(null);
    }

    private boolean sameCategory(String a, String b) {
        Set<String> commercial = Set.of("MSA", "SOW", "VENDOR_PURCHASE");
        return commercial.contains(a) && commercial.contains(b);
    }

    private static double round(double d) { return Math.round(d * 10000.0) / 10000.0; }
}
