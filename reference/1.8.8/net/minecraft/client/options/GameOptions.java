package net.minecraft.client.options;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.chat.ChatGui;
import net.minecraft.client.render.model.PlayerModelPart;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.sound.SoundCategory;
import net.minecraft.client.twitch.Twitch;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientSettingsC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Difficulty;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

public class GameOptions {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new Gson();
    private static final ParameterizedType STRING_LIST_TYPE = new ParameterizedType() {
        @Override
        public Type[] getActualTypeArguments() {
            return new Type[]{String.class};
        }

        @Override
        public Type getRawType() {
            return List.class;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }
    };
    private static final String[] GUI_SCALE_SETTINGS = new String[]{
        "options.guiScale.auto", "options.guiScale.small", "options.guiScale.normal", "options.guiScale.large"
    };
    private static final String[] PARTICLE_SETTINGS = new String[]{"options.particles.all", "options.particles.decreased", "options.particles.minimal"};
    private static final String[] AMBIENT_OCCLUSION_SETTINGS = new String[]{"options.ao.off", "options.ao.min", "options.ao.max"};
    private static final String[] STREAM_COMPRESSION_SETTINGS = new String[]{
        "options.stream.compression.low", "options.stream.compression.medium", "options.stream.compression.high"
    };
    private static final String[] STREAM_CHAT_ENABLED_SETTINGS = new String[]{
        "options.stream.chat.enabled.streaming", "options.stream.chat.enabled.always", "options.stream.chat.enabled.never"
    };
    private static final String[] STREAM_USER_FILTER_SETTINGS = new String[]{
        "options.stream.chat.userFilter.all", "options.stream.chat.userFilter.subs", "options.stream.chat.userFilter.mods"
    };
    private static final String[] STREAM_MIC_TOGGLE_SETTINGS = new String[]{"options.stream.mic_toggle.mute", "options.stream.mic_toggle.talk"};
    private static final String[] GRAPHICS_SETTINGS = new String[]{"options.off", "options.graphics.fast", "options.graphics.fancy"};
    public float mouseSensitivity = 0.5F;
    public boolean invertMouseY;
    public int viewDistance = -1;
    public boolean viewBobbing = true;
    public boolean anaglyph;
    public boolean fboEnable = true;
    public int fpsLimit = 120;
    public int cloudRenderMode = 2;
    public boolean fancyGraphics = true;
    public int ambientOcclusion = 2;
    public List<String> resourcePacks = Lists.newArrayList();
    public List<String> incompatibleResourcePacks = Lists.newArrayList();
    public PlayerEntity.ChatVisibility chatVisibility = PlayerEntity.ChatVisibility.FULL;
    public boolean chatColors = true;
    public boolean chatLinks = true;
    public boolean chatLinksPrompt = true;
    public float chatOpacity = 1.0F;
    public boolean snooperEnabled = true;
    public boolean fullscreen;
    public boolean vsync = true;
    public boolean useVbo = false;
    public boolean allowBlockAlternatives = true;
    public boolean reducedDebugInfo = false;
    public boolean hideServerAddress;
    public boolean advancedItemTooltips;
    public boolean pauseOnUnfocus = true;
    private final Set<PlayerModelPart> playerModelParts = Sets.newHashSet(PlayerModelPart.values());
    public boolean touchscreen;
    public int overrideWidth;
    public int overrideHeight;
    public boolean itemInHandTooltips = true;
    public float chatScale = 1.0F;
    public float chatWidth = 1.0F;
    public float unfocusedChatHeight = 0.44366196F;
    public float focusedChatHeight = 1.0F;
    public boolean showInventoryAchievementHint = true;
    public int mipmapLevels = 4;
    private Map<SoundCategory, Float> soundCategoryVolumes = Maps.newEnumMap(SoundCategory.class);
    public float streamBytesPerPixel = 0.5F;
    public float streamMicVolume = 1.0F;
    public float streamSystemVolume = 1.0F;
    public float streamKbps = 0.5412844F;
    public float streamFps = 0.31690142F;
    public int streamCompression = 1;
    public boolean streamSendMetadata = true;
    public String streamPreferredServer = "";
    public int streamChatEnabled = 0;
    public int streamChatUserFilter = 0;
    public int streamMicToggleBehavior = 0;
    public boolean useNativeTransport = true;
    public boolean renderClouds = true;
    public KeyBinding forwardKey = new KeyBinding("key.forward", 17, "key.categories.movement");
    public KeyBinding leftKey = new KeyBinding("key.left", 30, "key.categories.movement");
    public KeyBinding backKey = new KeyBinding("key.back", 31, "key.categories.movement");
    public KeyBinding rightKey = new KeyBinding("key.right", 32, "key.categories.movement");
    public KeyBinding jumpKey = new KeyBinding("key.jump", 57, "key.categories.movement");
    public KeyBinding sneakKey = new KeyBinding("key.sneak", 42, "key.categories.movement");
    public KeyBinding sprintKey = new KeyBinding("key.sprint", 29, "key.categories.movement");
    public KeyBinding inventoryKey = new KeyBinding("key.inventory", 18, "key.categories.inventory");
    public KeyBinding useKey = new KeyBinding("key.use", -99, "key.categories.gameplay");
    public KeyBinding dropKey = new KeyBinding("key.drop", 16, "key.categories.gameplay");
    public KeyBinding attackKey = new KeyBinding("key.attack", -100, "key.categories.gameplay");
    public KeyBinding pickItemKey = new KeyBinding("key.pickItem", -98, "key.categories.gameplay");
    public KeyBinding chatKey = new KeyBinding("key.chat", 20, "key.categories.multiplayer");
    public KeyBinding playerListKey = new KeyBinding("key.playerlist", 15, "key.categories.multiplayer");
    public KeyBinding commandKey = new KeyBinding("key.command", 53, "key.categories.multiplayer");
    public KeyBinding screenshotKey = new KeyBinding("key.screenshot", 60, "key.categories.misc");
    public KeyBinding togglePerspectiveKey = new KeyBinding("key.togglePerspective", 63, "key.categories.misc");
    public KeyBinding smoothCameraKey = new KeyBinding("key.smoothCamera", 0, "key.categories.misc");
    public KeyBinding fullscreenKey = new KeyBinding("key.fullscreen", 87, "key.categories.misc");
    public KeyBinding spectatorOutlinesKey = new KeyBinding("key.spectatorOutlines", 0, "key.categories.misc");
    public KeyBinding streamStartStopKey = new KeyBinding("key.streamStartStop", 64, "key.categories.stream");
    public KeyBinding streamPauseKey = new KeyBinding("key.streamPauseUnpause", 65, "key.categories.stream");
    public KeyBinding streamCommercialKey = new KeyBinding("key.streamCommercial", 0, "key.categories.stream");
    public KeyBinding streamToggleMicKey = new KeyBinding("key.streamToggleMic", 0, "key.categories.stream");
    public KeyBinding[] hotbarKeyBindings = new KeyBinding[]{
        new KeyBinding("key.hotbar.1", 2, "key.categories.inventory"),
        new KeyBinding("key.hotbar.2", 3, "key.categories.inventory"),
        new KeyBinding("key.hotbar.3", 4, "key.categories.inventory"),
        new KeyBinding("key.hotbar.4", 5, "key.categories.inventory"),
        new KeyBinding("key.hotbar.5", 6, "key.categories.inventory"),
        new KeyBinding("key.hotbar.6", 7, "key.categories.inventory"),
        new KeyBinding("key.hotbar.7", 8, "key.categories.inventory"),
        new KeyBinding("key.hotbar.8", 9, "key.categories.inventory"),
        new KeyBinding("key.hotbar.9", 10, "key.categories.inventory")
    };
    public KeyBinding[] keyBindings = ArrayUtils.addAll(
        new KeyBinding[]{
            this.attackKey,
            this.useKey,
            this.forwardKey,
            this.leftKey,
            this.backKey,
            this.rightKey,
            this.jumpKey,
            this.sneakKey,
            this.sprintKey,
            this.dropKey,
            this.inventoryKey,
            this.chatKey,
            this.playerListKey,
            this.pickItemKey,
            this.commandKey,
            this.screenshotKey,
            this.togglePerspectiveKey,
            this.smoothCameraKey,
            this.streamStartStopKey,
            this.streamPauseKey,
            this.streamCommercialKey,
            this.streamToggleMicKey,
            this.fullscreenKey,
            this.spectatorOutlinesKey
        },
        this.hotbarKeyBindings
    );
    protected Minecraft minecraft;
    private File file;
    public Difficulty difficulty = Difficulty.NORMAL;
    public boolean hideGui;
    /**
     * Camera perspective (0 = first person, 1 = second person, 2 = third person)
     */
    public int perspective;
    public boolean debugEnabled;
    public boolean debugProfilerEnabled;
    public boolean debugTpsEnabled;
    public String lastServer = "";
    public boolean smoothCamera;
    public boolean debugCamera;
    public float fov = 70.0F;
    public float gamma;
    public float saturation;
    public int guiScale;
    public int particles;
    public String language = "en_US";
    public boolean forceUnicodeFont = false;

