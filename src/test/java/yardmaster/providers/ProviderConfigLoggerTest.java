package yardmaster.providers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProviderConfigLoggerTest {

    @Test
    void keepsSchemeHostAndPortOnly() {
        assertThat(ProviderConfigLogger.endpointOf("https://api.openai.com/v1"))
                .isEqualTo("https://api.openai.com");
        assertThat(ProviderConfigLogger.endpointOf("http://localhost:9999/v1"))
                .isEqualTo("http://localhost:9999");
    }

    @Test
    void dropsCredentialsInUserInfo() {
        var endpoint = ProviderConfigLogger.endpointOf("https://user:s3cret@gateway.example:8443/openai/v1");

        assertThat(endpoint).isEqualTo("https://gateway.example:8443");
        assertThat(endpoint).doesNotContain("user", "s3cret");
    }

    @Test
    void dropsQueryStringAndPath() {
        var endpoint = ProviderConfigLogger.endpointOf("https://proxy.example/v1?token=abc123&x=y");

        assertThat(endpoint).isEqualTo("https://proxy.example");
        assertThat(endpoint).doesNotContain("abc123", "token", "/v1");
    }

    @Test
    void neverEchoesValuesItCannotParse() {
        assertThat(ProviderConfigLogger.endpointOf(null)).isEqualTo("(unset)");
        assertThat(ProviderConfigLogger.endpointOf("  ")).isEqualTo("(unset)");
        assertThat(ProviderConfigLogger.endpointOf("not a url with secret")).isEqualTo("(unparseable)");
        assertThat(ProviderConfigLogger.endpointOf("secret-token-only")).isEqualTo("(unparseable)");
    }
}
