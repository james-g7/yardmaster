package yardmaster.providers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

class ProviderPropertiesTest {

    @Test
    void usesDefaultsFromApplicationYml() throws IOException {
        var openai = bind(Map.of()).openai();

        assertThat(openai.baseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(openai.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(openai.readTimeout()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void environmentVariablesOverrideDefaults() throws IOException {
        var openai = bind(Map.of(
                "YARDMASTER_PROVIDERS_OPENAI_BASE_URL", "http://localhost:9999/v1",
                "YARDMASTER_PROVIDERS_OPENAI_CONNECT_TIMEOUT", "2s",
                "YARDMASTER_PROVIDERS_OPENAI_READ_TIMEOUT", "90s")).openai();

        assertThat(openai.baseUrl()).isEqualTo("http://localhost:9999/v1");
        assertThat(openai.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(openai.readTimeout()).isEqualTo(Duration.ofSeconds(90));
    }

    private static ProviderProperties bind(Map<String, Object> env) throws IOException {
        List<PropertySource<?>> yml = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        var sources = new MutablePropertySources();
        // Environment first, like Spring Boot, so it wins over application.yml.
        sources.addFirst(new SystemEnvironmentPropertySource("env", env));
        yml.forEach(sources::addLast);
        return new Binder(ConfigurationPropertySources.from(sources))
                .bind("yardmaster.providers", ProviderProperties.class).get();
    }
}
