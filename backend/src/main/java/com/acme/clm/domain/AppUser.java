package com.acme.clm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(name = "external_idp_id")
    public String externalIdpId;
    @Column(nullable = false, unique = true)
    public String email;
    @Column(name = "password_hash", nullable = false)
    public String passwordHash;
    @Column(name = "display_name", nullable = false)
    public String displayName;
    @Column(name = "default_entity_id")
    public UUID defaultEntityId;
    public String department;
    @Column(name = "cost_center")
    public String costCenter;
    @Column(name = "manager_user_id")
    public UUID managerUserId;
    @Column(name = "hr_system_id")
    public String hrSystemId;
    public String roles = "REQUESTER";
    public String status = "ACTIVE";
    @Column(name = "created_at")
    public Instant createdAt = Instant.now();
}
