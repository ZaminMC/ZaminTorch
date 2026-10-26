package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.server.auth.AuthenticationUnavailableException;
import net.zaminmc.torch.server.auth.GameProfile;
import net.zaminmc.torch.server.auth.SessionService;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * The Mojang session-service consult (Wave ON-1b) — the reference's
 * YggdrasilMinecraftSessionService.hasJoinedServer arm: one GET against
 * the session server's hasJoined endpoint with the joining name and the
 * login's server-id hash.
 *
 * <p><b>The endpoint's own contract</b>:
 * {@code https://sessionserver.mojang.com/session/minecraft/hasJoined?username=X&serverId=Y}
 * — a 200 answers the authorized profile (the authoritative undashed id,
 * the name, the signed texture properties); a 204 (or any other status)
 * is the flat refusal — the name is not in a session with this server,
 * which the login walk answers with the reference's "Failed to verify
 * username!" string. A connect failure or timeout is the network's own
 * arm — {@link AuthenticationUnavailableException} — the reference's
 * "Authentication servers are down." string.</p>
 *
 * <p><b>The bounds</b>: a 5-second connect and a 20-second request
 * timeout — the reference authlib carries no explicit bound (an OS-level
 * stall hangs its authenticator thread and the login dies on the
 * read-timeout reap instead of the honest down-string); the bounded
 * client is the one deliberate hardening, so the failure arm reaches the
 * player with the reference's own message. The client is shared
 * (one per server, the JDK's own pooling); the consult is called only on
 * the login's authenticator thread, never the event loop.</p>
 */
final class MojangSessionService implements SessionService {

    static final String HAS_JOINED_URL = "https://sessionserver.mojang.com"
            + "/session/minecraft/hasJoined";

    private final String endpoint;
    private final HttpClient client;
    private final Duration requestTimeout;

    MojangSessionService() {
        this(HAS_JOINED_URL, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), Duration.ofSeconds(20));
    }

    /** Visible for tests: the endpoint, the client, and the bound. */
    MojangSessionService(String endpoint, HttpClient client, Duration requestTimeout) {
        this.endpoint = endpoint;
        this.client = client;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public GameProfile hasJoined(String username, String serverIdHash)
            throws AuthenticationUnavailableException {
        String url = endpoint
                + "?username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&serverId=" + URLEncoder.encode(serverIdHash, StandardCharsets.UTF_8);
        return consult(url);
    }

    /**
     * The consult itself: one GET, the status arm, the parse arm. Package
     * visible so the wire tests drive the same walk against a local stub.
     */
    GameProfile consult(String url) throws AuthenticationUnavailableException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new AuthenticationUnavailableException(
                    "the session servers could not be reached: " + e.getMessage(), e);
        }
        if (response.statusCode() != 200) {
            // 204 No Content is the flat refusal; any other status rides
            // the same arm (the endpoint's contract answers 200 or no).
            return null;
        }
        String body = response.body();
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return SessionProfileParser.parse(body);
        } catch (IllegalArgumentException e) {
            // A body we cannot read whole is the endpoint's own failure —
            // never a half-profile login. The down-arm answers it.
            throw new AuthenticationUnavailableException(
                    "the session server's answer did not parse: " + e.getMessage(), e);
        }
    }
}
