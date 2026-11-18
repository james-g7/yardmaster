package yardmaster.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import yardmaster.providers.ChatTranslator;

class ChatTranslatorTest {

  private final ChatTranslator translator = new ChatTranslator();

  @Test
  void nonStreamingBodyPassesThroughUnchanged() {
    String body =
        """
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
    String body =
        """
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

  @Test
  void streamingOptionsAreKept() {
    String body =
        """
        {
          "model": "gpt-4o-mini",
          "messages": [
            {"role": "system", "content": "You are terse."},
            {"role": "user", "content": "Say hi."}
          ],
          "stream": true,
          "temperature": 0.7,
          "stop": null,
          "x_custom": 1,
          "stream_options": {
            "y_custom": true
          }
        }
        """;

    Map<String, Object> result = bytesToMap(translator.translate(stringToBytes(body)));

    assertThat(result).containsEntry("stream", true);
    assertThat(result)
        .containsEntry("stream_options", Map.of("y_custom", true, "include_usage", true));
    assertThat(result).containsEntry("temperature", 0.7);
  }

  @Test
  void streamingForcesIncludeUsage() {
    String body =
        """
        {
          "model": "gpt-4o-mini",
          "messages": [
            {"role": "system", "content": "You are terse."},
            {"role": "user", "content": "Say hi."}
          ],
          "stream": true,
          "temperature": 0.7,
          "stop": null,
          "x_custom": 1,
          "stream_options": {
            "include_usage": false
          }
        }
        """;

    Map<String, Object> result = bytesToMap(translator.translate(stringToBytes(body)));

    assertThat(result).containsEntry("stream", true);
    assertThat(result).containsEntry("stream_options", Map.of("include_usage", true));
    assertThat(result).containsEntry("x_custom", 1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "\"x\"", "5", "[]"})
  void streamingReplacesNonObjectStreamOptions(String streamOptions) {
    String body =
        """
        {
          "model": "gpt-4o-mini",
          "messages": [{"role": "user", "content": "Say hi."}],
          "stream": true,
          "stream_options": %s
        }
        """
            .formatted(streamOptions);

    Map<String, Object> result = bytesToMap(translator.translate(stringToBytes(body)));

    assertThat(result).containsEntry("stream_options", Map.of("include_usage", true));
  }

  @ParameterizedTest
  @ValueSource(strings = {"[1, 2]", "null", "", "{\"content\": secretprompt}"})
  void rejectsBodyThatIsNotJsonObjectWithoutLeakingIt(String body) {
    assertThatThrownBy(() -> translator.translate(stringToBytes(body)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageNotContaining("secretprompt")
        .hasNoCause();
  }

  private static byte[] stringToBytes(String str) {
    return str.getBytes(StandardCharsets.UTF_8);
  }

  private static Map<String, Object> bytesToMap(byte[] bytes) {
    return JsonMapper.shared().readValue(bytes, new TypeReference<>() {});
  }
}
