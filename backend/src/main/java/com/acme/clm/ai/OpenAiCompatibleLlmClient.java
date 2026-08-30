package com.acme.clm.ai;

import com.acme.clm.common.Json;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** OpenAI-compatible Chat Completions + Embeddings client. */
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);

    private final LlmProperties props;
    private final RestClient http;

    public OpenAiCompatibleLlmClient(LlmProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) Duration.ofSeconds(15).toMillis());
        f.setReadTimeout((int) Duration.ofSeconds(props.getTimeoutSeconds()).toMillis());
        this.http = RestClient.builder()
                .baseUrl(props.getBaseUrl().replaceAll("/+$", ""))
                .requestFactory(f)
                .defaultHeader("Authorization", "Bearer " + props.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override public boolean isLive() { return true; }
    @Override public String modelId() { return props.getModel(); }

    @Override
    public ChatResult chat(List<Message> messages, boolean reasoning) {
        return call(messages, reasoning, false);
    }

    @Override
    public ChatResult chatJson(List<Message> messages, boolean reasoning) {
        return call(messages, reasoning, true);
    }

    private ChatResult call(List<Message> messages, boolean reasoning, boolean json) {
        String model = reasoning ? props.reasoningModelOrDefault() : props.getModel();
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", json ? 0.1 : 0.4);
        body.put("messages", messages.stream()
                .map(m -> Map.of("role", m.role(), "content", m.content())).toList());
        if (json) body.put("response_format", Map.of("type", "json_object"));

        try {
            String raw = http.post().uri("/chat/completions").body(body).retrieve().body(String.class);
            JsonNode root = Json.read(raw);
            String text = root.path("choices").path(0).path("message").path("content").asText("");
            JsonNode usage = root.path("usage");
            return new ChatResult(text.trim(),
                    usage.path("prompt_tokens").asInt(0),
                    usage.path("completion_tokens").asInt(0),
                    root.path("model").asText(model));
        } catch (Exception e) {
            log.warn("LLM chat call failed: {}", e.getMessage());
            throw new LlmUnavailableException("LLM request failed: " + e.getMessage(), e);
        }
    }

    @Override
    public float[] embed(String text) {
        if (!props.isEmbeddingEnabled()) return new float[0];
        try {
            Map<String, Object> body = Map.of("model", props.getEmbeddingModel(), "input", text);
            String raw = http.post().uri("/embeddings").body(body).retrieve().body(String.class);
            JsonNode arr = Json.read(raw).path("data").path(0).path("embedding");
            float[] v = new float[arr.size()];
            for (int i = 0; i < arr.size(); i++) v[i] = (float) arr.get(i).asDouble();
            return v;
        } catch (Exception e) {
            log.warn("Embedding call failed: {}", e.getMessage());
            return new float[0];
        }
    }

    public static class LlmUnavailableException extends RuntimeException {
        public LlmUnavailableException(String m, Throwable t) { super(m, t); }
    }
}
