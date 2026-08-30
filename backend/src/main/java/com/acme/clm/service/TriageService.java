package com.acme.clm.service;

import com.acme.clm.domain.ContractTypeDefinition;
import com.acme.clm.domain.LegalEntity;
import com.acme.clm.domain.Party;
import java.util.*;
import org.springframework.stereotype.Service;

/** Composite triage scoring (plan §6.3): value, governing law, counterparty risk, data processing, base risk, term. */
@Service
public class TriageService {

    public record Triage(int score, String path, String pathLabel, int estimatedBusinessDays,
                         List<String> factors, boolean autoIssueEligible) {}

    public Triage score(ContractTypeDefinition type, LegalEntity entity, Party counterparty,
                        Double valueAmount, boolean dataProcessing, Integer termMonths,
                        String governingLaw, int clauseDeviations) {
        int score = type == null ? 20 : type.baseRisk;
        List<String> factors = new ArrayList<>();
        if (type != null) factors.add(type.displayName + " base risk " + type.baseRisk);

        double v = valueAmount == null ? 0 : valueAmount;
        if (v >= 1_000_000) { score += 30; factors.add("Value ≥ 1,000,000 (+30)"); }
        else if (v >= 250_000) { score += 18; factors.add("Value ≥ 250,000 (+18)"); }
        else if (v >= 50_000) { score += 8; factors.add("Value ≥ 50,000 (+8)"); }

        if (governingLaw != null && entity != null && entity.defaultGoverningLaw != null
                && !governingLaw.equalsIgnoreCase(entity.defaultGoverningLaw)) {
            score += 15;
            factors.add("Non-standard governing law for " + entity.shortName + " (+15)");
        }

        if (counterparty == null) { score += 10; factors.add("New / unresolved counterparty (+10)"); }
        else if (!"CLEAR".equalsIgnoreCase(counterparty.sanctionsCheckStatus)) {
            score += 12; factors.add("Counterparty not sanctions-cleared (+12)");
        }

        if (dataProcessing) { score += 12; factors.add("Involves personal data — DPA / GDPR review (+12)"); }
        if (termMonths != null && termMonths > 36) { score += 8; factors.add("Term > 36 months (+8)"); }
        if (clauseDeviations > 0) { score += Math.min(30, clauseDeviations * 10);
            factors.add(clauseDeviations + " clause deviation(s) (+" + Math.min(30, clauseDeviations * 10) + ")"); }
        if (type != null && type.requiresLegalReviewDefault) { score += 10; factors.add("Type requires legal review by default (+10)"); }

        score = Math.min(100, score);

        String path; String label; int days;
        if (score < 25) { path = "SELF_SERVICE"; label = "Self-service — no approval required"; days = 0; }
        else if (score < 55) { path = "STANDARD_APPROVAL"; label = "Standard approval"; days = 1; }
        else { path = "LEGAL_REVIEW"; label = "Legal review"; days = 3; }

        boolean autoIssue = type != null && type.autoIssueAllowed && score < 25 && clauseDeviations == 0
                && counterparty != null && "CLEAR".equalsIgnoreCase(counterparty.sanctionsCheckStatus);

        return new Triage(score, path, label, days, factors, autoIssue);
    }
}
