package com.acme.clm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "comment_message")
public class CommentMessage {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @Column(name = "thread_id", nullable = false)
    public UUID threadId;
    @Column(name = "author_user_id")
    public UUID authorUserId;
    @Column(name = "channel", nullable = false)
    public String channel = "HUMAN";
    @Column(name = "author_agent")
    public String authorAgent;
    @Column(name = "represented_user_id")
    public UUID representedUserId;
    @Column(name = "body_html", nullable = false, columnDefinition = "text")
    public String bodyHtml;
    @Column(name = "body_format")
    public String bodyFormat = "HTML";
    @JdbcTypeCode(SqlTypes.JSON)
    public String mentions = "[]";
    @Column(name = "created_at")
    public Instant createdAt = Instant.now();
    @Column(name = "edited_at")
    public Instant editedAt;
    public boolean deleted = false;
}
