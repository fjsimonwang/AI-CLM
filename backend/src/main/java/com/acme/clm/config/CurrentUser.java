package com.acme.clm.config;

import com.acme.clm.common.ApiExceptions;
import com.acme.clm.domain.AppUser;
import com.acme.clm.repo.Repos;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Convenience accessor for the authenticated principal. */
@Component
public class CurrentUser {

    private final Repos.Users users;

    public CurrentUser(Repos.Users users) { this.users = users; }

    public UUID id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || "anonymousUser".equals(auth.getName())) {
            throw new ApiExceptions.ForbiddenException("Not authenticated");
        }
        return UUID.fromString(auth.getName());
    }

    public AppUser get() {
        return users.findById(id())
                .orElseThrow(() -> new ApiExceptions.ForbiddenException("Unknown user"));
    }
}
