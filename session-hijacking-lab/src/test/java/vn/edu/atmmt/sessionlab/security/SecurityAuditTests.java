package vn.edu.atmmt.sessionlab.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class SecurityAuditTests {

    @Test
    void auditUsesOnlyAllowlistedFieldsAndReusesTheRequestId() {
        var logger = (Logger) LoggerFactory.getLogger(SecurityAudit.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var request = new MockHttpServletRequest();
            request.setMethod("POST");
            request.setServletPath("/login");
            request.setRemoteAddr("127.0.0.1\r\nFORGED_EVENT=secret");
            request.setParameter("username", "private-user");
            request.setParameter("password", "private-password");
            request.setParameter("_csrf", "private-token");
            request.addHeader("Cookie", "JSESSIONID=private-session");
            request.addHeader("X-Forwarded-For", "private-forwarded-address");
            request.setQueryString("token=private-query");
            var audit = new SecurityAudit();
            audit.record(SecurityAudit.Event.LOGIN_FAILURE, request);
            audit.record(SecurityAudit.Event.LOGIN_LIMIT_REACHED, request);
            assertEquals(2, appender.list.size());
            String first = appender.list.get(0).getFormattedMessage();
            String second = appender.list.get(1).getFormattedMessage();
            assertTrue(first.matches("security_event=LOGIN_FAILURE request_id=[a-f0-9-]{36} client=[a-zA-Z0-9_.:@-]{1,64} route=LOGIN method=POST"));
            assertEquals(first.split(" ")[1], second.split(" ")[1]);
            for (String secret : new String[]{"private-user", "private-password", "private-token", "private-session", "private-forwarded-address", "private-query"}) {
                assertFalse((first + second).contains(secret));
            }
            assertFalse(first.contains("\r"));
            assertFalse(first.contains("\n"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
