package com.acme.clm.service;

import com.acme.clm.common.Json;
import com.acme.clm.domain.UserSetting;
import com.acme.clm.repo.Repos;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The single per-user switch behind the header "Agent talk" toggle. When a user turns it off it
 * disables ALL of their working-alongside-you AI: the dashboard AI insight, approver AI briefings,
 * and agent participation in contract discussions.
 */
@Service
public class AiFeatures {

    /** Kept as "agent_collab" for backward compatibility with the existing user_setting rows. */
    public static final String KEY = "agent_collab";

    private final Repos.UserSettings settings;

    public AiFeatures(Repos.UserSettings settings) {
        this.settings = settings;
    }

    public boolean enabledFor(UUID userId) {
        if (userId == null) return false;
        return settings.findById(new UserSetting.Key(userId, KEY))
                .map(s -> Boolean.TRUE.equals(Json.readMap(s.value).get("enabled")))
                .orElse(false);
    }
}
