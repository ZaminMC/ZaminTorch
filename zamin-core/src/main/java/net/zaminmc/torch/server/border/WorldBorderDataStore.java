package net.zaminmc.torch.server.border;

import net.zaminmc.torch.server.text.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * The world border's persistence (Slice 90 — the reference's level.dat
 * border rows): worldborder.json at the server root, one flat object —
 * the center pair, the live size with the lerp target and the remaining
 * lerp time (a pending resize resumes on load, exactly the reference's
 * own BorderSizeLerpTarget != BorderSize arm), and the damage family
 * (the safe zone, the per-block damage, the warning pair).
 *
 * <p>A corrupt or missing file degrades to the fresh border — never a
 * boot failure (the established store behavior).</p>
 */
public final class WorldBorderDataStore {

    private final Path file;
    private final EngineWorldBorder border;

    public WorldBorderDataStore(Path file, EngineWorldBorder border) {
        this.file = Objects.requireNonNull(file, "file");
        this.border = Objects.requireNonNull(border, "border");
    }

    /** Loads the saved border; a missing or corrupt file leaves the fresh one. */
    public void load() {
        if (!Files.exists(file)) {
            return;
        }
        String json;
        try {
            json = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException unreadable) {
            return; // a corrupt file degrades to the fresh border, never a boot failure
        }
        try {
            Json.Value value = Json.parse(json);
            if (!(value instanceof Json.Obj obj)) {
                return;
            }
            Double centerX = num(obj, "centerX");
            Double centerZ = num(obj, "centerZ");
            Double size = num(obj, "size");
            if (centerX == null || centerZ == null || size == null) {
                return;
            }
            Double lerpTarget = num(obj, "sizeLerpTarget");
            Long lerpTime = integer(obj, "sizeLerpTime");
            border.setCenter(centerX, centerZ);
            // The reference's own load arm: a pending lerp (target != size)
            // resumes with the saved remaining time; a stationary border takes
            // the plain setSize.
            if (lerpTarget != null && lerpTime != null && lerpTime > 0
                    && lerpTarget != size) {
                border.setSize(size, lerpTarget, lerpTime);
            } else {
                border.setSize(size);
            }
            Double safeZone = num(obj, "safeZone");
            if (safeZone != null) {
                border.setSafeZone(safeZone);
            }
            Double damagePerBlock = num(obj, "damagePerBlock");
            if (damagePerBlock != null) {
                border.setDamagePerBlock(damagePerBlock);
            }
            Integer warningTime = whole(obj, "warningTime");
            if (warningTime != null) {
                border.setWarningTime(warningTime);
            }
            Integer warningDistance = whole(obj, "warningDistance");
            if (warningDistance != null) {
                border.setWarningDistance(warningDistance);
            }
        } catch (RuntimeException corrupt) {
            // a malformed row degrades to what already landed, never a boot failure
        }
    }

    /** Saves the border's live state (the pending lerp as the remaining time). */
    public void save() {
        // The live size first — a completed lerp snaps here (getLerpSize's
        // own completion arm), so a finished resize saves stationary.
        double size = border.getLerpSize();
        String json = "{\n"
                + "  \"centerX\": " + border.getCenterX() + ",\n"
                + "  \"centerZ\": " + border.getCenterZ() + ",\n"
                + "  \"size\": " + size + ",\n"
                + "  \"sizeLerpTarget\": " + border.getSizeLerpTarget() + ",\n"
                + "  \"sizeLerpTime\": " + border.getLerpTime() + ",\n"
                + "  \"safeZone\": " + border.getSafeZone() + ",\n"
                + "  \"damagePerBlock\": " + border.getDamagePerBlock() + ",\n"
                + "  \"warningTime\": " + border.getWarningTime() + ",\n"
                + "  \"warningDistance\": " + border.getWarningDistance() + "\n"
                + "}\n";
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("World border save failed for " + file, e);
        }
    }

    private static Double num(Json.Obj obj, String key) {
        Json.Value value = obj.getPrim(key);
        if (value instanceof Json.Num number) {
            try {
                return number.getAsDouble();
            } catch (NumberFormatException malformed) {
                return null;
            }
        }
        if (value instanceof Json.Str text) {
            try {
                return Double.parseDouble(text.getAsString());
            } catch (NumberFormatException malformed) {
                return null;
            }
        }
        return null;
    }

    private static Long integer(Json.Obj obj, String key) {
        Json.Value value = obj.getPrim(key);
        if (value instanceof Json.Num number) {
            try {
                return number.getAsLong();
            } catch (NumberFormatException malformed) {
                return null;
            }
        }
        return null;
    }

    private static Integer whole(Json.Obj obj, String key) {
        Json.Value value = obj.getPrim(key);
        if (value instanceof Json.Num number) {
            try {
                return number.getAsInt();
            } catch (NumberFormatException malformed) {
                return null;
            }
        }
        return null;
    }
}
