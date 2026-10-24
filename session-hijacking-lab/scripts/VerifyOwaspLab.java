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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/** Fictional localhost lab only. Raw cookies/tokens stay in the ignored .local directory. */
class VerifyOwaspLab {
    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");
    private static final String PASSWORD = "AliceLab!2026";
    private static final String WRONG = "OWASP-WRONG-PASSWORD-MARKER";
    private static final String MARKER = "OWASP-PRIVATE-QUERY-MARKER";
    private static final Set<String> secrets = new LinkedHashSet<>();
    private static final List<String> results = new ArrayList<>();
    private final String variant;
    private final URI base;
    private final SSLContext tls;
    private final String cookieName;
    private Browser authenticated;
    private long blockedAt;

    private VerifyOwaspLab(String variant, URI base, SSLContext tls, String cookieName) {
        this.variant = variant; this.base = base; this.tls = tls; this.cookieName = cookieName;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Use: java scripts/VerifyOwaspLab.java <localhost.cer> <.local/evidence-directory>");
        Path privateDirectory = Path.of(args[1]).toAbsolutePath().normalize();
        Path localRoot = Path.of(".local").toAbsolutePath().normalize();
        if (!privateDirectory.startsWith(localRoot)) throw new IllegalArgumentException("Keep raw verification artifacts under .local");
        Files.createDirectories(privateDirectory);
        Path resultFile = privateDirectory.resolve("owasp-checks.tsv");
        if (Files.exists(resultFile)) throw new IllegalArgumentException("Use a new verification directory");
        secrets.addAll(List.of("alice", "bob", PASSWORD, "BobLab!2026", WRONG, MARKER, "198.51.100.77"));
        results.add("variant\tcheck\tstatus\ttimestamp\tobservation");
        try {
            X509Certificate certificate;
            try (var input = Files.newInputStream(Path.of(args[0]))) {
                certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
            }
            certificate.checkValidity();
            var trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            trustStore.setCertificateEntry("local-session-lab", certificate);
            var managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            managers.init(trustStore);
            var tls = SSLContext.getInstance("TLS"); tls.init(null, managers.getTrustManagers(), null);
            var http = new VerifyOwaspLab("HTTP", URI.create("http://127.0.0.1:8080"), tls, "JSESSIONID");
            var https = new VerifyOwaspLab("HTTPS", URI.create("https://127.0.0.1:8443"), tls, "__Host-LABSESSION_HTTPS");
            http.prepare();
            // HTTPS must still accept the same account while the HTTP account is blocked.
            https.prepare();
            https.check("O13", http.login(http.browser(), "alice", PASSWORD).statusCode() == 429,
                "HTTPS authenticated while the HTTP account remains blocked");
            System.out.println("Waiting for the real 60-second block; app configuration is unchanged.");
            Thread.sleep(30_000);
            http.checkRetryDoesNotRestartBlock(); https.checkRetryDoesNotRestartBlock();
            long elapsed = (System.nanoTime() - https.blockedAt) / 1_000_000L;
            long remaining = Math.max(0, 61_500 - elapsed);
            while (remaining > 0) {
                long step = Math.min(15_000, remaining);
                Thread.sleep(step); remaining -= step;
                System.out.println("Block expiry wait in progress; no session tokens printed.");
            }
            http.finish(); https.finish();
            System.out.println("RESULT: " + (results.size() - 1) + " OWASP runtime checks passed.");
        } finally {
            Files.write(resultFile, results, StandardCharsets.UTF_8);
            // Used only for a local exact-value scan of Docker audit logs; never publish this file.
            Files.write(privateDirectory.resolve("sensitive-values.txt"), secrets, StandardCharsets.UTF_8);
        }
    }

    private void prepare() throws Exception {
        authenticated = browser();
        var page = get(authenticated, "/login");
        check("O01", page.statusCode() == 200 && (variant.equals("HTTP") || page.sslSession().isPresent()), "Login page accessible; HTTPS validates certificate and hostname");
        var login = login(authenticated, "alice", PASSWORD);
        check("O02", isRedirect(login, "/me") && isAlice(get(authenticated, "/me")), "Valid credentials authenticate before throttling");
        var copy = copiedCookie(sessionId(authenticated));
        var missing = browser(); get(missing, "/login");
        check("O03", post(missing, "/login", Map.of("username", "alice", "password", WRONG)).statusCode() == 403, "Missing CSRF is rejected before password failure accounting");
        for (int i = 1; i <= 5; i++) {
            var attempt = browser();
            var response = login(attempt, i % 2 == 0 ? " ALIce " : "alice", WRONG);
            require(isRedirect(response, "/login?error"), "All five admitted wrong passwords must return the error redirect");
        }
        blockedAt = System.nanoTime();
        check("O04", true, "Five wrong passwords across independent cookie stores trigger the account limit");
        var blocked = login(browser(), "alice", PASSWORD);
        long retry = retryAfter(blocked);
        check("O05", blocked.statusCode() == 429 && retry >= 1 && retry <= 60
            && blocked.headers().firstValue("Cache-Control").orElse("").contains("no-store")
            && blocked.body().contains("Vui l\u00f2ng ch\u1edd m\u1ed9t ch\u00fat."), "Next login returns 429, Retry-After, no-store and the HTML rate-limit page");
        check("O06", login(browser(), " ALICE ", PASSWORD).statusCode() == 429, "New browser and case or whitespace changes do not bypass the account limit");
        var bob = browser();
        check("O07", isRedirect(login(bob, "bob", "BobLab!2026"), "/me"), "A different account remains usable");
        logout(bob);
        check("O08", isAlice(get(copy, "/me")), "A live copied cookie still works while password login is blocked");
        logout(authenticated);
    }

