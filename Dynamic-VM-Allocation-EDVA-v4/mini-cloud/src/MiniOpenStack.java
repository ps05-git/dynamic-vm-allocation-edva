import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Windows-only educational OpenStack-like control plane.
 * IMPORTANT: instances/hosts are simulated; submitted jobs run as real Windows processes.
 * This is not an OpenStack deployment and does not create hypervisor-backed VMs.
 */
public class MiniOpenStack {
    private static final String DEMO_TOKEN = "edva-demo-token-change-me";
    private static final AtomicInteger instanceIds = new AtomicInteger(0);
    private static final AtomicInteger jobIds = new AtomicInteger(0);
    private static final Map<Integer, Instance> instances = new ConcurrentHashMap<>();
    private static final Map<Integer, CloudJob> jobs = new ConcurrentHashMap<>();
    private static final ExecutorService workers = Executors.newCachedThreadPool();

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/health", x -> respond(x, 200, "{\"status\":\"ok\",\"service\":\"MiniOpenStack\"}"));
        server.createContext("/v1/auth/tokens", MiniOpenStack::auth);
        server.createContext("/v1/instances", MiniOpenStack::instancesApi);
        server.createContext("/v1/jobs", MiniOpenStack::jobsApi);
        server.createContext("/v1/admin/reset", MiniOpenStack::resetApi);
        server.setExecutor(workers);
        server.start();
        System.out.println("Mini OpenStack-like API running at http://127.0.0.1:" + port);
        System.out.println("Educational mode: instances are simulated; jobs execute as real Windows processes.");
        System.out.println("Demo token: " + DEMO_TOKEN);
        System.out.println("Press Ctrl+C to stop.");
    }

    private static void resetApi(HttpExchange x) throws IOException {
        if (!authorized(x)) return;
        if (!"POST".equals(x.getRequestMethod())) { respond(x, 405, error("Use POST")); return; }
        // Test/demo endpoint: clears in-memory records, not running processes.
        jobs.clear();
        instances.clear();
        jobIds.set(0);
        instanceIds.set(0);
        respond(x, 200, "{\"reset\":true,\"note\":\"in-memory records cleared; does not terminate running processes\"}");
    }

    private static void auth(HttpExchange x) throws IOException {
        if (!"POST".equals(x.getRequestMethod())) { respond(x, 405, error("Use POST")); return; }
        respond(x, 201, "{\"token\":\"" + DEMO_TOKEN + "\",\"expires_at\":\"" + Instant.now().plusSeconds(3600) + "\"}");
    }

    private static boolean authorized(HttpExchange x) throws IOException {
        if (DEMO_TOKEN.equals(x.getRequestHeaders().getFirst("X-Auth-Token"))) return true;
        respond(x, 401, error("Missing or invalid X-Auth-Token. POST /v1/auth/tokens to obtain demo token."));
        return false;
    }

    private static void instancesApi(HttpExchange x) throws IOException {
        if (!authorized(x)) return;
        String path = x.getRequestURI().getPath();
        String method = x.getRequestMethod();
        if ("/v1/instances".equals(path) && "GET".equals(method)) {
            StringJoiner out = new StringJoiner(",", "{\"instances\":[", "]}");
            instances.values().stream().sorted(Comparator.comparingInt(i -> i.id)).forEach(i -> out.add(i.json()));
            respond(x, 200, out.toString()); return;
        }
        if ("/v1/instances".equals(path) && "POST".equals(method)) {
            Map<String,String> q = query(x);
            String name = q.getOrDefault("name", "edva-instance-" + (instanceIds.get()+1));
            String host = q.getOrDefault("host", "host-1");
            int id = instanceIds.incrementAndGet();
            Instance i = new Instance(id, name, host);
            instances.put(id, i);
            respond(x, 201, i.json()); return;
        }
        String[] parts = path.split("/");
        if (parts.length == 5) {
            int id = parseId(parts[3]);
            Instance i = instances.get(id);
            if (i == null) { respond(x, 404, error("Instance not found")); return; }
            String action = parts[4];
            if ("POST".equals(method) && "start".equals(action)) { i.state = "ACTIVE"; i.updated = Instant.now().toString(); respond(x, 200, i.json()); return; }
            if ("POST".equals(method) && "stop".equals(action)) { i.state = "STOPPED"; i.updated = Instant.now().toString(); respond(x, 200, i.json()); return; }
            if ("DELETE".equals(method) && "delete".equals(action)) { instances.remove(id); respond(x, 200, "{\"deleted\":true,\"id\":" + id + "}"); return; }
        }
        if (parts.length == 4 && "DELETE".equals(method)) {
            int id = parseId(parts[3]);
            if (instances.remove(id) != null) { respond(x, 200, "{\"deleted\":true,\"id\":" + id + "}"); }
            else respond(x, 404, error("Instance not found"));
            return;
        }
        respond(x, 404, error("Unknown instance endpoint"));
    }

    private static void jobsApi(HttpExchange x) throws IOException {
        if (!authorized(x)) return;
        String path = x.getRequestURI().getPath();
        if ("/v1/jobs".equals(path) && "GET".equals(x.getRequestMethod())) {
            StringJoiner out = new StringJoiner(",", "{\"jobs\":[", "]}");
            jobs.values().stream().sorted(Comparator.comparingInt(j -> j.id)).forEach(j -> out.add(j.json()));
            respond(x, 200, out.toString()); return;
        }
        if ("/v1/jobs".equals(path) && "POST".equals(x.getRequestMethod())) {
            Map<String,String> q = query(x);
            int instanceId;
            int seconds;
            try { instanceId = Integer.parseInt(q.getOrDefault("instance_id", "-1")); seconds = Integer.parseInt(q.getOrDefault("seconds", "2")); }
            catch (NumberFormatException e) { respond(x, 400, error("instance_id and seconds must be integers")); return; }
            if (!instances.containsKey(instanceId)) { respond(x, 400, error("Create an instance first and pass its instance_id")); return; }
            if (seconds < 1 || seconds > 30) { respond(x, 400, error("seconds must be between 1 and 30")); return; }
            int id = jobIds.incrementAndGet();
            CloudJob job = new CloudJob(id, instanceId, seconds);
            jobs.put(id, job);
            workers.submit(() -> runWindowsProcess(job));
            respond(x, 202, job.json()); return;
        }
        respond(x, 404, error("Unknown jobs endpoint"));
    }

    private static void runWindowsProcess(CloudJob job) {
        job.state = "RUNNING"; job.started = Instant.now().toString();
        try {
            // Fixed command and bounded integer argument; no user-provided shell text is executed.
            Process p = new ProcessBuilder("cmd.exe", "/c", "ping", "-n", String.valueOf(job.seconds + 1), "127.0.0.1").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).replace("\"", "'").trim();
            int exit = p.waitFor();
            job.exitCode = exit; job.output = output; job.state = exit == 0 ? "SUCCEEDED" : "FAILED";
        } catch (Exception e) { job.state = "FAILED"; job.output = e.getClass().getSimpleName() + ": " + e.getMessage(); }
        job.finished = Instant.now().toString();
    }

    private static Map<String,String> query(HttpExchange x) {
        Map<String,String> m = new HashMap<>(); String raw = x.getRequestURI().getRawQuery();
        if (raw == null) return m;
        for (String pair : raw.split("&")) { String[] kv = pair.split("=", 2); m.put(decode(kv[0]), kv.length > 1 ? decode(kv[1]) : ""); }
        return m;
    }
    private static String decode(String s) { return URLDecoder.decode(s, StandardCharsets.UTF_8); }
    private static int parseId(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return -1; } }
    private static String error(String message) { return "{\"error\":\"" + esc(message) + "\"}"; }
    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': b.append("\\\\"); break;
                case '"': b.append("\\\""); break;
                case '\b': b.append("\\b"); break;
                case '\f': b.append("\\f"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int)c));
                    else b.append(c);
            }
        }
        return b.toString();
    }
    private static void respond(HttpExchange x, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        x.getResponseHeaders().set("Access-Control-Allow-Origin", "http://127.0.0.1");
        x.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-Auth-Token");
        x.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        if ("OPTIONS".equals(x.getRequestMethod())) { x.sendResponseHeaders(204, -1); return; }
        x.sendResponseHeaders(status, bytes.length); x.getResponseBody().write(bytes); x.close();
    }

    private static class Instance {
        final int id; final String name; final String host; final String created = Instant.now().toString();
        volatile String state = "BUILD"; volatile String updated = created;
        Instance(int id, String name, String host) { this.id=id; this.name=name; this.host=host; }
        String json() { return "{\"id\":"+id+",\"name\":\""+esc(name)+"\",\"host\":\""+esc(host)+"\",\"state\":\""+state+"\",\"created_at\":\""+created+"\",\"updated_at\":\""+updated+"\"}"; }
    }
    private static class CloudJob {
        final int id, instanceId, seconds; final String submitted = Instant.now().toString();
        volatile String state="QUEUED", started="", finished="", output=""; volatile int exitCode=-1;
        CloudJob(int id,int instanceId,int seconds) { this.id=id; this.instanceId=instanceId; this.seconds=seconds; }
        String json() { return "{\"id\":"+id+",\"instance_id\":"+instanceId+",\"duration_seconds\":"+seconds+",\"state\":\""+state+"\",\"exit_code\":"+exitCode+",\"submitted_at\":\""+submitted+"\",\"started_at\":\""+started+"\",\"finished_at\":\""+finished+"\",\"output\":\""+esc(output)+"\"}"; }
    }
}
