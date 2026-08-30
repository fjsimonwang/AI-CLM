package com.acme.clm.api;

import com.acme.clm.common.ApiExceptions;
import com.acme.clm.config.CurrentUser;
import com.acme.clm.config.DevPasswordSeeder;
import com.acme.clm.config.JwtService;
import com.acme.clm.config.Permissions;
import com.acme.clm.domain.AppUser;
import com.acme.clm.repo.Repos;
import com.acme.clm.service.AiInsightService;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final Repos.Users users;
    private final Repos.LegalEntities entities;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final CurrentUser current;
    private final AiInsightService insight;

    public AuthController(Repos.Users users, Repos.LegalEntities entities, PasswordEncoder encoder,
                          JwtService jwt, CurrentUser current, AiInsightService insight) {
        this.users = users;
        this.entities = entities;
        this.encoder = encoder;
        this.jwt = jwt;
        this.current = current;
        this.insight = insight;
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        AppUser u = users.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> new ApiExceptions.ForbiddenException("Invalid credentials"));
        if (!encoder.matches(req.password(), u.passwordHash)) {
            throw new ApiExceptions.ForbiddenException("Invalid credentials");
        }
        // Start generating today's dashboard AI insight in the background so it is ready
        // (and cached for the rest of the day) by the time the user reaches the dashboard.
        insight.onLogin(u.id);
        return Map.of("token", jwt.issue(u.id, u.email, u.roles), "user", profile(u));
    }

    @GetMapping("/me")
    public Map<String, Object> me() { return profile(current.get()); }

    /** Convenience for the demo login screen. */
    @GetMapping("/demo-users")
    public List<Map<String, Object>> demoUsers() {
        return users.findAll().stream()
                .sorted((a, b) -> a.email.compareTo(b.email))
                .map(u -> Map.<String, Object>of(
                        "email", u.email,
                        "displayName", u.displayName,
                        "roles", u.roles,
                        "password", DevPasswordSeeder.DEMO_PASSWORD))
                .toList();
    }

    private Map<String, Object> profile(AppUser u) {
        String entityName = u.defaultEntityId == null ? null :
                entities.findById(u.defaultEntityId).map(e -> e.legalName).orElse(null);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", u.id);
        m.put("email", u.email);
        m.put("displayName", u.displayName);
        m.put("department", u.department == null ? "" : u.department);
        m.put("roles", List.of(u.roles.split(",")));
        m.put("permissions", Permissions.forRoles(u.roles));
        m.put("defaultEntityId", u.defaultEntityId == null ? "" : u.defaultEntityId);
        m.put("defaultEntityName", entityName == null ? "" : entityName);
        return m;
    }
}
