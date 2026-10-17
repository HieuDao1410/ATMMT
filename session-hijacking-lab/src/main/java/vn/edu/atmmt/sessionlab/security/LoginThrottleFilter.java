package vn.edu.atmmt.sessionlab.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

public class LoginThrottleFilter extends OncePerRequestFilter {

    private static final String FAILED = LoginThrottleFilter.class.getName() + ".failed";

    private final LoginAttemptLimiter limiter;
    private final SecurityAudit audit;

    public LoginThrottleFilter(LoginAttemptLimiter limiter, SecurityAudit audit) {
        this.limiter = limiter;
        this.audit = audit;
    }

    public static void markFailure(HttpServletRequest request) {
        request.setAttribute(FAILED, true);
    }

    public static void markSuccess(HttpServletRequest request) {
        request.setAttribute(FAILED, false);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !"/login".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var admission = limiter.begin(request.getParameter("username"));
        if (admission.attempt() == null) {
            audit.record(SecurityAudit.Event.LOGIN_THROTTLED, request);
            response.setHeader("Retry-After", Long.toString(admission.retryAfterSeconds()));
            response.setHeader("Cache-Control", "no-store");
            // Boot renders templates/error/429.html for browser requests.
            response.sendError(429);
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            Object outcome = request.getAttribute(FAILED);
            boolean reachedLimit = limiter.finish(admission.attempt(), outcome instanceof Boolean failed ? failed : null);
            request.removeAttribute(FAILED);
            if (reachedLimit) {
                audit.record(SecurityAudit.Event.LOGIN_LIMIT_REACHED, request);
            }
        }
    }
}
