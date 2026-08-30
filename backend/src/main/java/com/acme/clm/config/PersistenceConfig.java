package com.acme.clm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Nested repository interfaces in {@code com.acme.clm.repo.Repos} require considerNestedRepositories. */
@Configuration
@EnableJpaRepositories(basePackages = "com.acme.clm.repo", considerNestedRepositories = true)
public class PersistenceConfig {
}