    private void checkRetryDoesNotRestartBlock() throws Exception {
        var response = login(browser(), "alice", PASSWORD);
        long retry = retryAfter(response);
        check("O09", response.statusCode() == 429 && retry >= 1 && retry < 40, "Retry-After decreases and blocked requests do not restart the 60-second timer");
    }

    private void finish() throws Exception {
        var recovered = browser();
        check("O10", isRedirect(login(recovered, "alice", PASSWORD), "/me") && isAlice(get(recovered, "/me")), "Correct login succeeds after the actual 60-second block expires");
        logout(recovered);
        for (int i = 0; i < 2; i++) require(isRedirect(login(browser(), "alice", WRONG), "/login?error"), "Pre-reset failure must be admitted");
        var reset = browser(); require(isRedirect(login(reset, "alice", PASSWORD), "/me"), "Successful login resets earlier failures"); logout(reset);
        for (int i = 0; i < 4; i++) require(isRedirect(login(browser(), "alice", WRONG), "/login?error"), "Post-reset failure must be admitted");
        var finalClient = browser();
        check("O11", isRedirect(login(finalClient, "alice", PASSWORD), "/me"), "Successful authentication clears earlier failure counts");
        var copy = copiedCookie(sessionId(finalClient)); require(isAlice(get(copy, "/me")), "Copied cookie initially authenticates");
        logout(finalClient);
        check("O12", isRedirect(get(copy, "/me"), "/login"), "Valid logout still invalidates the copied session after OWASP checks");
    }

    private Browser browser() {
        var jar = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        return new Browser(HttpClient.newBuilder().sslContext(tls).cookieHandler(jar)
            .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build(), jar, null);
    }
    private Browser copiedCookie(String value) {
        return new Browser(HttpClient.newBuilder().sslContext(tls).followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10)).build(), null, cookieName + "=" + value);
    }
    private HttpResponse<String> get(Browser browser, String path) throws Exception {
        var request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(15));
        if (browser.fixedCookie() != null) request.header("Cookie", browser.fixedCookie());
        var response = browser.http().send(request.GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        rememberCookies(browser);
        return response;
    }
    private HttpResponse<String> post(Browser browser, String path, Map<String,String> fields) throws Exception {
        String body = fields.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).collect(Collectors.joining("&"));
        var response = browser.http().send(HttpRequest.newBuilder(base.resolve(path + "?audit_marker=" + MARKER))
            .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "text/html").header("X-Forwarded-For", "198.51.100.77")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        rememberCookies(browser);
        return response;
    }
    private HttpResponse<String> login(Browser browser, String user, String password) throws Exception {
        return post(browser, "/login", Map.of("username", user, "password", password, "_csrf", csrf(get(browser, "/login").body())));
    }
    private void logout(Browser browser) throws Exception {
        require(isRedirect(post(browser, "/logout", Map.of("_csrf", csrf(get(browser, "/me").body()))), "/login?logout"), "Valid logout redirects");
    }
    private String csrf(String html) {
        var matcher = CSRF.matcher(html); require(matcher.find(), "CSRF input exists");
        secrets.add(matcher.group(1)); return matcher.group(1);
    }
    private String sessionId(Browser browser) {
        for (HttpCookie c : browser.jar().getCookieStore().getCookies()) secrets.add(c.getValue());
        return browser.jar().getCookieStore().getCookies().stream().filter(c -> c.getName().equals(cookieName)).map(HttpCookie::getValue).findFirst().orElseThrow();
    }
    private void rememberCookies(Browser browser) {
        if (browser.jar() != null) for (HttpCookie cookie : browser.jar().getCookieStore().getCookies()) {
            if (!cookie.getValue().isEmpty()) secrets.add(cookie.getValue());
        }
    }
    private long retryAfter(HttpResponse<String> response) {
        return Long.parseLong(response.headers().firstValue("Retry-After").orElse("0"));
    }
    private boolean isRedirect(HttpResponse<String> response, String path) {
        return response.statusCode() == 302 && response.headers().firstValue("Location").orElse("").endsWith(path);
    }
    private boolean isAlice(HttpResponse<String> response) {
        return response.statusCode() == 200 && response.body().contains("<dd id=\"current-user\">alice</dd>");
    }
    private void check(String id, boolean condition, String label) {
        require(condition, variant + " " + id + ": " + label);
        results.add(variant + "\t" + id + "\tPASS\t" + Instant.now() + "\t" + label);
        System.out.println("PASS " + variant + " " + id + ": " + label);
    }
    private static void require(boolean condition, String label) {
        if (!condition) throw new IllegalStateException(label);
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private record Browser(HttpClient http, CookieManager jar, String fixedCookie) {}
}
