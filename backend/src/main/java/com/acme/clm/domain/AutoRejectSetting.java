package com.acme.clm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Global per-approver auto-rejection trigger timing (applies to all of the owner's rules). */
@Entity
@Table(name = "auto_reject_setting")
public class AutoRejectSetting {
    public enum Mode { IMMEDIATE, DELAYED }

    @Id
    @Column(name = "owner_user_id")
    public UUID ownerUserId;

    @Enumerated(EnumType.STRING)
    public Mode mode = Mode.IMMEDIATE;

    @Column(name = "delay_hours")
    public int delayHours = 0;

    @Column(name = "updated_at")
    public Instant updatedAt = Instant.now();
}