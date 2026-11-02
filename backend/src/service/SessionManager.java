package service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which login token belongs to which user.
 * Everything here is static because there is exactly one shared list
 * for the whole server. (Tokens reset when the server restarts.)
 */
public final class SessionManager {

    private static final Map<String, Integer> SESSIONS = new ConcurrentHashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();

    private SessionManager() {
    }

    public static String createSession(int userId) {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        StringBuilder token = new StringBuilder();
        for (byte b : bytes) {
            token.append(String.format("%02x", b));
        }
        SESSIONS.put(token.toString(), userId);
        return token.toString();
    }

    /** Returns the user id for a token, or null when the token is unknown. */
    public static Integer getUserId(String token) {
        return token == null ? null : SESSIONS.get(token);
    }

    public static void remove(String token) {
        if (token != null) {
            SESSIONS.remove(token);
        }
    }
}