    public GameOptions(Minecraft minecraft, File dir) {
        this.minecraft = minecraft;
        this.file = new File(dir, "options.txt");
        if (minecraft.is64Bit() && Runtime.getRuntime().maxMemory() >= 1000000000L) {
            GameOptions.Option.RENDER_DISTANCE.setMax(32.0F);
        } else {
            GameOptions.Option.RENDER_DISTANCE.setMax(16.0F);
        }

        this.viewDistance = minecraft.is64Bit() ? 12 : 8;
        this.load();
    }

    public GameOptions() {
    }

    public static String getKeyName(int keyCode) {
        if (keyCode < 0) {
            return I18n.translate("key.mouseButton", keyCode + 101);
        } else {
            return keyCode < 256 ? Keyboard.getKeyName(keyCode) : String.format("%c", (char)(keyCode - 256)).toUpperCase();
        }
    }

    public static boolean isPressed(KeyBinding keyBinding) {
        if (keyBinding.getKeyCode() == 0) {
            return false;
        } else {
            return keyBinding.getKeyCode() < 0 ? Mouse.isButtonDown(keyBinding.getKeyCode() + 100) : Keyboard.isKeyDown(keyBinding.getKeyCode());
        }
    }

    public void setKeyCode(KeyBinding keyBinding, int code) {
        keyBinding.setKeyCode(code);
        this.save();
    }

    public void set(GameOptions.Option option, float value) {
        if (option == GameOptions.Option.SENSITIVITY) {
            this.mouseSensitivity = value;
        }

        if (option == GameOptions.Option.FOV) {
            this.fov = value;
        }

        if (option == GameOptions.Option.GAMMA) {
            this.gamma = value;
        }

        if (option == GameOptions.Option.FRAMERATE_LIMIT) {
            this.fpsLimit = (int)value;
        }

        if (option == GameOptions.Option.CHAT_OPACITY) {
            this.chatOpacity = value;
            this.minecraft.gui.getChat().reset();
        }

        if (option == GameOptions.Option.CHAT_HEIGHT_FOCUSED) {
            this.focusedChatHeight = value;
            this.minecraft.gui.getChat().reset();
        }

        if (option == GameOptions.Option.CHAT_HEIGHT_UNFOCUSED) {
            this.unfocusedChatHeight = value;
            this.minecraft.gui.getChat().reset();
        }

        if (option == GameOptions.Option.CHAT_WIDTH) {
            this.chatWidth = value;
            this.minecraft.gui.getChat().reset();
        }

        if (option == GameOptions.Option.CHAT_SCALE) {
            this.chatScale = value;
            this.minecraft.gui.getChat().reset();
        }

        if (option == GameOptions.Option.MAPMAP_LEVELS) {
            int i = this.mipmapLevels;
            this.mipmapLevels = (int)value;
            if (i != value) {
                this.minecraft.getBlocksAtlas().setMaxMipLevel(this.mipmapLevels);
                this.minecraft.getTextureManager().bind(TextureAtlas.BLOCKS_LOCATION);
                this.minecraft.getBlocksAtlas().setFilter(false, this.mipmapLevels > 0);
                this.minecraft.reloadResourcesAsync();
            }
        }

        if (option == GameOptions.Option.BLOCK_ALTERNATIVES) {
            this.allowBlockAlternatives = !this.allowBlockAlternatives;
            this.minecraft.worldRenderer.reload();
        }

        if (option == GameOptions.Option.RENDER_DISTANCE) {
            this.viewDistance = (int)value;
            this.minecraft.worldRenderer.onViewChanged();
        }

        if (option == GameOptions.Option.STREAM_BYTES_PER_PIXEL) {
            this.streamBytesPerPixel = value;
        }

        if (option == GameOptions.Option.STREAM_VOLUME_MIC) {
            this.streamMicVolume = value;
            this.minecraft.getTwitchStream().updateVolume();
        }

        if (option == GameOptions.Option.STREAM_VOLUME_SYSTEM) {
            this.streamSystemVolume = value;
            this.minecraft.getTwitchStream().updateVolume();
        }

        if (option == GameOptions.Option.STREAM_KBPS) {
            this.streamKbps = value;
        }

        if (option == GameOptions.Option.STREAM_FPS) {
            this.streamFps = value;
        }
    }

