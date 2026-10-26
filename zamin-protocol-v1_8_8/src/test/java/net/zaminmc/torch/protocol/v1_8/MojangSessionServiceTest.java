package net.zaminmc.torch.protocol.v1_8;

import com.sun.net.httpserver.HttpServer;
import net.zaminmc.torch.server.auth.AuthenticationUnavailableException;
import net.zaminmc.torch.server.auth.GameProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave ON-1b's session-service pins — the hasJoined consult against a
 * local stub HTTP server (the endpoint's three answers: the 200 profile,
 * the 204 refusal, the network's own failure), plus the URL's own shape
 * (the name and the server-id hash ride the query, encoded).
 */
class MojangSessionServiceTest {

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();
    private volatile String lastUri;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** Boots the stub with the given responder. */
    private int boot(Responder responder) throws java.io.IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/session/minecraft/hasJoined", exchange -> {
            hits.incrementAndGet();
            lastUri = exchange.getRequestURI().toString();
            try (OutputStream out = exchange.getResponseBody()) {
                responder.answer(exchange, out);
            }
        });
        server.start();
        return server.getAddress().getPort();
    }

    @FunctionalInterface
    private interface Responder {
        void answer(com.sun.net.httpserver.HttpExchange exchange, OutputStream out) throws java.io.IOException;
    }

    /** The service pointed at the local stub (the same consult walk). */
    private static MojangSessionService serviceOver(int port) {
        return new MojangSessionService("http://127.0.0.1:" + port
                + "/session/minecraft/hasJoined",
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                Duration.ofSeconds(5));
    }

    @Test
    void a200AnswerIsTheProfile() throws Exception {
        String body = "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\","
                + "\"properties\":[{\"name\":\"textures\",\"value\":\"dg==\",\"signature\":\"cw==\"}]}";
        int port = boot((exchange, out) -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.getBytes(StandardCharsets.UTF_8).length);
            out.write(body.getBytes(StandardCharsets.UTF_8));
        });
        GameProfile profile = serviceOver(port).hasJoined("Notch", "-4f0f1c2b");
        assertEquals(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"), profile.id());
        assertEquals("Notch", profile.name());
        assertEquals(1, profile.properties().size());
        assertEquals("/session/minecraft/hasJoined?username=Notch&serverId=-4f0f1c2b", lastUri,
                "the name and the hash ride the query as-is (the endpoint's own form)");
    }

    @Test
    void a204AnswerIsTheFlatRefusal() throws Exception {
        int port = boot((exchange, out) -> exchange.sendResponseHeaders(204, -1));
        assertNull(serviceOver(port).hasJoined("Notch", "abc123"));
        assertEquals(1, hits.get());
    }

    @Test
    void aGarbage200IsTheDownArm() throws Exception {
        int port = boot((exchange, out) -> {
            byte[] garbage = "not a profile at all".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, garbage.length);
            out.write(garbage);
        });
        assertThrows(AuthenticationUnavailableException.class,
                () -> serviceOver(port).hasJoined("Notch", "abc123"));
    }

    @Test
    void anUnreachableServiceIsTheDownArm() {
        // Port 1 on localhost: nothing listens there — a connect refusal.
        MojangSessionService dead = new MojangSessionService(
                "http://127.0.0.1:1/session/minecraft/hasJoined",
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                Duration.ofSeconds(2));
        assertThrows(AuthenticationUnavailableException.class,
                () -> dead.hasJoined("Notch", "abc123"));
    }

    @Test
    void theRealEndpointsUrlIsTheReferenceShape() {
        assertEquals("https://sessionserver.mojang.com/session/minecraft/hasJoined",
                MojangSessionService.HAS_JOINED_URL);
        assertTrue(MojangSessionService.HAS_JOINED_URL.startsWith("https://"),
                "the consult rides TLS");
    }
}
