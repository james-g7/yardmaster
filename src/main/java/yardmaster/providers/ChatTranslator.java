package yardmaster.providers;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns the caller's OpenAI chat request body into the body sent to the provider.
 *
 * <p>Every field is kept as sent. When the caller streams, {@code stream_options.include_usage} is
 * set so the final chunk carries token counts.
 */
@Component
public class ChatTranslator {

  private final JsonMapper mapper;

  /** Uses its own mapper, so app-wide {@code spring.jackson.*} settings never change the body. */
  public ChatTranslator() {
    this.mapper = JsonMapper.builder().build();
  }

  /**
   * Returns the provider request body for the given caller request body.
   *
   * @throws IllegalArgumentException if the body is not a JSON object
   */
  public byte[] translate(byte[] callerBody) {
    Map<String, Object> callerBodyMap = parseObject(callerBody);
    if (Boolean.TRUE.equals(callerBodyMap.get("stream"))) {
      callerBodyMap.put("stream_options", withIncludeUsage(callerBodyMap.get("stream_options")));
    }
    return mapper.writeValueAsBytes(callerBodyMap);
  }

  private Map<String, Object> parseObject(byte[] body) {
    Map<String, Object> map;
    try {
      map = mapper.readValue(body, new TypeReference<>() {});
    } catch (JacksonException e) {
      // Jackson's message can quote part of the body, so it is not kept as the cause.
      throw notJsonObject();
    }
    if (map == null) {
      // The literal body "null" parses to a null map.
      throw notJsonObject();
    }
    return map;
  }

  private static IllegalArgumentException notJsonObject() {
    return new IllegalArgumentException("request body must be a JSON object");
  }

  /**
   * Keeps whatever the caller put in stream_options and sets include_usage, so the final chunk
   * carries token counts. A missing, null or non-object value is replaced.
   */
  private static Map<String, Object> withIncludeUsage(Object streamOptions) {
    Map<String, Object> options = new LinkedHashMap<>();
    if (streamOptions instanceof Map<?, ?> existing) {
      // JSON object keys are always strings.
      existing.forEach((key, value) -> options.put((String) key, value));
    }
    options.put("include_usage", true);
    return options;
  }
}