    public void set(GameOptions.Option option, int value) {
        if (option == GameOptions.Option.INVERT_MOUSE) {
            this.invertMouseY = !this.invertMouseY;
        }

        if (option == GameOptions.Option.GUI_SCALE) {
            this.guiScale = this.guiScale + value & 3;
        }

        if (option == GameOptions.Option.PARTICLES) {
            this.particles = (this.particles + value) % 3;
        }

        if (option == GameOptions.Option.VIEW_BOBBING) {
            this.viewBobbing = !this.viewBobbing;
        }

        if (option == GameOptions.Option.RENDER_CLOUDS) {
            this.cloudRenderMode = (this.cloudRenderMode + value) % 3;
        }

        if (option == GameOptions.Option.FORCE_UNICODE_FONT) {
            this.forceUnicodeFont = !this.forceUnicodeFont;
            this.minecraft.textRenderer.setUnicode(this.minecraft.getLanguageManager().isUnicode() || this.forceUnicodeFont);
        }

        if (option == GameOptions.Option.FBO_ENABLE) {
            this.fboEnable = !this.fboEnable;
        }

        if (option == GameOptions.Option.ANAGLYPH) {
            this.anaglyph = !this.anaglyph;
            this.minecraft.reloadResources();
        }

        if (option == GameOptions.Option.GRAPHICS) {
            this.fancyGraphics = !this.fancyGraphics;
            this.minecraft.worldRenderer.reload();
        }

        if (option == GameOptions.Option.AMBIENT_OCCLUSION) {
            this.ambientOcclusion = (this.ambientOcclusion + value) % 3;
            this.minecraft.worldRenderer.reload();
        }

        if (option == GameOptions.Option.CHAT_VISIBILITY) {
            this.chatVisibility = PlayerEntity.ChatVisibility.byIndex((this.chatVisibility.getIndex() + value) % 3);
        }

        if (option == GameOptions.Option.STREAM_COMPRESSION) {
            this.streamCompression = (this.streamCompression + value) % 3;
        }

        if (option == GameOptions.Option.STREAM_SEND_METADATA) {
            this.streamSendMetadata = !this.streamSendMetadata;
        }

        if (option == GameOptions.Option.STREAM_CHAT_ENABLED) {
            this.streamChatEnabled = (this.streamChatEnabled + value) % 3;
        }

        if (option == GameOptions.Option.STREAM_CHAT_USER_FILTER) {
            this.streamChatUserFilter = (this.streamChatUserFilter + value) % 3;
        }

        if (option == GameOptions.Option.STREAM_MIC_TOGGLE_BEHAVIOR) {
            this.streamMicToggleBehavior = (this.streamMicToggleBehavior + value) % 2;
        }

        if (option == GameOptions.Option.CHAT_COLOR) {
            this.chatColors = !this.chatColors;
        }

        if (option == GameOptions.Option.CHAT_LINKS) {
            this.chatLinks = !this.chatLinks;
        }

        if (option == GameOptions.Option.CHAT_LINKS_PROMPT) {
            this.chatLinksPrompt = !this.chatLinksPrompt;
        }

        if (option == GameOptions.Option.SNOOPER_ENABLED) {
            this.snooperEnabled = !this.snooperEnabled;
        }

        if (option == GameOptions.Option.TOUCHSCREEN) {
            this.touchscreen = !this.touchscreen;
        }

        if (option == GameOptions.Option.USE_FULLSCREEN) {
            this.fullscreen = !this.fullscreen;
            if (this.minecraft.isFullscreen() != this.fullscreen) {
                this.minecraft.toggleFullscreen();
            }
        }

        if (option == GameOptions.Option.ENABLE_VSYNC) {
            this.vsync = !this.vsync;
            Display.setVSyncEnabled(this.vsync);
        }

        if (option == GameOptions.Option.USE_VBO) {
            this.useVbo = !this.useVbo;
            this.minecraft.worldRenderer.reload();
        }

        if (option == GameOptions.Option.BLOCK_ALTERNATIVES) {
            this.allowBlockAlternatives = !this.allowBlockAlternatives;
            this.minecraft.worldRenderer.reload();
        }

        if (option == GameOptions.Option.REDUCED_DEBUG_INFO) {
            this.reducedDebugInfo = !this.reducedDebugInfo;
        }

        if (option == GameOptions.Option.ENTITY_SHADOWS) {
            this.renderClouds = !this.renderClouds;
        }

        this.save();
    }

