package yardmaster;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Small helper for calling the running gateway in integration tests. */
final class HttpChecks {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private HttpChecks() {
    }

    static HttpResponse<String> get(int port, String path) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
