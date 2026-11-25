package com.suma.auth;

import com.suma.common.ClientInfo;
import com.suma.user.User;

import java.time.Instant;
import java.util.UUID;

/** Hook so that attendance tracking is driven by real authentication events. */
public interface SessionLifecycleListener {
    void onLogin(User user, UserSession session, ClientInfo client);

    /** @param source USER_LOGOUT, SESSION_EXPIRY, ADMIN_TERMINATED, BROWSER_UNLOAD, RECONCILIATION */
    void onSessionEnded(UUID sessionId, String source, Instant at);
}
