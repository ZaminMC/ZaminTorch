package net.zaminmc.torch.server.auth;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The authenticated player profile — Torch's GameProfile (the reference's
 * com.mojang.authlib shape, the fields the 1.8.8 wire and engine actually
 * read: the UUID, the name, and the signed properties the session service
 * returns — the textures property, its value and signature).
 *
 * <p>Wave ON-1's carrier: the offline branch builds it from the
 * offline-UUID convention (no properties — the reference's own
 * createFakeProfile shape); the online branch builds it from the
 * hasJoined response whole. Immutable, value-equal.</p>
 *
 * @param id         the profile's UUID — the offline v3 for the offline
 *                   branch, the Mojang id for the online branch
 * @param name       the profile's name (16-char wire shape)
 * @param properties the signed properties (empty offline; the online
 *                   branch's textures row)
 */
public record GameProfile(UUID id, String name, List<Property> properties) {

    public GameProfile {
        Objects.requireNonNull(name, "name");
        properties = properties == null ? List.of() : List.copyOf(properties);
    }

    /** The offline-branch shape (the reference's createFakeProfile: no properties). */
    public static GameProfile offline(String name, UUID offlineUuid) {
        return new GameProfile(offlineUuid, name, List.of());
    }

    /**
     * One signed profile property — the reference's PropertyTriple shape:
     * the name ("textures"), the base64 value, and the signature when the
     * session service signed it (it always does for textures).
     */
    public record Property(String name, String value, String signature) {

        public Property {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
        }

        /** The wire's own question: whether a signature rides the property. */
        public boolean hasSignature() {
            return signature != null && !signature.isEmpty();
        }
    }
}
