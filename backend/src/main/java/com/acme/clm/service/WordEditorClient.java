package com.acme.clm.service;

import com.acme.clm.common.Json;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin client for the embedded word-editor service. Creates one document per contract from the
 * assembled draft HTML, keeps it in sync, and mints short-lived document-scoped tokens for the
 * iframe embed (see word-editor/server/scopedAuth.js).
 */
@Component
public class WordEditorClient {

    private static final Logger log = LoggerFactory.getLogger(WordEditorClient.class);

    private final String internalUrl;
    private final String publicUrl;
    private final String token;
    private final RestClient http;

    public WordEditorClient(
            @Value("${clm.word-editor.internal-url:}") String internalUrl,
            @Value("${clm.word-editor.public-url:}") String publicUrl,
            @Value("${clm.word-editor.token:}") String token) {
        this.internalUrl = trim(internalUrl);
        this.publicUrl = trim(publicUrl);
        this.token = token == null ? "" : token;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        f.setReadTimeout((int) Duration.ofSeconds(20).toMillis());
        this.http = this.internalUrl.isEmpty() ? null : RestClient.builder()
                .baseUrl(this.internalUrl)
                .requestFactory(f)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeaders(h -> { if (!this.token.isEmpty()) h.add("Authorization", "Bearer " + this.token); })
                .build();
    }

    public boolean enabled() { return http != null; }

    public String publicBaseUrl() { return publicUrl.isEmpty() ? "http://localhost:3100" : publicUrl; }

    /** Create a document seeded with HTML content; returns the editor document id, or null on failure. */
    public String createDocument(String title, String html, String contractId) {
        if (http == null) return null;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("title", title);
            body.put("state", html);            // word-editor stores HTML in `state`
            body.put("contractId", contractId);
            body.put("tenantId", "acme");
            String raw = http.post().uri("/api/documents").body(body).retrieve().body(String.class);
            return Json.read(raw).path("id").asText(null);
        } catch (Exception e) {
            log.warn("word-editor createDocument failed: {}", e.toString());
            return null;
        }
    }

    public boolean setContent(String docId, String html) {
        if (http == null || docId == null) return false;
        try {
            http.put().uri("/api/documents/{id}/content", docId)
                    .body(Map.of("html", html)).retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("word-editor setContent failed: {}", e.toString());
            return false;
        }
    }

    /** Current plain text of the editor document, or null on failure. */
    public String getText(String docId) {
        if (http == null || docId == null) return null;
        try {
            String raw = http.get().uri("/api/documents/{id}/content", docId).retrieve().body(String.class);
            return Json.read(raw).path("text").asText(null);
        } catch (Exception e) {
            log.warn("word-editor getText failed: {}", e.toString());
            return null;
        }
    }

    /** Mint a short-lived token scoped to one document for the iframe embed. */
    public String mintScopedToken(String docId, String contractId) {
        if (http == null || docId == null) return "";
        try {
            Map<String, Object> body = Map.of(
                    "editorDocumentId", docId, "contractId", contractId, "tenantId", "acme", "ttlSeconds", 3300);
            String raw = http.post().uri("/api/auth/token").body(body).retrieve().body(String.class);
            JsonNode j = Json.read(raw);
            return j.path("token").asText("");
        } catch (Exception e) {
            log.warn("word-editor mintScopedToken failed (embedding without scope): {}", e.toString());
            return "";
        }
    }

    public JsonNode meta(String docId) {
        if (http == null || docId == null) return null;
        try {
            String raw = http.get().uri("/api/documents/{id}/meta", docId).retrieve().body(String.class);
            return Json.read(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private static String trim(String s) { return s == null ? "" : s.replaceAll("/+$", ""); }
}
