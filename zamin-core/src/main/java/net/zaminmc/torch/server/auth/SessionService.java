package net.zaminmc.torch.server.auth;

/**
 * The session-service seam (Wave ON-1): the reference's
 * MinecraftServer.getSessionService() — the one consult the online login
 * walk makes against Mojang's session servers.
 *
 * <p>The contract mirrors
 * com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService
 * .hasJoinedServer(GameProfile, String): the profile's NAME and the
 * server-id hash ride the consult; a non-null answer is the AUTHORIZED
 * profile (the authoritative id and the signed properties — what the
 * server keeps); a null answer is a flat refusal (the name is not in a
 * session with this server); an {@link AuthenticationUnavailableException}
 * is the network's own failure — the login walk's two arms answer the
 * two shapes with the reference's own two strings.</p>
 */
public interface SessionService {

    /**
     * The hasJoined consult.
     *
     * @param username     the joining name (the wire's 16-char shape)
     * @param serverIdHash the login's server-id hash (the SHA-1 digest's
     *                     hex form, BigInteger-signed — the reference's
     *                     own spelling)
     * @return the authorized profile, or null when the session servers
     *         answered "not joined"
     * @throws AuthenticationUnavailableException when the session servers
     *         could not be reached
     */
    GameProfile hasJoined(String username, String serverIdHash) throws AuthenticationUnavailableException;
}
