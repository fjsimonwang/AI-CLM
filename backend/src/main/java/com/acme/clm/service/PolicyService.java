package com.acme.clm.service;

import com.acme.clm.ai.AiService;
import com.acme.clm.domain.AppUser;
import com.acme.clm.domain.LegalEntity;
import com.acme.clm.domain.PolicyDocument;
import com.acme.clm.repo.Repos;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * CLM policy & procedure documents that ground the help assistant. Each document declares its
 * audience with access dimensions (roles / countries / regions, each empty = "all") plus a
 * confidential flag; {@link #visibleTo} returns only the documents a given user is allowed to see,
 * and {@link #helpContext} renders them for the help prompt.
 */
@Service
public class PolicyService {

    private static final int MAX_DOC_CHARS = 6000;
    private static final int MAX_TOTAL_CHARS = 20000;

    private final Repos.PolicyDocuments policies;
    private final Repos.Users users;
    private final Repos.LegalEntities entities;
    private final AccessService access;
    private final DocToHtmlService docToHtml;

    public PolicyService(Repos.PolicyDocuments policies, Repos.Users users, Repos.LegalEntities entities,
                         AccessService access, DocToHtmlService docToHtml) {
        this.policies = policies;
        this.users = users;
        this.entities = entities;
        this.access = access;
        this.docToHtml = docToHtml;
    }

    /** Documents the given user may see, honouring role / country / region / confidential. */
    public List<PolicyDocument> visibleTo(UUID userId) {
        AppUser u = users.findById(userId).orElse(null);
        if (u == null) return List.of();
        Set<String> roles = csv(u.roles);
        LegalEntity home = u.defaultEntityId == null ? null : entities.findById(u.defaultEntityId).orElse(null);
        String country = home == null ? null : norm(home.countryCode);
        String region = home == null ? null : norm(home.dataResidencyRegion);
        boolean confidentialCleared = access.seesEverything(userId);

        List<PolicyDocument> out = new ArrayList<>();
        for (PolicyDocument p : policies.findByIsActiveTrueOrderByTitleAsc()) {
            if (!matchesDim(p.roles, roles)) continue;
            if (!matchesValue(p.countries, country)) continue;
            if (!matchesValue(p.regions, region)) continue;
            if (p.confidential && !confidentialCleared) continue;
            out.add(p);
        }
        return out;
    }

    /** A text block of the user's visible policies for the help-chat system prompt (empty if none). */
    public String helpContext(UUID userId) {
        List<PolicyDocument> docs = visibleTo(userId);
        if (docs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (PolicyDocument p : docs) {
            String text = AiService.neutralizeFences(docToHtml.toPlainText(p.bodyHtml)).strip();
            if (text.isBlank()) continue;
            if (text.length() > MAX_DOC_CHARS) text = text.substring(0, MAX_DOC_CHARS) + " …";
            sb.append("--- POLICY: ").append(AiService.neutralizeFences(p.title)).append(" ---\n")
                    .append(text).append("\n\n");
            if (sb.length() > MAX_TOTAL_CHARS) break;
        }
        return sb.toString().strip();
    }

    // --- matching ---

    /** A dimension with a list is satisfied when the user has ANY of the listed values. */
    private static boolean matchesDim(String csvList, Set<String> userValues) {
        Set<String> want = csv(csvList);
        if (want.isEmpty()) return true;
        return userValues.stream().map(PolicyService::norm).anyMatch(want::contains);
    }

    /** A dimension with a list is satisfied when the user's single value is in the list. */
    private static boolean matchesValue(String csvList, String userValue) {
        Set<String> want = csv(csvList);
        if (want.isEmpty()) return true;
        return userValue != null && want.contains(norm(userValue));
    }

    private static Set<String> csv(String s) {
        if (s == null || s.isBlank()) return Set.of();
        return Arrays.stream(s.split("[,;]"))
                .map(PolicyService::norm).filter(x -> !x.isEmpty())
                .collect(Collectors.toSet());
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }
}
