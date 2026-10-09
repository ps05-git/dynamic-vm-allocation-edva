import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Client for the local Mini OpenStack-like API. It does not connect to real OpenStack. */
public final class MiniCloudClient {
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final String token;

    public MiniCloudClient(String baseUrl) throws Exception {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        String response = request("POST", "/v1/auth/tokens", null, false);
        this.token = stringField(response, "token");
    }

    public void resetDemoState() throws Exception {
        request("POST", "/v1/admin/reset", null, true);
    }

    public int createActiveInstance(String name, String host) throws Exception {
        String path = "/v1/instances?name=" + enc(name) + "&host=" + enc(host);
        String created = request("POST", path, null, true);
        int id = intField(created, "id");
        request("POST", "/v1/instances/" + id + "/start", null, true);
        return id;
    }

    public int submitJob(int instanceId, int seconds) throws Exception {
        int bounded = Math.max(1, Math.min(30, seconds));
        String response = request("POST", "/v1/jobs?instance_id=" + instanceId + "&seconds=" + bounded, null, true);
        return intField(response, "id");
    }

    public String getJobsJson() throws Exception {
        return request("GET", "/v1/jobs", null, true);
    }

    private String request(String method, String path, String body, boolean auth) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(10));
        if (auth) b.header("X-Auth-Token", token);
        if (body == null) b.method(method, HttpRequest.BodyPublishers.noBody());
        else b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> response = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(method + " " + path + " returned HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private static String stringField(String json, String name) throws IOException {
        Matcher m = Pattern.compile("\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(json);
        if (!m.find()) throw new IOException("Missing field '" + name + "' in API response: " + json);
        return m.group(1);
    }

    private static int intField(String json, String name) throws IOException {
        Matcher m = Pattern.compile("\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*(\\d+)").matcher(json);
        if (!m.find()) throw new IOException("Missing numeric field '" + name + "' in API response: " + json);
        return Integer.parseInt(m.group(1));
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
