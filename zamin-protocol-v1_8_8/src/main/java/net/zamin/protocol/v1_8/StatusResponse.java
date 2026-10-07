package net.zamin.protocol.v1_8;

import net.zamin.engine.EngineServer;

/**
 * Builds the status response JSON of protocol 47. JSON is assembled directly:
 * the schema is tiny, fixed, and version-specific — a JSON library would add a
 * dependency without removing any real complexity here (§31 dependency rule).
 */
final class StatusResponse {

    private StatusResponse() {
    }

    static String build(EngineServer server, int onlinePlayers) {
        String motd = server.config().motd()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        return "{\"version\":{\"name\":\"" + Protocol18.VERSION_NAME
                + "\",\"protocol\":" + Protocol18.PROTOCOL_VERSION
                + "},\"players\":{\"max\":" + server.config().maxPlayers()
                + ",\"online\":" + onlinePlayers
                + "},\"description\":{\"text\":\"" + motd + "\"}}";
    }

    /** Builds the login-stage disconnect reason. */
    static String disconnectReason(String text) {
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"text\":\"" + escaped + "\"}";
    }
}
