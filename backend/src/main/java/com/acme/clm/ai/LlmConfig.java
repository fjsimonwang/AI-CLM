package com.acme.clm.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    LlmClient llmClient(LlmProperties props) {
        if (props.isConfigured()) {
            log.info("LLM: live OpenAI-compatible provider at {} (model={}, embeddings={})",
                    props.getBaseUrl(), props.getModel(), props.isEmbeddingEnabled());
            return new OpenAiCompatibleLlmClient(props);
        }
        log.warn("LLM: no api key configured — using deterministic MockLlmClient. "
                + "Set LLM_BASE_URL / LLM_API_KEY / LLM_MODEL in .env to enable a real model.");
        return new MockLlmClient();
    }
}
