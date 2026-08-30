package com.acme.clm.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "legal_team")
public class LegalTeam {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    public String name;
    public String region;
    @Column(name = "default_queue_sla_hours")
    public int defaultQueueSlaHours = 48;
}
