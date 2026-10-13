package vn.edu.atmmt.sessionlab;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionHijackingLabApplicationTests {

    private static final Pattern CSRF_INPUT =
        Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

    @LocalServerPort
    private int port;

    @Test
    void publicPagesAndStylesAreAccessible() throws Exception {
        var browser = newBrowser();
        assertEquals(200, get(browser, "/").statusCode());
        assertEquals(200, get(browser, "/login").statusCode());
        assertEquals(200, get(browser, "/css/app.css").statusCode());
    }

    @Test
    void privatePagesRequireAuthentication() throws Exception {
        var browser = newBrowser();
        assertLoginRedirect(get(browser, "/me"));
        assertLoginRedirect(get(browser, "/notes"));
    }

    @Test
    void validCredentialsAuthenticateBothLabUsers() throws Exception {
        var alice = newBrowser();
        var bob = newBrowser();
        login(alice, "alice", "AliceLab!2026");
        login(bob, "bob", "BobLab!2026");
        assertIdentity(alice, "alice");
        assertIdentity(bob, "bob");
    }

    @Test
    void wrongPasswordDoesNotAuthenticate() throws Exception {
        var browser = newBrowser();
        String token = csrfToken(get(browser, "/login").body());
        var response = post(browser, "/login", Map.of(
            "username", "alice", "password", "wrong-password", "_csrf", token));

        assertEquals(302, response.statusCode());
        assertTrue(response.headers().firstValue("Location").orElseThrow().endsWith("/login?error"));
        assertLoginRedirect(get(browser, "/me"));
        assertTrue(get(browser, "/login?error").body().contains("Tên đăng nhập hoặc mật khẩu không đúng."));
    }

    @Test
    void notesUseAuthenticatedOwnerEvenWhenRequestSuggestsAnotherUser() throws Exception {
        var alice = newBrowser();
        var bob = newBrowser();
        login(alice, "alice", "AliceLab!2026");
        login(bob, "bob", "BobLab!2026");

        var aliceNotes = get(alice, "/notes?username=bob");
        var bobNotes = get(bob, "/notes?username=alice");
        assertEquals(200, aliceNotes.statusCode());
        assertEquals(200, bobNotes.statusCode());
        assertTrue(aliceNotes.body().contains("Ghi chú riêng của Alice"));
        assertFalse(aliceNotes.body().contains("Ghi chú riêng của Bob"));
        assertTrue(bobNotes.body().contains("Ghi chú riêng của Bob"));
        assertFalse(bobNotes.body().contains("Ghi chú riêng của Alice"));
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() throws Exception {
        var browser = newBrowser();
        get(browser, "/login");
        var response = post(browser, "/login", Map.of(
            "username", "alice", "password", "AliceLab!2026"));
        assertEquals(403, response.statusCode());
        assertLoginRedirect(get(browser, "/me"));
    }

    @Test
    void csrfTokenFromAnotherSessionIsRejected() throws Exception {
        var first = newBrowser();
        var second = newBrowser();
        String otherToken = csrfToken(get(first, "/login").body());
        get(second, "/login");
        var response = post(second, "/login", Map.of(
            "username", "alice", "password", "AliceLab!2026", "_csrf", otherToken));
        assertEquals(403, response.statusCode());
        assertLoginRedirect(get(second, "/me"));
    }

    @Test
    void sessionIdChangesAfterLoginAndOldAnonymousIdDoesNotAuthenticate() throws Exception {
        var browser = newBrowser();
        String token = csrfToken(get(browser, "/login").body());
        String beforeLogin = sessionId(browser);
        var oldSessionClient = clientWithSession(beforeLogin);

        var response = post(browser, "/login", Map.of(
            "username", "alice", "password", "AliceLab!2026", "_csrf", token));
        assertEquals(302, response.statusCode());
        assertNotEquals(beforeLogin, sessionId(browser), "Session ID must rotate on authentication");
        assertIdentity(browser, "alice");
        assertLoginRedirect(get(oldSessionClient, "/me"));
    }

    @Test
    void copiedLiveSessionIdentifiesTheSameUserWithoutPassword() throws Exception {
        var alice = newBrowser();
        login(alice, "alice", "AliceLab!2026");
        var secondClient = clientWithSession(sessionId(alice));
        assertIdentity(secondClient, "alice");
    }

    @Test
    void logoutInvalidatesTheSessionForBothOriginalAndCopiedCookie() throws Exception {
        var alice = newBrowser();
        login(alice, "alice", "AliceLab!2026");
        var secondClient = clientWithSession(sessionId(alice));
        assertIdentity(secondClient, "alice");

        String token = csrfToken(get(alice, "/me").body());
        var response = post(alice, "/logout", Map.of("_csrf", token));
        assertEquals(302, response.statusCode());
        assertTrue(response.headers().firstValue("Location").orElseThrow().endsWith("/login?logout"));
        assertLoginRedirect(get(alice, "/me"));
        assertLoginRedirect(get(secondClient, "/me"));
        assertTrue(get(alice, "/login?logout").body().contains("Bạn đã đăng xuất."));
    }

    @Test
    void logoutWithoutCsrfTokenIsRejectedAndSessionRemainsValid() throws Exception {
        var alice = newBrowser();
        login(alice, "alice", "AliceLab!2026");
        assertEquals(403, post(alice, "/logout", Map.of()).statusCode());
        assertIdentity(alice, "alice");
    }

    @Test
    void getLogoutDoesNotInvalidateTheSession() throws Exception {
        var alice = newBrowser();
        login(alice, "alice", "AliceLab!2026");
        get(alice, "/logout");
        assertIdentity(alice, "alice");
    }

    @Test
    void sessionCookieHasHttpOnlySameSiteAndNoUrlRewriting() throws Exception {
        var browser = newBrowser();
        var response = get(browser, "/login");
        String sessionHeader = response.headers().allValues("Set-Cookie").stream()
            .filter(header -> header.startsWith("JSESSIONID="))
            .findFirst().orElseThrow();
        assertTrue(sessionHeader.contains("HttpOnly"));
        assertTrue(sessionHeader.contains("SameSite=Lax"));
        assertFalse(response.body().contains(";jsessionid="));
    }

    private void login(Browser browser, String username, String password) throws Exception {
        String token = csrfToken(get(browser, "/login").body());
        var response = post(browser, "/login", Map.of(
            "username", username, "password", password, "_csrf", token));
        assertEquals(302, response.statusCode());
        assertTrue(response.headers().firstValue("Location").orElseThrow().endsWith("/me"));
    }

    private void assertIdentity(Browser browser, String username) throws Exception {
        var response = get(browser, "/me");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("<dd id=\"current-user\">" + username + "</dd>"));
        assertTrue(response.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));
    }

    private void assertLoginRedirect(HttpResponse<String> response) {
        assertEquals(302, response.statusCode());
        URI location = URI.create(response.headers().firstValue("Location").orElseThrow());
        assertEquals("/login", location.getPath());
    }

    private String csrfToken(String html) {
        var matcher = CSRF_INPUT.matcher(html);
        assertTrue(matcher.find(), "The rendered form must contain a CSRF token");
        return matcher.group(1);
    }

    private Browser newBrowser() {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder()
            .cookieHandler(cookies)
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        return new Browser(cookies, client);
    }

    private Browser clientWithSession(String value) {
        var browser = newBrowser();
        var cookie = new HttpCookie("JSESSIONID", value);
        cookie.setVersion(0);
        cookie.setPath("/");
        browser.cookies().getCookieStore().add(uri("/"), cookie);
        return browser;
    }

    private String sessionId(Browser browser) {
        return browser.cookies().getCookieStore().getCookies().stream()
            .filter(cookie -> cookie.getName().equals("JSESSIONID"))
            .map(HttpCookie::getValue)
            .findFirst().orElseThrow();
    }

    private HttpResponse<String> get(Browser browser, String path) throws Exception {
        var request = HttpRequest.newBuilder(uri(path))
            .timeout(Duration.ofSeconds(10))
            .GET().build();
        return browser.client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(Browser browser, String path, Map<String, String> fields) throws Exception {
        String body = fields.entrySet().stream()
            .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
            .collect(Collectors.joining("&"));
        var request = HttpRequest.newBuilder(uri(path))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return browser.client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record Browser(CookieManager cookies, HttpClient client) {
    }
}
