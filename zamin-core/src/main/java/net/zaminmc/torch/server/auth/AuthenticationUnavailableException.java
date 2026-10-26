package net.zaminmc.torch.server.auth;

/**
 * The session service's own unavailability — the reference
 * com.mojang.authlib.exceptions.AuthenticationUnavailableException shape:
 * the network could not reach the session servers (connect refused,
 * timeout, DNS), which is a DIFFERENT arm from a flat "no" answer (the
 * profile came back null). The login walk answers the two arms with the
 * reference's own two strings.
 */
public class AuthenticationUnavailableException extends Exception {

    public AuthenticationUnavailableException(String message) {
        super(message);
    }

    public AuthenticationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
