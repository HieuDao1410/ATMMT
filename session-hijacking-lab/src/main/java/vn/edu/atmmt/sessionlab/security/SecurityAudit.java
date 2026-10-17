package vn.edu.atmmt.sessionlab.security;

import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SecurityAudit {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityAudit.class);
    private static final String REQUEST_ID = SecurityAudit.class.getName() + ".requestId";
    private static final Set<String> METHODS = Set.of("GET", "POST", "HEAD", "OPTIONS", "PUT", "PATCH", "DELETE");

    public enum Event {
        LOGIN_SUCCESS, LOGIN_FAILURE, LOGIN_THROTTLED, LOGIN_LIMIT_REACHED,
        LOGIN_ERROR, LOGOUT, ACCESS_DENIED, CSRF_REJECTED
    }

    public void record(Event event, HttpServletRequest request) {
        // Never log credentials, cookies, tokens, request bodies, query strings or exception messages.
        String client = safeField(request.getRemoteAddr());
        // Omit usernames as well: the event can be shared without exposing account identifiers.
        Object requestId = request.getAttribute(REQUEST_ID);
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
            request.setAttribute(REQUEST_ID, requestId);
        }
        String route = switch (request.getServletPath()) {
            case "/login" -> "LOGIN";
            case "/logout" -> "LOGOUT";
            case "/me" -> "ME";
            case "/notes" -> "NOTES";
            default -> "OTHER";
        };
        String method = METHODS.contains(request.getMethod()) ? request.getMethod() : "OTHER";
        String format = "security_event={} request_id={} client={} route={} method={}";
        if (event != Event.LOGIN_SUCCESS && event != Event.LOGOUT) {
            LOG.warn(format, event, requestId, client, route, method);
        } else {
            LOG.info(format, event, requestId, client, route, method);
        }
    }

    private String safeField(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String bounded = value.substring(0, Math.min(value.length(), 64));
        return bounded.replaceAll("[^a-zA-Z0-9_.:@-]", "_");
    }
}