    public float getFloat(GameOptions.Option option) {
        if (option == GameOptions.Option.FOV) {
            return this.fov;
        } else if (option == GameOptions.Option.GAMMA) {
            return this.gamma;
        } else if (option == GameOptions.Option.SATURATION) {
            return this.saturation;
        } else if (option == GameOptions.Option.SENSITIVITY) {
            return this.mouseSensitivity;
        } else if (option == GameOptions.Option.CHAT_OPACITY) {
            return this.chatOpacity;
        } else if (option == GameOptions.Option.CHAT_HEIGHT_FOCUSED) {
            return this.focusedChatHeight;
        } else if (option == GameOptions.Option.CHAT_HEIGHT_UNFOCUSED) {
            return this.unfocusedChatHeight;
        } else if (option == GameOptions.Option.CHAT_SCALE) {
            return this.chatScale;
        } else if (option == GameOptions.Option.CHAT_WIDTH) {
            return this.chatWidth;
        } else if (option == GameOptions.Option.FRAMERATE_LIMIT) {
            return this.fpsLimit;
        } else if (option == GameOptions.Option.MAPMAP_LEVELS) {
            return this.mipmapLevels;
        } else if (option == GameOptions.Option.RENDER_DISTANCE) {
            return this.viewDistance;
        } else if (option == GameOptions.Option.STREAM_BYTES_PER_PIXEL) {
            return this.streamBytesPerPixel;
        } else if (option == GameOptions.Option.STREAM_VOLUME_MIC) {
            return this.streamMicVolume;
        } else if (option == GameOptions.Option.STREAM_VOLUME_SYSTEM) {
            return this.streamSystemVolume;
        } else if (option == GameOptions.Option.STREAM_KBPS) {
            return this.streamKbps;
        } else {
            return option == GameOptions.Option.STREAM_FPS ? this.streamFps : 0.0F;
        }
    }

    public boolean getBoolean(GameOptions.Option option) {
        switch (option) {
            case INVERT_MOUSE:
                return this.invertMouseY;
            case VIEW_BOBBING:
                return this.viewBobbing;
            case ANAGLYPH:
                return this.anaglyph;
            case FBO_ENABLE:
                return this.fboEnable;
            case CHAT_COLOR:
                return this.chatColors;
            case CHAT_LINKS:
                return this.chatLinks;
            case CHAT_LINKS_PROMPT:
                return this.chatLinksPrompt;
            case SNOOPER_ENABLED:
                return this.snooperEnabled;
            case USE_FULLSCREEN:
                return this.fullscreen;
            case ENABLE_VSYNC:
                return this.vsync;
            case USE_VBO:
                return this.useVbo;
            case TOUCHSCREEN:
                return this.touchscreen;
            case STREAM_SEND_METADATA:
                return this.streamSendMetadata;
            case FORCE_UNICODE_FONT:
                return this.forceUnicodeFont;
            case BLOCK_ALTERNATIVES:
                return this.allowBlockAlternatives;
            case REDUCED_DEBUG_INFO:
                return this.reducedDebugInfo;
            case ENTITY_SHADOWS:
                return this.renderClouds;
            default:
                return false;
        }
    }

    private static String translateIntegerValue(String[] translationKeys, int value) {
        if (value < 0 || value >= translationKeys.length) {
            value = 0;
        }

        return I18n.translate(translationKeys[value]);
    }

    public String getAsString(GameOptions.Option option) {
        String s = I18n.translate(option.getName()) + ": ";
        if (option.isFloat()) {
            float f1 = this.getFloat(option);
            float f = option.normalize(f1);
            if (option == GameOptions.Option.SENSITIVITY) {
                if (f == 0.0F) {
                    return s + I18n.translate("options.sensitivity.min");
                } else {
                    return f == 1.0F ? s + I18n.translate("options.sensitivity.max") : s + (int)(f * 200.0F) + "%";
                }
            } else if (option == GameOptions.Option.FOV) {
                if (f1 == 70.0F) {
                    return s + I18n.translate("options.fov.min");
                } else {
                    return f1 == 110.0F ? s + I18n.translate("options.fov.max") : s + (int)f1;
                }
            } else {
                if (option == GameOptions.Option.FRAMERATE_LIMIT) {
                    return f1 == option.max ? s + I18n.translate("options.framerateLimit.max") : s + (int)f1 + " fps";
                }

                if (option == GameOptions.Option.RENDER_CLOUDS) {
                    return f1 == option.min ? s + I18n.translate("options.cloudHeight.min") : s + ((int)f1 + 128);
                }

                if (option == GameOptions.Option.GAMMA) {
                    if (f == 0.0F) {
                        return s + I18n.translate("options.gamma.min");
                    } else {
                        return f == 1.0F ? s + I18n.translate("options.gamma.max") : s + "+" + (int)(f * 100.0F) + "%";
                    }
                } else if (option == GameOptions.Option.SATURATION) {
                    return s + (int)(f * 400.0F) + "%";
                } else if (option == GameOptions.Option.CHAT_OPACITY) {
                    return s + (int)(f * 90.0F + 10.0F) + "%";
                } else if (option == GameOptions.Option.CHAT_HEIGHT_UNFOCUSED) {
                    return s + ChatGui.getHeight(f) + "px";
                } else if (option == GameOptions.Option.CHAT_HEIGHT_FOCUSED) {
                    return s + ChatGui.getHeight(f) + "px";
                } else if (option == GameOptions.Option.CHAT_WIDTH) {
                    return s + ChatGui.getWidth(f) + "px";
                } else if (option == GameOptions.Option.RENDER_DISTANCE) {
                    return s + (int)f1 + " chunks";
                } else if (option == GameOptions.Option.MAPMAP_LEVELS) {
                    return f1 == 0.0F ? s + I18n.translate("options.off") : s + (int)f1;
                } else if (option == GameOptions.Option.STREAM_FPS) {
                    return s + Twitch.m_7422955(f) + " fps";
                } else if (option == GameOptions.Option.STREAM_KBPS) {
                    return s + Twitch.m_1012270(f) + " Kbps";
                } else if (option == GameOptions.Option.STREAM_BYTES_PER_PIXEL) {
                    return s + String.format("%.3f bpp", Twitch.m_2187119(f));
                } else {
                    return f == 0.0F ? s + I18n.translate("options.off") : s + (int)(f * 100.0F) + "%";
                }
            }
        } else {
            if (option.isBoolean()) {
                boolean flag = this.getBoolean(option);
                return flag ? s + I18n.translate("options.on") : s + I18n.translate("options.off");
            }

            if (option == GameOptions.Option.GUI_SCALE) {
                return s + translateIntegerValue(GUI_SCALE_SETTINGS, this.guiScale);
            }

            if (option == GameOptions.Option.CHAT_VISIBILITY) {
                return s + I18n.translate(this.chatVisibility.getId());
            }

            if (option == GameOptions.Option.PARTICLES) {
                return s + translateIntegerValue(PARTICLE_SETTINGS, this.particles);
            }

            if (option == GameOptions.Option.AMBIENT_OCCLUSION) {
                return s + translateIntegerValue(AMBIENT_OCCLUSION_SETTINGS, this.ambientOcclusion);
            }

            if (option == GameOptions.Option.STREAM_COMPRESSION) {
                return s + translateIntegerValue(STREAM_COMPRESSION_SETTINGS, this.streamCompression);
            }

            if (option == GameOptions.Option.STREAM_CHAT_ENABLED) {
                return s + translateIntegerValue(STREAM_CHAT_ENABLED_SETTINGS, this.streamChatEnabled);
            }

            if (option == GameOptions.Option.STREAM_CHAT_USER_FILTER) {
                return s + translateIntegerValue(STREAM_USER_FILTER_SETTINGS, this.streamChatUserFilter);
            }

            if (option == GameOptions.Option.STREAM_MIC_TOGGLE_BEHAVIOR) {
                return s + translateIntegerValue(STREAM_MIC_TOGGLE_SETTINGS, this.streamMicToggleBehavior);
            }

            if (option == GameOptions.Option.RENDER_CLOUDS) {
                return s + translateIntegerValue(GRAPHICS_SETTINGS, this.cloudRenderMode);
            }

            if (option == GameOptions.Option.GRAPHICS) {
                if (this.fancyGraphics) {
                    return s + I18n.translate("options.graphics.fancy");
                }

                String s1 = "options.graphics.fast";
                return s + I18n.translate("options.graphics.fast");
            } else {
                return s;
            }
        }
    }

