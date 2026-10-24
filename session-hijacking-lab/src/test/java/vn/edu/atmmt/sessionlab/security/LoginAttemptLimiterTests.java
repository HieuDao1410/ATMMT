package vn.edu.atmmt.sessionlab.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptLimiterTests {

    @Test
    void fifthFailureBlocksTheAccountAcrossCaseAndWhitespaceVariants() {
        var limiter = new LoginAttemptLimiter(5, 300, 60);
        for (int i = 0; i < 5; i++) {
            var admission = limiter.begin(i % 2 == 0 ? "alice" : " ALIce ");
            assertNotNull(admission.attempt());
            assertEquals(i == 4, limiter.finish(admission.attempt(), true));
        }
        var blocked = limiter.begin("Alice");
        assertNull(blocked.attempt());
        assertTrue(blocked.retryAfterSeconds() >= 1 && blocked.retryAfterSeconds() <= 60);
        assertNotNull(limiter.begin("bob").attempt());
    }

    @Test
    void successfulAuthenticationClearsEarlierFailures() {
        var limiter = new LoginAttemptLimiter(2, 300, 60);
        assertFalse(limiter.finish(limiter.begin("alice").attempt(), true));
        assertFalse(limiter.finish(limiter.begin("alice").attempt(), false));
        assertFalse(limiter.finish(limiter.begin("alice").attempt(), true));
        assertNotNull(limiter.begin("alice").attempt());
    }

    @Test
    void unfinishedRequestsReserveCapacityAndCannotBypassTheThreshold() {
        var limiter = new LoginAttemptLimiter(2, 300, 60);
        var first = limiter.begin("alice").attempt();
        var second = limiter.begin("alice").attempt();
        assertNotNull(first);
        assertNotNull(second);
        assertNull(limiter.begin("alice").attempt());
        assertFalse(limiter.finish(first, true));
        assertNull(limiter.begin("alice").attempt());
        assertTrue(limiter.finish(second, true));
        assertNull(limiter.begin("alice").attempt());
    }

    @Test
    void missingCredentialOutcomeReleasesReservationWithoutCountingAFailure() {
        var limiter = new LoginAttemptLimiter(1, 300, 60);
        var attempt = limiter.begin("alice").attempt();
        assertFalse(limiter.finish(attempt, null));
        assertFalse(limiter.finish(attempt, true), "Finishing twice must have no effect");
        assertNotNull(limiter.begin("alice").attempt());
    }

    @Test
    void blockedAccountCanTryAgainAfterTheConfiguredPeriod() throws Exception {
        var limiter = new LoginAttemptLimiter(1, 300, 1);
        assertTrue(limiter.finish(limiter.begin("alice").attempt(), true));
        assertNull(limiter.begin("alice").attempt());
        Thread.sleep(1_100);
        assertNotNull(limiter.begin("alice").attempt());
    }

    @Test
    void failuresFromAnExpiredObservationWindowDoNotAccumulate() throws Exception {
        var limiter = new LoginAttemptLimiter(2, 1, 60);
        assertFalse(limiter.finish(limiter.begin("alice").attempt(), true));
        Thread.sleep(1_100);
        assertFalse(limiter.finish(limiter.begin("alice").attempt(), true));
        assertNotNull(limiter.begin("alice").attempt());
    }

    @Test
    void invalidLimitsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LoginAttemptLimiter(0, 300, 60));
        assertThrows(IllegalArgumentException.class, () -> new LoginAttemptLimiter(5, 0, 60));
        assertThrows(IllegalArgumentException.class, () -> new LoginAttemptLimiter(5, 300, 0));
    }
}
