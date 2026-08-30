package com.acme.clm.config;

import com.acme.clm.repo.Repos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Replaces the 'SEED' placeholder password hash on demo users with bcrypt('demo1234'). */
@Configuration
public class DevPasswordSeeder {

    private static final Logger log = LoggerFactory.getLogger(DevPasswordSeeder.class);
    public static final String DEMO_PASSWORD = "demo1234";

    @Bean
    ApplicationRunner seedPasswords(Repos.Users users, PasswordEncoder encoder) {
        return args -> {
            var toFix = users.findAll().stream()
                    .filter(u -> "SEED".equals(u.passwordHash))
                    .toList();
            toFix.forEach(u -> u.passwordHash = encoder.encode(DEMO_PASSWORD));
            if (!toFix.isEmpty()) {
                users.saveAll(toFix);
                log.info("Seeded demo password for {} users (password: {})", toFix.size(), DEMO_PASSWORD);
            }
        };
    }
}