    public void load() {
        try {
            if (!this.file.exists()) {
                return;
            }

            BufferedReader bufferedreader = new BufferedReader(new FileReader(this.file));
            String s = "";
            this.soundCategoryVolumes.clear();

            while ((s = bufferedreader.readLine()) != null) {
                try {
                    String[] astring = s.split(":");
                    if (astring[0].equals("mouseSensitivity")) {
                        this.mouseSensitivity = this.parseFloat(astring[1]);
                    }

                    if (astring[0].equals("fov")) {
                        this.fov = this.parseFloat(astring[1]) * 40.0F + 70.0F;
                    }

                    if (astring[0].equals("gamma")) {
                        this.gamma = this.parseFloat(astring[1]);
                    }

                    if (astring[0].equals("saturation")) {
                        this.saturation = this.parseFloat(astring[1]);
                    }

                    if (astring[0].equals("invertYMouse")) {
                        this.invertMouseY = astring[1].equals("true");
                    }

                    if (astring[0].equals("renderDistance")) {
                        this.viewDistance = Integer.parseInt(astring[1]);
                    }

                    if (astring[0].equals("guiScale")) {
                        this.guiScale = Integer.parseInt(astring[1]);
                    }

                    if (astring[0].equals("particles")) {
                        this.particles = Integer.parseInt(astring[1]);
                    }

                    if (astring[0].equals("bobView")) {
                        this.viewBobbing = astring[1].equals("true");
                    }

                    if (astring[0].equals("anaglyph3d")) {
                        this.anaglyph = astring[1].equals("true");
                    }

                    if (astring[0].equals("maxFps")) {
                        this.fpsLimit = Integer.parseInt(astring[1]);
                    }

                    if (astring[0].equals("fboEnable")) {
                        this.fboEnable = astring[1].equals("true");
                    }

                    if (astring[0].equals("difficulty")) {
                        this.difficulty = Difficulty.byId(Integer.parseInt(astring[1]));
                    }

                    if (astring[0].equals("fancyGraphics")) {
                        this.fancyGraphics = astring[1].equals("true");
                    }

                    if (astring[0].equals("ao")) {
                        if (astring[1].equals("true")) {
                            this.ambientOcclusion = 2;
                        } else if (astring[1].equals("false")) {
                            this.ambientOcclusion = 0;
                        } else {
                            this.ambientOcclusion = Integer.parseInt(astring[1]);
                        }
                    }

                    if (astring[0].equals("renderClouds")) {
                        if (astring[1].equals("true")) {
                            this.cloudRenderMode = 2;
                        } else if (astring[1].equals("false")) {
                            this.cloudRenderMode = 0;
                        } else if (astring[1].equals("fast")) {
                            this.cloudRenderMode = 1;
                        }
                    }

                    if (astring[0].equals("resourcePacks")) {
                        this.resourcePacks = GSON.fromJson(s.substring(s.indexOf(58) + 1), STRING_LIST_TYPE);
                        if (this.resourcePacks == null) {
                            this.resourcePacks = Lists.newArrayList();
                        }
                    }

                    if (astring[0].equals("incompatibleResourcePacks")) {
                        this.incompatibleResourcePacks = GSON.fromJson(s.substring(s.indexOf(58) + 1), STRING_LIST_TYPE);
                        if (this.incompatibleResourcePacks == null) {
                            this.incompatibleResourcePacks = Lists.newArrayList();
                        }
                    }

                    if (astring[0].equals("lastServer") && astring.length >= 2) {
                        this.lastServer = s.substring(s.indexOf(58) + 1);
                    }

                    if (astring[0].equals("lang") && astring.length >= 2) {
                        this.language = astring[1];
                    }

                    if (astring[0].equals("chatVisibility")) {
                        this.chatVisibility = PlayerEntity.ChatVisibility.byIndex(Integer.parseInt(astring[1]));
                    }

                    if (astring[0].equals("chatColors")) {
                        this.chatColors = astring[1].equals("true");
                    }

                    if (astring[0].equals("chatLinks")) {
                        this.chatLinks = astring[1].equals("true");
                    }

                    if (astring[0].equals("chatLinksPrompt")) {
                        this.chatLinksPrompt = astring[1].equals("true");
                    }

                    if (astring[0].equals("chatOpacity")) {
                        this.chatOpacity = this.parseFloat(astring[1]);
                    }

                    if (astring[0].equals("snooperEnabled")) {
                        this.snooperEnabled = astring[1].equals("true");
                    }

                    if (astring[0].equals("fullscreen")) {
                        this.fullscreen = astring[1].equals("true");
                    }

                    if (astring[0].equals("enableVsync")) {
                        this.vsync = astring[1].equals("true");
                    }

                    if (astring[0].equals("useVbo")) {
                        this.useVbo = astring[1].equals("true");
                    }

                    if (astring[0].equals("hideServerAddress")) {
                        this.hideServerAddress = astring[1].equals("true");
                    }

                    if (astring[0].equals("advancedItemTooltips")) {
                        this.advancedItemTooltips = astring[1].equals("true");
                    }

                    if (astring[0].equals("pauseOnLostFocus")) {
                        this.pauseOnUnfocus = astring[1].equals("true");
                    }

                    if (astring[0].equals("touchscreen")) {
                        this.touchscreen = astring[1].equals("true");
                    }

                    if (astring[0].equals("overrideHeight")) {
                        this.overrideHeight = Integer.parseInt(astring[1]);
                    }

                    if (astring[0].equals("overrideWidth")) {
                        this.overrideWidth = Integer.parseInt(astring[1]);
                    }

                    if (astring[0].equals("heldItemTooltips")) {
                        this.itemInHandTooltips = astring[1].equals("true");
                    }

                    if (astring[0].equals("chatHeightFocused")) {
                        this.focusedChatHeight = this.parseFloat(astring[1]);
                    }

                    if (astring[0].equals("chatHeightUnfocused")) {
                        this.unfocusedChatHeight = this.parseFloat(astring[1]);
                    }

                    if (astring[0].equals("chatScale")) {
                        this.chatScale = this.parseFloat(astring[1]);
                    }

                    if (astring[0].equals("chatWidth")) {
                        this.chatWidth = this.parseFloat(astring[1]);
                    }

                    if (astring[0].equals("showInventoryAchievementHint")) {
                        this.showInventoryAchievementHint = astring[1].equals("true");
                    }

                    if (astring[0].equals("mipmapLevels")) {
                        this.mipmapLevels = Integer.parseInt(astring[1]);
                    }

                    if (astring[0].equals("streamBytesPerPixel")) {
                        this.streamBytesPerPixel = this.parseFloat(astring[1]);
                    }

                    if (astring[0].equals("streamMicVolume")) {
                        this.streamMicVolume = this.parseFloat(astring[1]);
                    }

                    if (astring[0].equals("streamSystemVolume")) {
                        this.streamSystemVolume = this.parseFloat(astring[1]);
                    }

                    if (astring[0].equals("streamKbps")) {
                        this.streamKbps = this.parseFloat(astring[1]);
                    }

                    if (astring[0].equals("streamFps")) {
                        this.streamFps = this.parseFloat(astring[1]);
                    }

                    if (astring[0].equals("streamCompression")) {
                        this.streamCompression = Integer.parseInt(astring[1]);
                    }

                    if (astring[0].equals("streamSendMetadata")) {
                        this.streamSendMetadata = astring[1].equals("true");
                    }

                    if (astring[0].equals("streamPreferredServer") && astring.length >= 2) {
                        this.streamPreferredServer = s.substring(s.indexOf(58) + 1);
                    }

                    if (astring[0].equals("streamChatEnabled")) {
                        this.streamChatEnabled = Integer.parseInt(astring[1]);
                    }

                    if (astring[0].equals("streamChatUserFilter")) {
                        this.streamChatUserFilter = Integer.parseInt(astring[1]);
                    }

                    if (astring[0].equals("streamMicToggleBehavior")) {
                        this.streamMicToggleBehavior = Integer.parseInt(astring[1]);
                    }

                    if (astring[0].equals("forceUnicodeFont")) {
                        this.forceUnicodeFont = astring[1].equals("true");
                    }

                    if (astring[0].equals("allowBlockAlternatives")) {
                        this.allowBlockAlternatives = astring[1].equals("true");
                    }

                    if (astring[0].equals("reducedDebugInfo")) {
                        this.reducedDebugInfo = astring[1].equals("true");
                    }

                    if (astring[0].equals("useNativeTransport")) {
                        this.useNativeTransport = astring[1].equals("true");
                    }

                    if (astring[0].equals("entityShadows")) {
                        this.renderClouds = astring[1].equals("true");
                    }

                    for (KeyBinding keybinding : this.keyBindings) {
                        if (astring[0].equals("key_" + keybinding.getName())) {
                            keybinding.setKeyCode(Integer.parseInt(astring[1]));
                        }
                    }

                    for (SoundCategory soundcategory : SoundCategory.values()) {
                        if (astring[0].equals("soundCategory_" + soundcategory.getName())) {
                            this.soundCategoryVolumes.put(soundcategory, this.parseFloat(astring[1]));
                        }
                    }

                    for (PlayerModelPart playermodelpart : PlayerModelPart.values()) {
                        if (astring[0].equals("modelPart_" + playermodelpart.getKey())) {
                            this.setPlayerModelPart(playermodelpart, astring[1].equals("true"));
                        }
                    }
                } catch (Exception exception) {
                    LOGGER.warn("Skipping bad option: " + s);
                }
            }

            KeyBinding.resetMapping();
            bufferedreader.close();
        } catch (Exception exception1) {
            LOGGER.error("Failed to load options", exception1);
        }
    }

