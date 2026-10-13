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
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/** Local, fictional lab only. Run with Java 17 source-file mode; no extra libraries. */
class VerifyHttpsLab {
    private static final String COOKIE = "__Host-LABSESSION_HTTPS";
    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");
    private final URI base;
    private final SSLContext tls;
    private int passed;

    private VerifyHttpsLab(URI base, SSLContext tls) {
        this.base = base;
        this.tls = tls;
    }

    public static void main(String[] args) throws Exception {
        Path certificatePath = Path.of(args.length > 0 ? args[0] : ".local/tls/localhost.cer");
        URI base = URI.create(args.length > 1 ? args[1] : "https://127.0.0.1:8443");
        if (!"https".equals(base.getScheme()) ||
                !("127.0.0.1".equals(base.getHost()) || "localhost".equals(base.getHost())) ||
                base.getRawUserInfo() != null || base.getRawQuery() != null || base.getRawFragment() != null ||
                !(base.getPath().isEmpty() || "/".equals(base.getPath()))) {
            throw new IllegalArgumentException("Use only this local lab: https://127.0.0.1:<port> or https://localhost:<port>");
        }
        X509Certificate certificate;
        try (var input = Files.newInputStream(certificatePath)) {
            certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
        certificate.checkValidity();
        var trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("local-session-lab", certificate);
        var trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trustStore);
        var tls = SSLContext.getInstance("TLS");
        tls.init(null, trustManagers.getTrustManagers(), null);
        // The JVM still verifies certificate signatures and hostname. No trust-all manager.
        new VerifyHttpsLab(base, tls).run();
    }

    private void run() throws Exception {
        System.out.println("HTTPS session checks against " + base + " (no session IDs or passwords printed)");
        var alice = browser();
        var loginPage = get(alice, "/login");
        require(loginPage.statusCode() == 200, "HTTPS login page responds");
        String protocol = loginPage.sslSession().orElseThrow().getProtocol();
        require(protocol.equals("TLSv1.2") || protocol.equals("TLSv1.3"), "Verified TLS connection: " + protocol);
        String header = loginPage.headers().allValues("Set-Cookie").stream()
            .filter(value -> value.startsWith(COOKIE + "=")).findFirst().orElseThrow();
        require(header.contains("; Secure") && header.contains("; HttpOnly") &&
            header.contains("SameSite=Lax") && header.contains("Path=/") &&
            !header.toLowerCase().contains("domain="), "Secure, HttpOnly, SameSite=Lax, host-only Path=/ cookie");
        require(loginPage.headers().firstValue("Strict-Transport-Security").isPresent(), "HSTS response header present");
        require(get(alice, "/js/app.js").statusCode() == 200, "Public UI script remains accessible");
        assertLoginRedirect(get(alice, "/me"), "Anonymous client cannot access private page");
        require(post(alice, "/login", Map.of("username", "alice", "password", "AliceLab!2026")).statusCode() == 403,
            "Login without CSRF token rejected");

        String before = sessionId(alice);
        var oldAnonymous = copiedCookie(before);
        require(post(alice, "/login", Map.of("username", "alice", "password", "AliceLab!2026",
            "_csrf", csrf(get(alice, "/login").body()))).statusCode() == 302, "Alice authenticates over HTTPS");
        String authenticated = sessionId(alice);
        require(!before.equals(authenticated), "Session ID rotates at login");
        assertIdentity(alice, "alice", "Authenticated client sees Alice");
        assertLoginRedirect(get(oldAnonymous, "/me"), "Old anonymous session does not authenticate");
        var copied = copiedCookie(authenticated);
        assertIdentity(copied, "alice", "Copied live HTTPS cookie still identifies Alice (known limitation)");
        var wrongName = client();
        assertLoginRedirect(sendGet(wrongName, "/me", "JSESSIONID=" + authenticated),
            "HTTP cookie name is not accepted as the HTTPS session");
        var notes = get(alice, "/notes?username=bob");
        // Unicode escapes keep Java 17 source-file mode portable across Windows encodings.
        require(notes.statusCode() == 200 && notes.body().contains("Ghi ch\u00fa ri\u00eang c\u1ee7a Alice") &&
            !notes.body().contains("Ghi ch\u00fa ri\u00eang c\u1ee7a Bob"), "Notes remain scoped to the authenticated principal");
        require(post(alice, "/logout", Map.of()).statusCode() == 403, "Logout without CSRF token rejected");
        assertIdentity(alice, "alice", "Rejected logout leaves the session usable");
        var logout = post(alice, "/logout", Map.of("_csrf", csrf(get(alice, "/me").body())));
        require(logout.statusCode() == 302 && logout.headers().firstValue("Location").orElseThrow().endsWith("/login?logout"),
            "Valid POST logout succeeds");
        System.out.println("Logout cookie attributes: " + logout.headers().allValues("Set-Cookie").stream()
            .map(value -> value.replaceFirst("=[^;]*", "=<redacted>")).toList());
        // A past Expires date also removes cookies; this Tomcat version uses it without Max-Age.
        require(logout.headers().allValues("Set-Cookie").stream()
            .flatMap(value -> HttpCookie.parse(value).stream()).anyMatch(cookie ->
                cookie.getName().equals(COOKIE) && cookie.getMaxAge() == 0 &&
                cookie.getSecure() && "/".equals(cookie.getPath())),
            "Logout expires the configured HTTPS cookie");
        assertLoginRedirect(get(alice, "/me"), "Original client loses access after logout");
        assertLoginRedirect(get(copied, "/me"), "Copied cookie loses access after server-side logout");

        var idle = browser();
        require(post(idle, "/login", Map.of("username", "bob", "password", "BobLab!2026",
            "_csrf", csrf(get(idle, "/login").body()))).statusCode() == 302, "Bob authenticates for timeout check");
        assertIdentity(idle, "bob", "Timeout session starts authenticated");
        var idleCopy = copiedCookie(sessionId(idle));
        System.out.println("Idle for 75 seconds. No requests from either timeout client during this wait.");
        for (int elapsed = 0; elapsed < 75; elapsed += 15) {
            Thread.sleep(15_000);
            System.out.println("Idle: " + (elapsed + 15) + "/75 seconds");
        }
        assertLoginRedirect(get(idle, "/me"), "Original session expires after inactivity");
        assertLoginRedirect(get(idleCopy, "/me"), "Copied cookie cannot revive the expired session");
        System.out.println("RESULT: " + passed + " checks passed. Wireshark and browser cookie policies are separate checks.");
    }

