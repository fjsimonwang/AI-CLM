package com.acme.clm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "intake_session")
public class IntakeSession {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(name = "requester_user_id", nullable = false)
    public UUID requesterUserId;
    public String channel = "CHAT";
    public String status = "OPEN";
    @Column(name = "contract_type_code")
    public String contractTypeCode;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "captured_fields")
    public String capturedFields = "{}";
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_provenance")
    public String fieldProvenance = "{}";
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "confidence_scores")
    public String confidenceScores = "{}";
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conversation_history")
    public String conversationHistory = "[]";
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "triage_result")
    public String triageResult;
    @Column(name = "resulting_contract_id")
    public UUID resultingContractId;
    /** True once the user explicitly saved the draft (Save button or submit). */
    @Column(nullable = false)
    public boolean saved;
    /** Reference for saved drafts, e.g. REQ-2026-0007; assigned at first save. */
    @Column(name = "request_number")
    public String requestNumber;
    @Column(name = "paper_filename")
    public String paperFilename;
    @Column(name = "paper_body_html", columnDefinition = "text")
    public String paperBodyHtml;
    @Column(name = "created_at")
    public Instant createdAt = Instant.now();
    @Column(name = "updated_at")
    public Instant updatedAt = Instant.now();
}