    private float parseFloat(String s) {
        if (s.equals("true")) {
            return 1.0F;
        } else {
            return s.equals("false") ? 0.0F : Float.parseFloat(s);
        }
    }

    public void save() {
        try {
            PrintWriter printwriter = new PrintWriter(new FileWriter(this.file));
            printwriter.println("invertYMouse:" + this.invertMouseY);
            printwriter.println("mouseSensitivity:" + this.mouseSensitivity);
            printwriter.println("fov:" + (this.fov - 70.0F) / 40.0F);
            printwriter.println("gamma:" + this.gamma);
            printwriter.println("saturation:" + this.saturation);
            printwriter.println("renderDistance:" + this.viewDistance);
            printwriter.println("guiScale:" + this.guiScale);
            printwriter.println("particles:" + this.particles);
            printwriter.println("bobView:" + this.viewBobbing);
            printwriter.println("anaglyph3d:" + this.anaglyph);
            printwriter.println("maxFps:" + this.fpsLimit);
            printwriter.println("fboEnable:" + this.fboEnable);
            printwriter.println("difficulty:" + this.difficulty.getId());
            printwriter.println("fancyGraphics:" + this.fancyGraphics);
            printwriter.println("ao:" + this.ambientOcclusion);
            switch (this.cloudRenderMode) {
                case 0:
                    printwriter.println("renderClouds:false");
                    break;
                case 1:
                    printwriter.println("renderClouds:fast");
                    break;
                case 2:
                    printwriter.println("renderClouds:true");
            }

            printwriter.println("resourcePacks:" + GSON.toJson(this.resourcePacks));
            printwriter.println("incompatibleResourcePacks:" + GSON.toJson(this.incompatibleResourcePacks));
            printwriter.println("lastServer:" + this.lastServer);
            printwriter.println("lang:" + this.language);
            printwriter.println("chatVisibility:" + this.chatVisibility.getIndex());
            printwriter.println("chatColors:" + this.chatColors);
            printwriter.println("chatLinks:" + this.chatLinks);
            printwriter.println("chatLinksPrompt:" + this.chatLinksPrompt);
            printwriter.println("chatOpacity:" + this.chatOpacity);
            printwriter.println("snooperEnabled:" + this.snooperEnabled);
            printwriter.println("fullscreen:" + this.fullscreen);
            printwriter.println("enableVsync:" + this.vsync);
            printwriter.println("useVbo:" + this.useVbo);
            printwriter.println("hideServerAddress:" + this.hideServerAddress);
            printwriter.println("advancedItemTooltips:" + this.advancedItemTooltips);
            printwriter.println("pauseOnLostFocus:" + this.pauseOnUnfocus);
            printwriter.println("touchscreen:" + this.touchscreen);
            printwriter.println("overrideWidth:" + this.overrideWidth);
            printwriter.println("overrideHeight:" + this.overrideHeight);
            printwriter.println("heldItemTooltips:" + this.itemInHandTooltips);
            printwriter.println("chatHeightFocused:" + this.focusedChatHeight);
            printwriter.println("chatHeightUnfocused:" + this.unfocusedChatHeight);
            printwriter.println("chatScale:" + this.chatScale);
            printwriter.println("chatWidth:" + this.chatWidth);
            printwriter.println("showInventoryAchievementHint:" + this.showInventoryAchievementHint);
            printwriter.println("mipmapLevels:" + this.mipmapLevels);
            printwriter.println("streamBytesPerPixel:" + this.streamBytesPerPixel);
            printwriter.println("streamMicVolume:" + this.streamMicVolume);
            printwriter.println("streamSystemVolume:" + this.streamSystemVolume);
            printwriter.println("streamKbps:" + this.streamKbps);
            printwriter.println("streamFps:" + this.streamFps);
            printwriter.println("streamCompression:" + this.streamCompression);
            printwriter.println("streamSendMetadata:" + this.streamSendMetadata);
            printwriter.println("streamPreferredServer:" + this.streamPreferredServer);
            printwriter.println("streamChatEnabled:" + this.streamChatEnabled);
            printwriter.println("streamChatUserFilter:" + this.streamChatUserFilter);
            printwriter.println("streamMicToggleBehavior:" + this.streamMicToggleBehavior);
            printwriter.println("forceUnicodeFont:" + this.forceUnicodeFont);
            printwriter.println("allowBlockAlternatives:" + this.allowBlockAlternatives);
            printwriter.println("reducedDebugInfo:" + this.reducedDebugInfo);
            printwriter.println("useNativeTransport:" + this.useNativeTransport);
            printwriter.println("entityShadows:" + this.renderClouds);

            for (KeyBinding keybinding : this.keyBindings) {
                printwriter.println("key_" + keybinding.getName() + ":" + keybinding.getKeyCode());
            }

            for (SoundCategory soundcategory : SoundCategory.values()) {
                printwriter.println("soundCategory_" + soundcategory.getName() + ":" + this.getSoundCategoryVolume(soundcategory));
            }

            for (PlayerModelPart playermodelpart : PlayerModelPart.values()) {
                printwriter.println("modelPart_" + playermodelpart.getKey() + ":" + this.playerModelParts.contains(playermodelpart));
            }

            printwriter.close();
        } catch (Exception exception) {
            LOGGER.error("Failed to save options", exception);
        }

        this.syncClientSettings();
    }

