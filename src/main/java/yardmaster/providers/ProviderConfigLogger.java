package yardmaster.providers;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Logs where upstream calls will go, so overrides are easy to confirm. */
@Component
class ProviderConfigLogger {

    private static final Logger log = LoggerFactory.getLogger(ProviderConfigLogger.class);

    private final ProviderProperties properties;

    ProviderConfigLogger(ProviderProperties properties) {
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    void logConfig() {
        var openai = properties.openai();
        log.info("OpenAI provider: endpoint={} connect-timeout={} read-timeout={}",
                endpointOf(openai.baseUrl()), openai.connectTimeout(), openai.readTimeout());
    }

    /**
     * Reduces a base URL to scheme, host and port. Operators may point the base URL at a
     * proxy or gateway with credentials in the user-info part or a token in the query
     * string; neither of those, nor the path, must ever reach the logs.
     */
    static String endpointOf(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "(unset)";
        }
        URI uri;
        try {
            uri = URI.create(baseUrl.strip());
        } catch (IllegalArgumentException e) {
            return "(unparseable)";
        }
        if (uri.getScheme() == null || uri.getHost() == null) {
            return "(unparseable)";
        }
        var endpoint = uri.getScheme() + "://" + uri.getHost();
        return uri.getPort() == -1 ? endpoint : endpoint + ":" + uri.getPort();
    }
}
