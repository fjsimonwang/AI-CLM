package com.acme.clm.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "merge_field")
public class MergeField {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(name = "template_id", nullable = false)
    public UUID templateId;
    @Column(name = "field_key", nullable = false)
    public String fieldKey;
    @Column(name = "source_path", nullable = false)
    public String sourcePath;
    @Column(name = "format_mask")
    public String formatMask;
    @Column(name = "is_required")
    public boolean isRequired = false;
}