    public float getSoundCategoryVolume(SoundCategory category) {
        return this.soundCategoryVolumes.containsKey(category) ? this.soundCategoryVolumes.get(category) : 1.0F;
    }

    public void setSoundCategoryVolume(SoundCategory category, float volume) {
        this.minecraft.getSoundManager().setVolume(category, volume);
        this.soundCategoryVolumes.put(category, volume);
    }

    public void syncClientSettings() {
        if (this.minecraft.player != null) {
            int i = 0;

            for (PlayerModelPart playermodelpart : this.playerModelParts) {
                i |= playermodelpart.getFlag();
            }

            this.minecraft
                .player
                .networkHandler
                .sendPacket(new ClientSettingsC2SPacket(this.language, this.viewDistance, this.chatVisibility, this.chatColors, i));
        }
    }

    public Set<PlayerModelPart> getPlayerModelParts() {
        return ImmutableSet.copyOf(this.playerModelParts);
    }

    public void setPlayerModelPart(PlayerModelPart part, boolean enable) {
        if (enable) {
            this.playerModelParts.add(part);
        } else {
            this.playerModelParts.remove(part);
        }

        this.syncClientSettings();
    }

    public void togglePlayerModelPart(PlayerModelPart part) {
        if (!this.getPlayerModelParts().contains(part)) {
            this.playerModelParts.add(part);
        } else {
            this.playerModelParts.remove(part);
        }

        this.syncClientSettings();
    }

