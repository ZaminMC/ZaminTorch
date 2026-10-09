package net.minecraft.server.stat;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.StatsS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.stat.PlayerStats;
import net.minecraft.stat.Stat;
import net.minecraft.stat.StatCounter;
import net.minecraft.stat.StatProgress;
import net.minecraft.stat.Stats;
import net.minecraft.stat.achievement.AchievementStat;
import net.minecraft.stat.achievement.Achievements;
import net.minecraft.text.TranslatableText;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ServerPlayerStats extends PlayerStats {
    private static final Logger LOGGER = LogManager.getLogger();
    private final MinecraftServer server;
    private final File file;
    private final Set<Stat> dirtyStats = Sets.newHashSet();
    private int lastUpdate = -300;
    private boolean dirty = false;

    public ServerPlayerStats(MinecraftServer server, File file) {
        this.server = server;
        this.file = file;
    }

    public void load() {
        if (this.file.isFile()) {
            try {
                this.counters.clear();
                this.counters.putAll(this.deserialize(FileUtils.readFileToString(this.file)));
            } catch (IOException ioexception) {
                LOGGER.error("Couldn't read statistics file " + this.file, ioexception);
            } catch (JsonParseException jsonparseexception) {
                LOGGER.error("Couldn't parse statistics file " + this.file, jsonparseexception);
            }
        }
    }

    public void save() {
        try {
            FileUtils.writeStringToFile(this.file, serialize(this.counters));
        } catch (IOException ioexception) {
            LOGGER.error("Couldn't save stats", ioexception);
        }
    }

    @Override
    public void set(PlayerEntity player, Stat stat, int value) {
        int i = stat.isAchievement() ? this.get(stat) : 0;
        super.set(player, stat, value);
        this.dirtyStats.add(stat);
        if (stat.isAchievement() && i == 0 && value > 0) {
            this.dirty = true;
            if (this.server.shouldAnnouncePlayerAchievements()) {
                this.server.getPlayerManager().sendSystemMessage(new TranslatableText("chat.type.achievement", player.getDisplayName(), stat.getNameForChat()));
            }
        }

        if (stat.isAchievement() && i > 0 && value == 0) {
            this.dirty = true;
            if (this.server.shouldAnnouncePlayerAchievements()) {
                this.server
                    .getPlayerManager()
                    .sendSystemMessage(new TranslatableText("chat.type.achievement.taken", player.getDisplayName(), stat.getNameForChat()));
            }
        }
    }

    public Set<Stat> takeDirty() {
        Set<Stat> set = Sets.newHashSet(this.dirtyStats);
        this.dirtyStats.clear();
        this.dirty = false;
        return set;
    }

    public Map<Stat, StatCounter> deserialize(String data) {
        JsonElement jsonelement = new JsonParser().parse(data);
        if (!jsonelement.isJsonObject()) {
            return Maps.newHashMap();
        }

        JsonObject jsonobject = jsonelement.getAsJsonObject();
        Map<Stat, StatCounter> map = Maps.newHashMap();

        for (Entry<String, JsonElement> entry : jsonobject.entrySet()) {
            Stat stat = Stats.byKey(entry.getKey());
            if (stat != null) {
                StatCounter statcounter = new StatCounter();
                if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
                    statcounter.setValue(entry.getValue().getAsInt());
                } else if (entry.getValue().isJsonObject()) {
                    JsonObject jsonobject1 = entry.getValue().getAsJsonObject();
                    if (jsonobject1.has("value") && jsonobject1.get("value").isJsonPrimitive() && jsonobject1.get("value").getAsJsonPrimitive().isNumber()) {
                        statcounter.setValue(jsonobject1.getAsJsonPrimitive("value").getAsInt());
                    }

                    if (jsonobject1.has("progress") && stat.getProgressType() != null) {
                        try {
                            Constructor<? extends StatProgress> constructor = stat.getProgressType().getConstructor();
                            StatProgress statprogress = constructor.newInstance();
                            statprogress.update(jsonobject1.get("progress"));
                            statcounter.setProgress(statprogress);
                        } catch (Throwable throwable) {
                            LOGGER.warn("Invalid statistic progress in " + this.file, throwable);
                        }
                    }
                }

                map.put(stat, statcounter);
            } else {
                LOGGER.warn("Invalid statistic in " + this.file + ": Don't know what " + entry.getKey() + " is");
            }
        }

        return map;
    }

    public static String serialize(Map<Stat, StatCounter> stats) {
        JsonObject jsonobject = new JsonObject();

        for (Entry<Stat, StatCounter> entry : stats.entrySet()) {
            if (entry.getValue().getProgress() != null) {
                JsonObject jsonobject1 = new JsonObject();
                jsonobject1.addProperty("value", entry.getValue().getValue());

                try {
                    jsonobject1.add("progress", entry.getValue().<StatProgress>getProgress().toJson());
                } catch (Throwable throwable) {
                    LOGGER.warn("Couldn't save statistic " + entry.getKey().getDecoratedName() + ": error serializing progress", throwable);
                }

                jsonobject.add(entry.getKey().key, jsonobject1);
            } else {
                jsonobject.addProperty(entry.getKey().key, entry.getValue().getValue());
            }
        }

        return jsonobject.toString();
    }

    public void markAllDirty() {
        for (Stat stat : this.counters.keySet()) {
            this.dirtyStats.add(stat);
        }
    }

    public void sendStats(ServerPlayerEntity player) {
        int i = this.server.getTicks();
        Map<Stat, Integer> map = Maps.newHashMap();
        if (this.dirty || i - this.lastUpdate > 300) {
            this.lastUpdate = i;

            for (Stat stat : this.takeDirty()) {
                map.put(stat, this.get(stat));
            }
        }

        player.networkHandler.sendPacket(new StatsS2CPacket(map));
    }

    public void sendAchievements(ServerPlayerEntity player) {
        Map<Stat, Integer> map = Maps.newHashMap();

        for (AchievementStat achievementstat : Achievements.ALL) {
            if (this.hasAchievement(achievementstat)) {
                map.put(achievementstat, this.get((Stat)achievementstat));
                this.dirtyStats.remove(achievementstat);
            }
        }

        player.networkHandler.sendPacket(new StatsS2CPacket(map));
    }

    public boolean isDirty() {
        return this.dirty;
    }
}
