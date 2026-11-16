package yardmaster.providers;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings for upstream AI providers. Holds no keys. */
@ConfigurationProperties(prefix = "yardmaster.providers")
public record ProviderProperties(OpenAi openai) {

    public record OpenAi(String baseUrl, Duration connectTimeout, Duration readTimeout) {
    }
}
