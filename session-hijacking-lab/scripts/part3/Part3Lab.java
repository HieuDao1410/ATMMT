import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/** Traffic generator for this project's loopback lab only. No external targets. */
class Part3Lab {
    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");
    private final Path privateDirectory;
    private final Path evidenceDirectory;
    private final URI base;
    private final String cookieName;
    private final SSLContext tls;

    private Part3Lab(Path privateDirectory, Path evidenceDirectory, boolean https, Path certificate) throws Exception {
        this.privateDirectory = privateDirectory;
        this.evidenceDirectory = evidenceDirectory;
        base = URI.create(https ? "https://127.0.0.1:8443" : "http://127.0.0.1:8080");
        cookieName = https ? "__Host-LABSESSION_HTTPS" : "JSESSIONID";
        tls = https ? trustedTls(certificate) : SSLContext.getDefault();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) throw new IllegalArgumentException("Mode, private directory and evidence directory required");
        String mode = args[0];
        boolean https = mode.equals("https");
        if (https && args.length < 4) throw new IllegalArgumentException("HTTPS requires the local public certificate path");
        var lab = new Part3Lab(Path.of(args[1]), Path.of(args[2]), https,
            https ? Path.of(args[3]) : null);
        Files.createDirectories(lab.privateDirectory);
        Files.createDirectories(lab.evidenceDirectory);
        switch (mode) {
            case "seed-http" -> lab.seedHttp();
            case "replay-http" -> lab.replayHttp();
            case "https" -> lab.https();
            default -> throw new IllegalArgumentException("Unknown lab phase");
        }
    }

    private void seedHttp() throws Exception {
        var alice = browser();
        var anonymousB = browser();
        check(redirectsToLogin(get(anonymousB, "/me")), "T01", "B without a session: GET /me -> 302 /login", "HTTP client B");
        String marker = UUID.randomUUID().toString();
        var loginPage = get(alice, "/login");
        require(loginPage.statusCode() == 200, "HTTP login unavailable");
        String before = sessionId(alice);
        login(alice, "alice", "AliceLab!2026", csrf(loginPage.body()));
        String after = sessionId(alice);
        check(!before.equals(after), "T09-HTTP", "HTTP session ID rotated at login", "session-fingerprints.tsv");
        fingerprint("HTTP", before, after);
        require(identity(get(alice, "/me?lab_run=" + marker + "&stage=seed"), "alice"), "HTTP A identity mismatch");
        require(get(alice, "/notes?lab_run=" + marker + "&stage=seed").statusCode() == 200, "HTTP notes unavailable");
        var state = new Properties();
        state.setProperty("marker", marker);
        state.setProperty("session", after);
        try (var output = Files.newOutputStream(privateDirectory.resolve("http-state.properties"))) {
            state.store(output, "PRIVATE local lab state. Never commit this file.");
        }
        System.out.println("HTTP A seeded; session value kept private. Marker: " + marker);
    }

    private void replayHttp() throws Exception {
        var state = new Properties();
        try (var input = Files.newInputStream(privateDirectory.resolve("http-state.properties"))) { state.load(input); }
        // B's credential comes from TShark's packet extraction, not A's cookie jar.
        String captured = Files.readString(privateDirectory.resolve("captured-http-cookie.txt"), StandardCharsets.UTF_8).trim();
        require(captured.matches("[A-Za-z0-9._-]{16,256}"), "Captured cookie has an unexpected format");
        require(captured.equals(state.getProperty("session")), "Wire cookie does not match the seeded session");
        var b = copied(captured);
        check(identity(get(b, "/me?lab_run=" + state.getProperty("marker") + "&stage=replay"), "alice"),
            "T02", "B replays the cookie extracted from the HTTP pcap: /me identifies alice", "http-seed.pcap -> captured-http-cookie.txt -> http-replay.pcap");
        require(get(b, "/notes?lab_run=" + state.getProperty("marker") + "&stage=replay").statusCode() == 200,
            "Replayed HTTP session cannot read notes");
        var a = copied(state.getProperty("session"));
        var logout = post(a, "/logout", Map.of("_csrf", csrf(get(a, "/me").body())));
        require(logout.statusCode() == 302, "HTTP logout failed");
        check(redirectsToLogin(get(b, "/me")), "HTTP-LOGOUT", "Captured HTTP cookie loses access after A logs out", "HTTP client B");
    }

    private void https() throws Exception {
        var alice = browser();
        var page = get(alice, "/login");
        require(page.statusCode() == 200, "HTTPS login unavailable");
        String protocol = page.sslSession().orElseThrow().getProtocol();
        require(protocol.equals("TLSv1.3") || protocol.equals("TLSv1.2"), "Unexpected TLS protocol");
        String header = page.headers().allValues("Set-Cookie").stream()
            .filter(value -> value.startsWith(cookieName + "=")).findFirst().orElseThrow();
        check(header.contains("; Secure") && header.contains("; HttpOnly") && header.contains("SameSite=Lax") &&
                header.contains("Path=/") && !header.toLowerCase().contains("domain="), "T05",
            cookieName + "; Secure; HttpOnly; SameSite=Lax; Path=/; no Domain", "HTTPS response inspected by verified TLS client; not a DevTools screenshot");
        String before = sessionId(alice);
        login(alice, "alice", "AliceLab!2026", csrf(page.body()));
        String session = sessionId(alice);
        check(!before.equals(session), "T09-HTTPS", "HTTPS session ID rotated at login", "session-fingerprints.tsv");
        fingerprint("HTTPS", before, session);
        require(identity(get(alice, "/me"), "alice"), "HTTPS A identity mismatch");
        require(redirectsToLogin(get(browser(), "/me")), "HTTPS B should start anonymous");
        var b = copied(session);
        check(identity(get(b, "/me"), "alice"), "T06", "Assumed-exposed HTTPS cookie still identifies B as alice", "Manual-copy simulation; cookie known at the client endpoint, not extracted from TLS pcap");
        var state = new Properties();
        state.setProperty("session", session);
        state.setProperty("protocol", protocol);
        try (var output = Files.newOutputStream(privateDirectory.resolve("https-state.properties"))) {
            state.store(output, "PRIVATE state used only to check that the cookie is absent from raw TLS bytes.");
        }
        var logout = post(alice, "/logout", Map.of("_csrf", csrf(get(alice, "/me").body())));
        require(logout.statusCode() == 302, "HTTPS logout failed");
        require(logout.headers().allValues("Set-Cookie").stream().flatMap(value -> HttpCookie.parse(value).stream())
            .anyMatch(cookie -> cookie.getName().equals(cookieName) && cookie.getMaxAge() == 0 && cookie.getSecure()),
            "HTTPS logout did not expire its secure cookie");
        check(redirectsToLogin(get(alice, "/me")) && redirectsToLogin(get(b, "/me")), "T07",
            "After A logout, both A and B with the old HTTPS cookie redirect to login", "Server-side session invalidation");

        var bob = browser();
        login(bob, "bob", "BobLab!2026", csrf(get(bob, "/login").body()));
        require(identity(get(bob, "/me"), "bob"), "Timeout session should start as bob");
        var idleCopy = copied(sessionId(bob));
        long start = System.nanoTime();
        for (int elapsed = 0; elapsed < 75; elapsed += 15) {
            Thread.sleep(15_000);
            System.out.println("Idle timeout: " + (elapsed + 15) + "/75 seconds; no requests from either timeout client");
        }
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        check(elapsedMillis >= 75_000 && redirectsToLogin(get(bob, "/me")) && redirectsToLogin(get(idleCopy, "/me")),
            "T08", "Both timeout clients redirect to login after " + elapsedMillis + " ms of inactivity", "HTTPS idle timeout configured as 60 seconds");
        Files.writeString(evidenceDirectory.resolve("tls-protocol.txt"), protocol + "\n", StandardCharsets.UTF_8);
    }

    private SSLContext trustedTls(Path certificatePath) throws Exception {
        X509Certificate certificate;
        try (var input = Files.newInputStream(certificatePath)) {
            certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
        certificate.checkValidity();
        var store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);
        store.setCertificateEntry("local-session-lab", certificate);
        var managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init(store);
        var context = SSLContext.getInstance("TLS");
        context.init(null, managers.getTrustManagers(), null);
        return context;
    }

    private Browser browser() {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        return new Browser(HttpClient.newBuilder().sslContext(tls).cookieHandler(cookies)
            .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build(), cookies, null);
    }

    private Browser copied(String value) {
        return new Browser(HttpClient.newBuilder().sslContext(tls).connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build(), null, cookieName + "=" + value);
    }

    private HttpResponse<String> get(Browser browser, String path) throws Exception {
        var request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10));
        if (browser.fixedCookie() != null) request.header("Cookie", browser.fixedCookie());
        return browser.http().send(request.GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(Browser browser, String path, Map<String, String> fields) throws Exception {
        String body = fields.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
            .collect(Collectors.joining("&"));
        var request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/x-www-form-urlencoded");
        if (browser.fixedCookie() != null) request.header("Cookie", browser.fixedCookie());
        return browser.http().send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void login(Browser browser, String username, String password, String token) throws Exception {
        require(post(browser, "/login", Map.of("username", username, "password", password, "_csrf", token)).statusCode() == 302,
            "Lab authentication failed");
    }

    private String sessionId(Browser browser) {
        return browser.cookies().getCookieStore().getCookies().stream()
            .filter(cookie -> cookie.getName().equals(cookieName)).map(HttpCookie::getValue).findFirst().orElseThrow();
    }

    private String csrf(String html) {
        var match = CSRF.matcher(html);
        require(match.find(), "CSRF hidden input missing");
        return match.group(1);
    }

    private boolean identity(HttpResponse<String> response, String username) {
        return response.statusCode() == 200 && response.body().contains("<dd id=\"current-user\">" + username + "</dd>");
    }

    private boolean redirectsToLogin(HttpResponse<String> response) {
        return response.statusCode() == 302 && response.headers().firstValue("Location")
            .map(value -> URI.create(value).getPath().equals("/login")).orElse(false);
    }

    private void fingerprint(String transport, String before, String after) throws Exception {
        String row = transport + "\t" + digest(before) + "\t" + digest(after) + "\n";
        Files.writeString(evidenceDirectory.resolve("session-fingerprints.tsv"), row, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private String digest(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
    }

    private void check(boolean passed, String id, String observation, String evidence) throws Exception {
        String status = passed ? "PASS" : "FAIL";
        String timestamp = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime().toString();
        Files.writeString(evidenceDirectory.resolve("events.tsv"), String.join("\t", id, status, timestamp, observation, evidence) + "\n",
            StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        System.out.println(status + " " + id + ": " + observation);
        require(passed, "Lab case failed: " + id);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private record Browser(HttpClient http, CookieManager cookies, String fixedCookie) { }
}