    private HttpClient client() {
        return HttpClient.newBuilder().sslContext(tls).connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    private Browser browser() {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var http = HttpClient.newBuilder().sslContext(tls).cookieHandler(cookies)
            .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
        return new Browser(http, cookies, null);
    }

    private Browser copiedCookie(String sessionId) {
        return new Browser(client(), null, COOKIE + "=" + sessionId);
    }

    private String sessionId(Browser browser) {
        return browser.cookies().getCookieStore().getCookies().stream()
            .filter(cookie -> cookie.getName().equals(COOKIE)).map(cookie -> cookie.getValue())
            .findFirst().orElseThrow();
    }

    private HttpResponse<String> get(Browser browser, String path) throws Exception {
        return sendGet(browser.http(), path, browser.fixedCookie());
    }

    private HttpResponse<String> sendGet(HttpClient http, String path, String cookie) throws Exception {
        var request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10));
        if (cookie != null) request.header("Cookie", cookie);
        return http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(Browser browser, String path, Map<String, String> fields) throws Exception {
        String body = fields.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
            .collect(Collectors.joining("&"));
        var request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return browser.http().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void assertIdentity(Browser browser, String name, String message) throws Exception {
        var response = get(browser, "/me");
        require(response.statusCode() == 200 && response.body().contains("<dd id=\"current-user\">" + name + "</dd>"), message);
    }

    private void assertLoginRedirect(HttpResponse<String> response, String message) {
        require(response.statusCode() == 302 &&
            URI.create(response.headers().firstValue("Location").orElseThrow()).getPath().equals("/login"), message);
    }

    private String csrf(String html) {
        var match = CSRF.matcher(html);
        if (!match.find()) throw new IllegalStateException("CSRF hidden input missing");
        return match.group(1);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("FAIL: " + message);
        passed++;
        System.out.println("PASS: " + message);
    }

    private record Browser(HttpClient http, CookieManager cookies, String fixedCookie) { }
}
