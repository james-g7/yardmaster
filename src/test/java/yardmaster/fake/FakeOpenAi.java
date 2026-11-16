package yardmaster.fake;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Stand-in for the OpenAI API in tests, so no test ever calls a real provider.
 * Serves {@code POST /v1/chat/completions} as an SSE stream. Request bodies are
 * read and discarded, never stored or logged.
 */
public final class FakeOpenAi implements AutoCloseable {

    /** How the next requests behave. */
    public enum Mode {
        /** Several chunks, a final chunk with usage, then [DONE]. */
        OK,
        /** Respond 500 before sending any body byte. */
        FAIL_BEFORE_FIRST_BYTE,
        /** Send the first chunks, then cut the connection. No usage, no [DONE]. */
        STOP_HALFWAY
    }

    public static final List<String> CONTENT_PARTS = List.of("Hello", ", ", "from ", "the fake.");
    public static final int PROMPT_TOKENS = 11;
    public static final int COMPLETION_TOKENS = 7;

    private final HttpServer server;
    private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.OK);
    private final AtomicInteger requests = new AtomicInteger();

    public FakeOpenAi() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start fake OpenAI server", e);
        }
        server.createContext("/v1/chat/completions", this::handle);
        server.start();
    }

    /** Value for {@code yardmaster.providers.openai.base-url}. */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void setMode(Mode newMode) {
        mode.set(newMode);
    }

    public int requestCount() {
        return requests.get();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            handleRequest(ex);
        } catch (IOException e) {
            // Expected when STOP_HALFWAY closes a body shorter than promised.
            if (mode.get() != Mode.STOP_HALFWAY) {
                throw e;
            }
        } finally {
            try {
                ex.close();
            } catch (RuntimeException ignored) {
                // connection already dropped
            }
        }
    }

    private void handleRequest(HttpExchange ex) throws IOException {
        {
            ex.getRequestBody().readAllBytes();
            if (!"POST".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                return;
            }
            requests.incrementAndGet();
            Mode current = mode.get();
            if (current == Mode.FAIL_BEFORE_FIRST_BYTE) {
                byte[] body = "{\"error\":{\"message\":\"fake failure\"}}".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "application/json");
                ex.sendResponseHeaders(500, body.length);
                ex.getResponseBody().write(body);
                return;
            }
            ex.getResponseHeaders().set("Content-Type", "text/event-stream");
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            // STOP_HALFWAY promises more bytes than it sends, so the client sees a truncated body.
            ex.sendResponseHeaders(200, current == Mode.STOP_HALFWAY ? 1_000_000 : 0);
            OutputStream out = ex.getResponseBody();
            int half = CONTENT_PARTS.size() / 2;
            for (int i = 0; i < CONTENT_PARTS.size(); i++) {
                if (current == Mode.STOP_HALFWAY && i == half) {
                    return; // try-with-resources closes the short body; the connection is dropped
                }
                send(out, chunk(CONTENT_PARTS.get(i), null));
            }
            send(out, "{\"id\":\"chatcmpl-fake\",\"object\":\"chat.completion.chunk\",\"choices\":[],"
                    + "\"usage\":{\"prompt_tokens\":" + PROMPT_TOKENS + ",\"completion_tokens\":" + COMPLETION_TOKENS
                    + ",\"total_tokens\":" + (PROMPT_TOKENS + COMPLETION_TOKENS) + "}}");
            send(out, "[DONE]");
        }
    }

    private static String chunk(String content, String finish) {
        return "{\"id\":\"chatcmpl-fake\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,"
                + "\"delta\":{\"content\":\"" + content + "\"},\"finish_reason\":"
                + (finish == null ? "null" : "\"" + finish + "\"") + "}]}";
    }

    private static void send(OutputStream out, String data) throws IOException {
        out.write(("data: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