    public int getCloudRenderMode() {
        return this.viewDistance >= 4 ? this.cloudRenderMode : 0;
    }

    public boolean shouldUseNativeTransport() {
        return this.useNativeTransport;
    }

    public enum Option {
        INVERT_MOUSE("options.invertMouse", false, true),
        SENSITIVITY("options.sensitivity", true, false),
        FOV("options.fov", true, false, 30.0F, 110.0F, 1.0F),
        GAMMA("options.gamma", true, false),
        SATURATION("options.saturation", true, false),
        RENDER_DISTANCE("options.renderDistance", true, false, 2.0F, 16.0F, 1.0F),
        VIEW_BOBBING("options.viewBobbing", false, true),
        ANAGLYPH("options.anaglyph", false, true),
        FRAMERATE_LIMIT("options.framerateLimit", true, false, 10.0F, 260.0F, 10.0F),
        FBO_ENABLE("options.fboEnable", false, true),
        RENDER_CLOUDS("options.renderClouds", false, false),
        GRAPHICS("options.graphics", false, false),
        AMBIENT_OCCLUSION("options.ao", false, false),
        GUI_SCALE("options.guiScale", false, false),
        PARTICLES("options.particles", false, false),
        CHAT_VISIBILITY("options.chat.visibility", false, false),
        CHAT_COLOR("options.chat.color", false, true),
        CHAT_LINKS("options.chat.links", false, true),
        CHAT_OPACITY("options.chat.opacity", true, false),
        CHAT_LINKS_PROMPT("options.chat.links.prompt", false, true),
        SNOOPER_ENABLED("options.snooper", false, true),
        USE_FULLSCREEN("options.fullscreen", false, true),
        ENABLE_VSYNC("options.vsync", false, true),
        USE_VBO("options.vbo", false, true),
        TOUCHSCREEN("options.touchscreen", false, true),
        CHAT_SCALE("options.chat.scale", true, false),
        CHAT_WIDTH("options.chat.width", true, false),
        CHAT_HEIGHT_FOCUSED("options.chat.height.focused", true, false),
        CHAT_HEIGHT_UNFOCUSED("options.chat.height.unfocused", true, false),
        MAPMAP_LEVELS("options.mipmapLevels", true, false, 0.0F, 4.0F, 1.0F),
        FORCE_UNICODE_FONT("options.forceUnicodeFont", false, true),
        STREAM_BYTES_PER_PIXEL("options.stream.bytesPerPixel", true, false),
        STREAM_VOLUME_MIC("options.stream.micVolumne", true, false),
        STREAM_VOLUME_SYSTEM("options.stream.systemVolume", true, false),
        STREAM_KBPS("options.stream.kbps", true, false),
        STREAM_FPS("options.stream.fps", true, false),
        STREAM_COMPRESSION("options.stream.compression", false, false),
        STREAM_SEND_METADATA("options.stream.sendMetadata", false, true),
        STREAM_CHAT_ENABLED("options.stream.chat.enabled", false, false),
        STREAM_CHAT_USER_FILTER("options.stream.chat.userFilter", false, false),
        STREAM_MIC_TOGGLE_BEHAVIOR("options.stream.micToggleBehavior", false, false),
        BLOCK_ALTERNATIVES("options.blockAlternatives", false, true),
        REDUCED_DEBUG_INFO("options.reducedDebugInfo", false, true),
        ENTITY_SHADOWS("options.entityShadows", false, true);

        private final boolean isFloat;
        private final boolean isBoolean;
        private final String name;
        private final float step;
        private float min;
        private float max;

        public static GameOptions.Option byId(int id) {
            for (GameOptions.Option gameoptions$option : values()) {
                if (gameoptions$option.getId() == id) {
                    return gameoptions$option;
                }
            }

            return null;
        }

        Option(String name, boolean isFloat, boolean isBoolean) {
            this(name, isFloat, isBoolean, 0.0F, 1.0F, 0.0F);
        }

        Option(String name, boolean isFloat, boolean isBoolean, float min, float max, float step) {
            this.name = name;
            this.isFloat = isFloat;
            this.isBoolean = isBoolean;
            this.min = min;
            this.max = max;
            this.step = step;
        }

        public boolean isFloat() {
            return this.isFloat;
        }

        public boolean isBoolean() {
            return this.isBoolean;
        }

        public int getId() {
            return this.ordinal();
        }

        public String getName() {
            return this.name;
        }

        public float getMax() {
            return this.max;
        }

        public void setMax(float value) {
            this.max = value;
        }

        public float normalize(float value) {
            return MathHelper.clamp((this.clampAndRoundToStepMultiple(value) - this.min) / (this.max - this.min), 0.0F, 1.0F);
        }

        public float denormalize(float value) {
            return this.clampAndRoundToStepMultiple(this.min + (this.max - this.min) * MathHelper.clamp(value, 0.0F, 1.0F));
        }

        public float clampAndRoundToStepMultiple(float value) {
            value = this.roundToStepMultiple(value);
            return MathHelper.clamp(value, this.min, this.max);
        }

        protected float roundToStepMultiple(float value) {
            if (this.step > 0.0F) {
                value = this.step * Math.round(value / this.step);
            }

            return value;
        }
    }
}
