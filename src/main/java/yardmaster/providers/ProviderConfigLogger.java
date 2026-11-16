package yardmaster.providers;

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
        log.info("OpenAI provider: base-url={} connect-timeout={} read-timeout={}",
                openai.baseUrl(), openai.connectTimeout(), openai.readTimeout());
    }
}
