package emu.grasscutter.auth;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.Account;
import org.junit.jupiter.api.Test;

class ComboDiagnosticRedactionTest {
    @Test
    void loginAndTokenAdoptionDoNotExposeEvenCredentialPrefixes() {
        var logger = Grasscutter.getLogger();
        Level previousLevel = logger.getLevel();
        var capture = new ListAppender<ILoggingEvent>();
        capture.start();
        logger.addAppender(capture);
        logger.setLevel(Level.INFO);
        String supplied = "supplied-secret-credential";
        String stored = "stored-secret-credential";
        var account = new Account();
        account.setSessionKey(stored);
        try {
            DefaultAuthenticators.SessionKeyAuthenticator.logAttempt("loopback", "test-uid", supplied, account);
            DefaultAuthenticators.SessionKeyAuthenticator.logTokenAdoption("test-uid", stored, supplied);
            DefaultAuthenticators.SessionKeyAuthenticator.logAttempt("loopback", "test-uid", null, null);
            DefaultAuthenticators.SessionKeyAuthenticator.logTokenAdoption("test-uid", null, null);
            String logs = capture.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (a, b) -> a + "\n" + b);
            assertTrue(logs.contains("uid=test-uid"));
            assertTrue(logs.contains("account=true"));
            assertTrue(logs.contains("account=false"));
            assertFalse(logs.contains(supplied.substring(0, 12)), "supplied token prefix leaked");
            assertFalse(logs.contains(stored.substring(0, 12)), "stored token prefix leaked");
            assertEquals(stored, account.getSessionKey(), "logging must not change authentication state");
        } finally {
            logger.setLevel(previousLevel);
            logger.detachAppender(capture);
            capture.stop();
        }
    }
}
