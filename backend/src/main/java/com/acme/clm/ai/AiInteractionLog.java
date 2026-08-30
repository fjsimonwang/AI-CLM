package com.acme.clm.ai;

import com.acme.clm.common.Json;
import com.acme.clm.domain.AiInteraction;
import com.acme.clm.repo.Repos;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Persists every AI call for traceability (plan §6A.4) and supports one-click revert. */
@Service
public class AiInteractionLog {

    private final Repos.AiInteractions repo;

    public AiInteractionLog(Repos.AiInteractions repo) { this.repo = repo; }

    public AiInteraction record(String surface, String capability, String modelId, String promptId,
                                String promptVersion, Object input, Object output, Double confidence,
                                Object sourceRefs, int latencyMs, int tokenCost,
                                UUID userId, UUID contractId, UUID intakeSessionId) {
        AiInteraction ai = new AiInteraction();
        ai.surface = surface;
        ai.capability = capability;
        ai.modelId = modelId;
        ai.promptId = promptId;
        ai.promptVersion = promptVersion;
        ai.inputSummary = Json.write(input);
        ai.inputHash = sha256(ai.inputSummary);
        ai.output = Json.write(output);
        ai.confidenceScore = confidence == null ? null : BigDecimal.valueOf(confidence);
        ai.sourceReferences = sourceRefs == null ? null : Json.write(sourceRefs);
        ai.latencyMs = latencyMs;
        ai.tokenCost = tokenCost;
        ai.userId = userId;
        ai.contractId = contractId;
        ai.intakeSessionId = intakeSessionId;
        ai.correlationId = UUID.randomUUID().toString();
        return repo.save(ai);
    }

    public void markOutcome(UUID interactionId, String outcome, Object editedDelta) {
        repo.findById(interactionId).ifPresent(ai -> {
            ai.outcome = outcome;
            if (editedDelta != null) ai.editedDelta = Json.write(editedDelta);
            repo.save(ai);
        });
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.substring(0, 32);
        } catch (Exception e) { return "n/a"; }
    }
}
