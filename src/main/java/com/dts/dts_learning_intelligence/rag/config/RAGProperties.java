package com.dts.dts_learning_intelligence.rag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "dts.rag")
@Getter
@Setter
public class RAGProperties {

    private boolean enabled = true;
    private GeminiProperties gemini = new GeminiProperties();
    private TokenLimitsProperties tokenLimits = new TokenLimitsProperties();

    @Getter
    @Setter
    public static class GeminiProperties {
        private String apiKey = "";
        private String model = "gemini-2.5-flash";
        private String embeddingModel = "text-embedding-004";
    }

    @Getter
    @Setter
    public static class TokenLimitsProperties {
        private int maxQueryCharacters = 500;
        private int maxOutputTokens = 600;
        private int rateLimitPerMinute = 5;
        private int dailyMaxQueries = 30;
        private int dailyMaxTokens = 40000;
    }
}
