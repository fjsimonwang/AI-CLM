package com.acme.clm.ai;

import java.util.List;

/** Provider-agnostic access to chat + embedding models. Business logic depends only on this. */
public interface LlmClient {

    record Message(String role, String content) {
        public static Message system(String c) { return new Message("system", c); }
        public static Message user(String c) { return new Message("user", c); }
        public static Message assistant(String c) { return new Message("assistant", c); }
    }

    record ChatResult(String text, int promptTokens, int completionTokens, String modelId) {}

    /** Free-form chat completion. */
    ChatResult chat(List<Message> messages, boolean reasoning);

    /** Chat completion constrained to a single JSON object response. */
    ChatResult chatJson(List<Message> messages, boolean reasoning);

    /** Embedding vector for a piece of text; empty if embeddings are not configured. */
    float[] embed(String text);

    /** Whether a real provider is wired (false => deterministic mock). */
    boolean isLive();

    String modelId();
}
