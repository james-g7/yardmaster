package yardmaster.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Calls the fake server directly. No real provider is involved. */
class FakeOpenAiTest {

    private FakeOpenAi fake;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void start() {
        fake = new FakeOpenAi();
    }

    @AfterEach
    void stop() {
        fake.close();
    }

    private HttpResponse<java.io.InputStream> post() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(fake.baseUrl() + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"model\":\"x\",\"stream\":true}"))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofInputStream());
    }

    private static List<String> readDataLines(HttpResponse<java.io.InputStream> res) throws IOException {
        List<String> data = new ArrayList<>();
        try (var reader = new BufferedReader(new InputStreamReader(res.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    data.add(line.substring(6));
                }
            }
        }
        return data;
    }

    @Test
    void streamsChunksThenUsageThenDone() throws Exception {
        HttpResponse<java.io.InputStream> res = post();

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.headers().firstValue("Content-Type")).contains("text/event-stream");
        List<String> data = readDataLines(res);

        int parts = FakeOpenAi.CONTENT_PARTS.size();
        assertThat(data).hasSize(parts + 2);
        String content = data.subList(0, parts).stream()
                .map(d -> d.replaceAll(".*\"content\":\"([^\"]*)\".*", "$1"))
                .collect(Collectors.joining());
        assertThat(content).isEqualTo(String.join("", FakeOpenAi.CONTENT_PARTS));
        assertThat(data.get(parts)).contains("\"usage\":{\"prompt_tokens\":" + FakeOpenAi.PROMPT_TOKENS
                + ",\"completion_tokens\":" + FakeOpenAi.COMPLETION_TOKENS);
        assertThat(data.get(parts + 1)).isEqualTo("[DONE]");
    }

    @Test
    void canFailBeforeFirstByte() throws Exception {
        fake.setMode(FakeOpenAi.Mode.FAIL_BEFORE_FIRST_BYTE);

        HttpResponse<java.io.InputStream> res = post();

        assertThat(res.statusCode()).isEqualTo(500);
        assertThat(res.headers().firstValue("Content-Type")).contains("application/json");
        assertThat(readDataLines(res)).isEmpty();
    }

    @Test
    void canStopHalfway() throws Exception {
        fake.setMode(FakeOpenAi.Mode.STOP_HALFWAY);

        HttpResponse<java.io.InputStream> res = post();
        assertThat(res.statusCode()).isEqualTo(200);

        List<String> data = new ArrayList<>();
        assertThatThrownBy(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(res.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        data.add(line.substring(6));
                    }
                }
            }
        }).isInstanceOf(IOException.class);

        assertThat(data).isNotEmpty().hasSizeLessThan(FakeOpenAi.CONTENT_PARTS.size() + 1);
        assertThat(data).doesNotContain("[DONE]");
        assertThat(data).noneMatch(d -> d.contains("\"usage\""));
    }

    @Test
    void countsRequests() throws Exception {
        readDataLines(post());
        readDataLines(post());
        assertThat(fake.requestCount()).isEqualTo(2);
    }
}
