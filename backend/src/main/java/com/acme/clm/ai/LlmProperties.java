package com.acme.clm.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clm.llm")
public class LlmProperties {
    private String baseUrl = "";
    private String apiKey = "";
    private String model = "gpt-4o-mini";
    private String modelReasoning = "";
    private String embeddingModel = "";
    private int embeddingDim = 1536;
    private int timeoutSeconds = 90;

    public boolean isConfigured() { return apiKey != null && !apiKey.isBlank() && baseUrl != null && !baseUrl.isBlank(); }
    public boolean isEmbeddingEnabled() { return isConfigured() && embeddingModel != null && !embeddingModel.isBlank(); }
    public String reasoningModelOrDefault() { return modelReasoning == null || modelReasoning.isBlank() ? model : modelReasoning; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String v) { this.baseUrl = v; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String v) { this.apiKey = v; }
    public String getModel() { return model; }
    public void setModel(String v) { this.model = v; }
    public String getModelReasoning() { return modelReasoning; }
    public void setModelReasoning(String v) { this.modelReasoning = v; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String v) { this.embeddingModel = v; }
    public int getEmbeddingDim() { return embeddingDim; }
    public void setEmbeddingDim(int v) { this.embeddingDim = v; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int v) { this.timeoutSeconds = v; }
}
