package yardmaster.chat;

import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ChatTranslator {

    private final JsonMapper mapper;

    public ChatTranslator() {
        this.mapper = JsonMapper.builder().build();
    }

    public byte[] translate(byte[] callerBody) {
      Map<String, Object> callerBodyMap = mapper.readValue(callerBody, new TypeReference<>() {});
      if (Boolean.TRUE.equals(callerBodyMap.get("stream"))) {
        if (callerBodyMap.containsKey("stream_options")) {
          Map<String, Object> stream_options = (Map<String, Object>) callerBodyMap.get("stream_options");
          stream_options.put("include_usage", true);
          callerBodyMap.put("stream_options", stream_options);
        } else {
          callerBodyMap.put("stream_options", Map.of("include_usage", true));
        }
      }
      return mapper.writeValueAsBytes(callerBodyMap);
    }
}
