package yardmaster.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class ChatTranslatorTest {

    private final ChatTranslator translator = new ChatTranslator();

    @Test
    void nonStreamingBodyPassesThroughUnchanged() {
        String body = """
                {
                  "model": "gpt-4o-mini",
                  "messages": [
                    {"role": "system", "content": "You are terse."},
                    {"role": "user", "content": "Say hi."}
                  ],
                  "stream": false,
                  "temperature": 0.7,
                  "stop": null,
                  "x_custom": 1
                }
                """;

        Map<String, Object> result = bytesToMap(translator.translate(stringToBytes(body)));

        assertThat(result).isEqualTo(bytesToMap(stringToBytes(body)));
        assertThat(result).containsEntry("stop", null);
        assertThat(result).doesNotContainKey("stream_options");
    }

    @Test
    void streamingBodyGetsIncludeUsage() {
        String body = """
                {
                  "model": "gpt-4o-mini",
                  "messages": [
                    {"role": "system", "content": "You are terse."},
                    {"role": "user", "content": "Say hi."}
                  ],
                  "stream": true,
                  "temperature": 0.7,
                  "stop": null,
                  "x_custom": 1
                }
                """;

        Map<String, Object> result = bytesToMap(translator.translate(stringToBytes(body)));

        assertThat(result).containsEntry("stream", true);
        assertThat(result).containsEntry("stream_options", Map.of("include_usage", true));
        assertThat(result).containsEntry("model", "gpt-4o-mini");
    }

    private static byte[] stringToBytes(String str) {
        return str.getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, Object> bytesToMap(byte[] bytes) {
        return JsonMapper.shared().readValue(bytes, new TypeReference<>() {
        });
    }
}
