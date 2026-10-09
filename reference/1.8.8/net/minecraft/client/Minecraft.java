package net.minecraft.client;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.net.SocketAddress;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import javax.imageio.ImageIO;
import net.minecraft.Bootstrap;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.client.entity.living.player.KeyboardInput;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.gui.BossBar;
import net.minecraft.client.gui.GameGui;
import net.minecraft.client.gui.ToastGui;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.ConfirmationListener;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.OutOfMemoryScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SleepingChatScreen;
import net.minecraft.client.gui.screen.StreamUnavailableScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.inventory.menu.SurvivalInventoryScreen;
import net.minecraft.client.gui.screen.options.ControlsOptionsScreen;
import net.minecraft.client.main.RunArgs;
import net.minecraft.client.network.handler.ClientLoginNetworkHandler;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.options.ServerListEntry;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.ItemInHandRenderer;
import net.minecraft.client.render.ProgressRenderError;
import net.minecraft.client.render.ProgressRenderer;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.block.BlockRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.client.resource.AssetIndex;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.resource.language.LanguageManager;
import net.minecraft.client.resource.manager.ReloadableResourceManager;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.client.resource.manager.SimpleReloadableResourceManager;
import net.minecraft.client.resource.metadata.AnimationMetadata;
import net.minecraft.client.resource.metadata.FontMetadata;
import net.minecraft.client.resource.metadata.LanguageMetadata;
import net.minecraft.client.resource.metadata.ResourceMetadataSerializerRegistry;
import net.minecraft.client.resource.metadata.ResourcePackMetadata;
import net.minecraft.client.resource.metadata.TextureMetadata;
import net.minecraft.client.resource.metadata.serializer.AnimationMetadataSerializer;
import net.minecraft.client.resource.metadata.serializer.FontMetadataSerializer;
import net.minecraft.client.resource.metadata.serializer.LanguageMetadataSerializer;
import net.minecraft.client.resource.metadata.serializer.PackMetadataSerializer;
import net.minecraft.client.resource.metadata.serializer.TextureMetadataSerializer;
import net.minecraft.client.resource.model.ModelManager;
import net.minecraft.client.resource.pack.BuiltInResourcePack;
import net.minecraft.client.resource.pack.ResourcePack;
import net.minecraft.client.resource.pack.ResourcePacks;
import net.minecraft.client.resource.skin.SkinManager;
import net.minecraft.client.sound.MusicManager;
import net.minecraft.client.sound.system.SoundManager;
import net.minecraft.client.twitch.ErrorTwitchStream;
import net.minecraft.client.twitch.Twitch;
import net.minecraft.client.twitch.TwitchStream;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.world.color.FoliageColorReloader;
import net.minecraft.client.world.color.GrassColorReloader;
import net.minecraft.entity.Entities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.LeadKnotEntity;
import net.minecraft.entity.decoration.PaintingEntity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.Connection;
import net.minecraft.network.NetworkProtocol;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import net.minecraft.network.packet.c2s.login.HelloC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.resource.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.snooper.Snooper;
import net.minecraft.snooper.SnooperPopulator;
import net.minecraft.stat.PlayerStats;
import net.minecraft.stat.achievement.AchievementStatFormatter;
import net.minecraft.stat.achievement.Achievements;
import net.minecraft.text.LiteralText;
import net.minecraft.util.BlockableEventLoop;
import net.minecraft.util.Utils;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.Difficulty;
import net.minecraft.world.HitResult;
import net.minecraft.world.WorldData;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.dimension.NetherDimension;
import net.minecraft.world.dimension.TheEndDimension;
import net.minecraft.world.storage.AnvilWorldStorageSource;
import net.minecraft.world.storage.WorldStorage;
import net.minecraft.world.storage.WorldStorageSource;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.LWJGLException;
import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.OpenGLException;
import org.lwjgl.opengl.PixelFormat;
import org.lwjgl.util.glu.GLU;

public class Minecraft implements BlockableEventLoop, SnooperPopulator {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Identifier MOJANG_LOGO_LOCATION = new Identifier("textures/gui/title/mojang.png");
    public static final boolean IS_MAC = Utils.getOS() == Utils.OS.MACOS;
    public static byte[] MEMORY_RESERVED_FOR_CRASH = new byte[10485760];
    private static final List<DisplayMode> DISPLAY_MODES = Lists.newArrayList(new DisplayMode(2560, 1600), new DisplayMode(2880, 1800));
    private final File resourcePacksDir;
    private final PropertyMap userProperties;
    private final PropertyMap profileProperties;
    private ServerListEntry currentServerEntry;
    private TextureManager textureManager;
    private static Minecraft INSTANCE;
    public ClientPlayerInteractionManager interactionManager;
    private boolean fullscreen;
    private boolean logGlErrors = true;
    private boolean crashed;
    private CrashReport crashReport;
    public int width;
    public int height;
    private boolean connectedToRealms = false;
    private TickTimer timer = new TickTimer(20.0F);
    private Snooper snooper = new Snooper("client", this, MinecraftServer.getTimeMillis());
    public ClientWorld world;
    public WorldRenderer worldRenderer;
    private EntityRenderDispatcher entityRenderDispatcher;
    private ItemRenderer itemRenderer;
    private ItemInHandRenderer itemInHandRenderer;
    public LocalClientPlayerEntity player;
    private Entity camera;
    public Entity targetEntity;
    public ParticleManager particleManager;
    private final Session session;
    private boolean paused;
    public TextRenderer textRenderer;
    public TextRenderer enchantingPhraseRenderer;
    public Screen screen;
    public ProgressRenderer progressRenderer;
    public GameRenderer gameRenderer;
    /**
     * This field is set to 10 when the player swings and misses.
     * When it is greater than 0, attacks are not processed.
     * Attack cooldown is decremented down every tick.
     * However, if the attack button is not held down at the time tick() is called, it is reset to 0.
     */
    private int attackCooldown;
    private int initWidth;
    private int initHeight;
    private IntegratedServer server;
    public ToastGui toast;
    public GameGui gui;
    public boolean skipGameRender;
    public HitResult crosshairTarget;
    public GameOptions options;
    public Mouse mouse;
    public final File gameDir;
    private final File assetsDir;
    private final String gameVersion;
    private final Proxy proxy;
    private WorldStorageSource worldStorageSource;
    private static int currentFps;
    private int useKeyCooldown;
    private String startupServerAddress;
    private int startupServerPort;
    public boolean focused;
    long lastTickTime = getTime();
    private int joinPlayerCounter;
    public final FrameTimeLogger frameTimeLogger = new FrameTimeLogger();
    long lastNanoTime = System.nanoTime();
    private final boolean is64Bit;
    private final boolean demo;
    private Connection connection;
    private boolean isIntegratedServerRunning;
    public final Profiler profiler = new Profiler();
    private long f3CTime = -1L;
    private ReloadableResourceManager resourceManager;
    private final ResourceMetadataSerializerRegistry resourceMetadataSerializerRegistry = new ResourceMetadataSerializerRegistry();
    private final List<ResourcePack> defaultResourcePacks = Lists.newArrayList();
    private final BuiltInResourcePack defaultResourcePack;
    private ResourcePacks resourcePacks;
    private LanguageManager languageManager;
    private TwitchStream twitchStream;
    private RenderTarget renderTarget;
    private TextureAtlas blocksAtlas;
    private SoundManager soundManager;
    private MusicManager musicManager;
    private Identifier logoLocation;
    private final MinecraftSessionService sessionService;
    private SkinManager skinManager;
    private final Queue<FutureTask<?>> tasks = Queues.newArrayDeque();
    private long f_8895172 = 0L;
    private final Thread thread = Thread.currentThread();
    private ModelManager modelManager;
    private BlockRenderDispatcher blockRenderer;
    volatile boolean running = true;
    public String fpsDebugInfo = "";
    public boolean f_1395113 = false;
    public boolean f_0060232 = false;
    public boolean f_8370368 = false;
    public boolean smartCull = true;
    /**
     * Time in miliseconds - only updates every second.
     */
    long timeAtLastSecond = getTime();
    int fpsCounter;
    long timeAfterLastTick = -1L;
    private String openProfilerSection = "root";

    public Minecraft(RunArgs session) {
        INSTANCE = this;
        this.gameDir = session.location.gameDir;
        this.assetsDir = session.location.assetsDir;
        this.resourcePacksDir = session.location.resourcePacksDir;
        this.gameVersion = session.game.version;
        this.userProperties = session.user.userProperties;
        this.profileProperties = session.user.profileProperties;
        this.defaultResourcePack = new BuiltInResourcePack(new AssetIndex(session.location.assetsDir, session.location.assetIndex).getIndex());
        this.proxy = session.user.proxy == null ? Proxy.NO_PROXY : session.user.proxy;
        this.sessionService = new YggdrasilAuthenticationService(session.user.proxy, UUID.randomUUID().toString()).createMinecraftSessionService();
        this.session = session.user.session;
        LOGGER.info("Setting user: " + this.session.getUsername());
        LOGGER.info("(Session ID is " + this.session.getSessionId() + ")");
        this.demo = session.game.demo;
        this.width = session.display.width > 0 ? session.display.width : 1;
        this.height = session.display.height > 0 ? session.display.height : 1;
        this.initWidth = session.display.width;
        this.initHeight = session.display.height;
        this.fullscreen = session.display.fullscreen;
        this.is64Bit = checkIs64Bit();
        this.server = new IntegratedServer(this);
        if (session.server.ip != null) {
            this.startupServerAddress = session.server.ip;
            this.startupServerPort = session.server.port;
        }

        ImageIO.setUseCache(false);
        Bootstrap.init();
    }

    public void run() {
        this.running = true;

        try {
            this.init();
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.of(throwable, "Initializing game");
            crashreport.addCategory("Initialization");
            this.gameCrashed(this.populateCrashReport(crashreport));
            return;
        }

        try {
            try {
                while (this.running) {
                    if (this.crashed && this.crashReport != null) {
                        this.gameCrashed(this.crashReport);
                        return;
                    }

                    try {
                        this.runGame();
                    } catch (OutOfMemoryError outofmemoryerror) {
                        this.cleanHeap();
                        this.openScreen(new OutOfMemoryScreen());
                        System.gc();
                    }
                }

                return;
            } catch (ProgressRenderError progressrendererror) {
            } catch (CrashException crashexception) {
                this.populateCrashReport(crashexception.getReport());
                this.cleanHeap();
                LOGGER.fatal("Reported exception thrown!", crashexception);
                this.gameCrashed(crashexception.getReport());
            } catch (Throwable throwable1) {
                CrashReport crashreport1 = this.populateCrashReport(new CrashReport("Unexpected error", throwable1));
                this.cleanHeap();
                LOGGER.fatal("Unreported exception thrown!", throwable1);
                this.gameCrashed(crashreport1);
            }
        } finally {
            this.shutdown();
        }
    }

    private void init() throws LWJGLException, IOException {
        this.options = new GameOptions(this, this.gameDir);
        this.defaultResourcePacks.add(this.defaultResourcePack);
        this.initTimerHackThread();
        if (this.options.overrideHeight > 0 && this.options.overrideWidth > 0) {
            this.width = this.options.overrideWidth;
            this.height = this.options.overrideHeight;
        }

        LOGGER.info("LWJGL Version: " + Sys.getVersion());
        this.initIcon();
        this.initDisplayMode();
        this.initDisplay();
        GLX.init();
        this.renderTarget = new RenderTarget(this.width, this.height, true);
        this.renderTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        this.initResourceMetadataSerializers();
        this.resourcePacks = new ResourcePacks(
            this.resourcePacksDir,
            new File(this.gameDir, "server-resource-packs"),
            this.defaultResourcePack,
            this.resourceMetadataSerializerRegistry,
            this.options
        );
        this.resourceManager = new SimpleReloadableResourceManager(this.resourceMetadataSerializerRegistry);
        this.languageManager = new LanguageManager(this.resourceMetadataSerializerRegistry, this.options.language);
        this.resourceManager.addListener(this.languageManager);
        this.reloadResources();
        this.textureManager = new TextureManager(this.resourceManager);
        this.resourceManager.addListener(this.textureManager);
        this.renderLoadingScreen(this.textureManager);
        this.initTwitchStream();
        this.skinManager = new SkinManager(this.textureManager, new File(this.assetsDir, "skins"), this.sessionService);
        this.worldStorageSource = new AnvilWorldStorageSource(new File(this.gameDir, "saves"));
        this.soundManager = new SoundManager(this.resourceManager, this.options);
        this.resourceManager.addListener(this.soundManager);
        this.musicManager = new MusicManager(this);
        this.textRenderer = new TextRenderer(this.options, new Identifier("textures/font/ascii.png"), this.textureManager, false);
        if (this.options.language != null) {
            this.textRenderer.setUnicode(this.isUnicode());
            this.textRenderer.setBidirectional(this.languageManager.isBidirectional());
        }

        this.enchantingPhraseRenderer = new TextRenderer(this.options, new Identifier("textures/font/ascii_sga.png"), this.textureManager, false);
        this.resourceManager.addListener(this.textRenderer);
        this.resourceManager.addListener(this.enchantingPhraseRenderer);
        this.resourceManager.addListener(new GrassColorReloader());
        this.resourceManager.addListener(new FoliageColorReloader());
        Achievements.OPEN_INVENTORY.setFormatter(new AchievementStatFormatter() {
            @Override
            public String format(String value) {
                try {
                    return String.format(value, GameOptions.getKeyName(Minecraft.this.options.inventoryKey.getKeyCode()));
                } catch (Exception exception) {
                    return "Error: " + exception.getLocalizedMessage();
                }
            }
        });
        this.mouse = new Mouse();
        this.logGlError("Pre startup");
        GlStateManager.enableTexture();
        GlStateManager.shadeModel(7425);
        GlStateManager.clearDepth(1.0);
        GlStateManager.enableDepthTest();
        GlStateManager.depthFunc(515);
        GlStateManager.enableAlphaTest();
        GlStateManager.alphaFunc(516, 0.1F);
        GlStateManager.cullFace(1029);
        GlStateManager.matrixMode(5889);
        GlStateManager.loadIdentity();
        GlStateManager.matrixMode(5888);
        this.logGlError("Startup");
        this.blocksAtlas = new TextureAtlas("textures");
        this.blocksAtlas.setMaxMipLevel(this.options.mipmapLevels);
        this.textureManager.register(TextureAtlas.BLOCKS_LOCATION, this.blocksAtlas);
        this.textureManager.bind(TextureAtlas.BLOCKS_LOCATION);
        this.blocksAtlas.setFilter(false, this.options.mipmapLevels > 0);
        this.modelManager = new ModelManager(this.blocksAtlas);
        this.resourceManager.addListener(this.modelManager);
        this.itemRenderer = new ItemRenderer(this.textureManager, this.modelManager);
        this.entityRenderDispatcher = new EntityRenderDispatcher(this.textureManager, this.itemRenderer);
        this.itemInHandRenderer = new ItemInHandRenderer(this);
        this.resourceManager.addListener(this.itemRenderer);
        this.gameRenderer = new GameRenderer(this, this.resourceManager);
        this.resourceManager.addListener(this.gameRenderer);
        this.blockRenderer = new BlockRenderDispatcher(this.modelManager.getModelShaper(), this.options);
        this.resourceManager.addListener(this.blockRenderer);
        this.worldRenderer = new WorldRenderer(this);
        this.resourceManager.addListener(this.worldRenderer);
        this.toast = new ToastGui(this);
        GlStateManager.viewport(0, 0, this.width, this.height);
        this.particleManager = new ParticleManager(this.world, this.textureManager);
        this.logGlError("Post startup");
        this.gui = new GameGui(this);
        if (this.startupServerAddress != null) {
            this.openScreen(new ConnectScreen(new TitleScreen(), this, this.startupServerAddress, this.startupServerPort));
        } else {
            this.openScreen(new TitleScreen());
        }

        this.textureManager.close(this.logoLocation);
        this.logoLocation = null;
        this.progressRenderer = new ProgressRenderer(this);
        if (this.options.fullscreen && !this.fullscreen) {
            this.toggleFullscreen();
        }

        try {
            Display.setVSyncEnabled(this.options.vsync);
        } catch (OpenGLException openglexception) {
            this.options.vsync = false;
            this.options.save();
        }

        this.worldRenderer.loadEntityOutline();
    }

    private void initResourceMetadataSerializers() {
        this.resourceMetadataSerializerRegistry.register(new TextureMetadataSerializer(), TextureMetadata.class);
        this.resourceMetadataSerializerRegistry.register(new FontMetadataSerializer(), FontMetadata.class);
        this.resourceMetadataSerializerRegistry.register(new AnimationMetadataSerializer(), AnimationMetadata.class);
        this.resourceMetadataSerializerRegistry.register(new PackMetadataSerializer(), ResourcePackMetadata.class);
        this.resourceMetadataSerializerRegistry.register(new LanguageMetadataSerializer(), LanguageMetadata.class);
    }

    private void initTwitchStream() {
        try {
            this.twitchStream = new Twitch(this, Iterables.getFirst(this.userProperties.get("twitch_access_token"), null));
        } catch (Throwable throwable) {
            this.twitchStream = new ErrorTwitchStream(throwable);
            LOGGER.error("Couldn't initialize twitch stream");
        }
    }

    private void initDisplay() throws LWJGLException {
        Display.setResizable(true);
        Display.setTitle("Minecraft 1.8.8");

        try {
            Display.create(new PixelFormat().withDepthBits(24));
        } catch (LWJGLException lwjglexception) {
            LOGGER.error("Couldn't set pixel format", lwjglexception);

            try {
                Thread.sleep(1000L);
            } catch (InterruptedException interruptedexception) {
            }

            if (this.fullscreen) {
                this.updateDisplayMode();
            }

            Display.create();
        }
    }

    private void initDisplayMode() throws LWJGLException {
        if (this.fullscreen) {
            Display.setFullscreen(true);
            DisplayMode displaymode = Display.getDisplayMode();
            this.width = Math.max(1, displaymode.getWidth());
            this.height = Math.max(1, displaymode.getHeight());
        } else {
            Display.setDisplayMode(new DisplayMode(this.width, this.height));
        }
    }

    private void initIcon() {
        Utils.OS utils$os = Utils.getOS();
        if (utils$os != Utils.OS.MACOS) {
            InputStream inputstream = null;
            InputStream inputstream1 = null;

            try {
                inputstream = this.defaultResourcePack.getAsset(new Identifier("icons/icon_16x16.png"));
                inputstream1 = this.defaultResourcePack.getAsset(new Identifier("icons/icon_32x32.png"));
                if (inputstream != null && inputstream1 != null) {
                    Display.setIcon(new ByteBuffer[]{this.readImageBuffer(inputstream), this.readImageBuffer(inputstream1)});
                }
            } catch (IOException ioexception) {
                LOGGER.error("Couldn't set icon", ioexception);
            } finally {
                IOUtils.closeQuietly(inputstream);
                IOUtils.closeQuietly(inputstream1);
            }
        }
    }

    private static boolean checkIs64Bit() {
        String[] astring = new String[]{"sun.arch.data.model", "com.ibm.vm.bitmode", "os.arch"};

        for (String s : astring) {
            String s1 = System.getProperty(s);
            if (s1 != null && s1.contains("64")) {
                return true;
            }
        }

        return false;
    }

    public RenderTarget getRenderTarget() {
        return this.renderTarget;
    }

    public String getGameVersion() {
        return this.gameVersion;
    }

    private void initTimerHackThread() {
        Thread thread = new Thread("Timer hack thread") {
            @Override
            public void run() {
                while (Minecraft.this.running) {
                    try {
                        Thread.sleep(2147483647L);
                    } catch (InterruptedException interruptedexception) {
                    }
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
    }

    public void integratedServerCrashed(CrashReport crashReport) {
        this.crashed = true;
        this.crashReport = crashReport;
    }

    public void gameCrashed(CrashReport report) {
        File file1 = new File(getInstance().gameDir, "crash-reports");
        File file2 = new File(file1, "crash-" + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()) + "-client.txt");
        Bootstrap.sysout(report.build());
        if (report.getFile() != null) {
            Bootstrap.sysout("#@!@# Game crashed! Crash report saved to: #@!@# " + report.getFile());
            System.exit(-1);
        } else if (report.writeToFile(file2)) {
            Bootstrap.sysout("#@!@# Game crashed! Crash report saved to: #@!@# " + file2.getAbsolutePath());
            System.exit(-1);
        } else {
            Bootstrap.sysout("#@?@# Game crashed! Crash report could not be saved. #@?@#");
            System.exit(-2);
        }
    }

    public boolean isUnicode() {
        return this.languageManager.isUnicode() || this.options.forceUnicodeFont;
    }

    public void reloadResources() {
        List<ResourcePack> list = Lists.newArrayList(this.defaultResourcePacks);

        for (ResourcePacks.Entry resourcepacks$entry : this.resourcePacks.getApplied()) {
            list.add(resourcepacks$entry.get());
        }

        if (this.resourcePacks.getServerPack() != null) {
            list.add(this.resourcePacks.getServerPack());
        }

        try {
            this.resourceManager.reload(list);
        } catch (RuntimeException runtimeexception) {
            LOGGER.info("Caught error stitching, removing all assigned resourcepacks", runtimeexception);
            list.clear();
            list.addAll(this.defaultResourcePacks);
            this.resourcePacks.apply(Collections.emptyList());
            this.resourceManager.reload(list);
            this.options.resourcePacks.clear();
            this.options.incompatibleResourcePacks.clear();
            this.options.save();
        }

        this.languageManager.reload(list);
        if (this.worldRenderer != null) {
            this.worldRenderer.reload();
        }
    }

    private ByteBuffer readImageBuffer(InputStream file) throws IOException {
        BufferedImage bufferedimage = ImageIO.read(file);
        int[] aint = bufferedimage.getRGB(0, 0, bufferedimage.getWidth(), bufferedimage.getHeight(), null, 0, bufferedimage.getWidth());
        ByteBuffer bytebuffer = ByteBuffer.allocate(4 * aint.length);

        for (int i : aint) {
            bytebuffer.putInt(i << 8 | i >> 24 & 0xFF);
        }

        ((Buffer)bytebuffer).flip();
        return bytebuffer;
    }

    private void updateDisplayMode() throws LWJGLException {
        Set<DisplayMode> set = Sets.newHashSet();
        Collections.addAll(set, Display.getAvailableDisplayModes());
        DisplayMode displaymode = Display.getDesktopDisplayMode();
        if (!set.contains(displaymode) && Utils.getOS() == Utils.OS.MACOS) {
            for (DisplayMode displaymode1 : DISPLAY_MODES) {
                boolean flag = true;

                for (DisplayMode displaymode2 : set) {
                    if (displaymode2.getBitsPerPixel() == 32
                        && displaymode2.getWidth() == displaymode1.getWidth()
                        && displaymode2.getHeight() == displaymode1.getHeight()) {
                        flag = false;
                        break;
                    }
                }

                if (!flag) {
                    for (DisplayMode displaymode3 : set) {
                        if (displaymode3.getBitsPerPixel() == 32
                            && displaymode3.getWidth() == displaymode1.getWidth() / 2
                            && displaymode3.getHeight() == displaymode1.getHeight() / 2) {
                            displaymode = displaymode3;
                            break;
                        }
                    }
                }
            }
        }

        Display.setDisplayMode(displaymode);
        this.width = displaymode.getWidth();
        this.height = displaymode.getHeight();
    }

    private void renderLoadingScreen(TextureManager textureManager) throws LWJGLException {
        Window window = new Window(this);
        int i = window.getScale();
        RenderTarget rendertarget = new RenderTarget(window.getWidth() * i, window.getHeight() * i, true);
        rendertarget.bindWrite(false);
        GlStateManager.matrixMode(5889);
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0, window.getWidth(), window.getHeight(), 0.0, 1000.0, 3000.0);
        GlStateManager.matrixMode(5888);
        GlStateManager.loadIdentity();
        GlStateManager.translatef(0.0F, 0.0F, -2000.0F);
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        GlStateManager.disableDepthTest();
        GlStateManager.enableTexture();
        InputStream inputstream = null;

        try {
            inputstream = this.defaultResourcePack.getResource(MOJANG_LOGO_LOCATION);
            this.logoLocation = textureManager.register("logo", new DynamicTexture(ImageIO.read(inputstream)));
            textureManager.bind(this.logoLocation);
        } catch (IOException ioexception) {
            LOGGER.error("Unable to load logo: " + MOJANG_LOGO_LOCATION, ioexception);
        } finally {
            IOUtils.closeQuietly(inputstream);
        }

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR);
        bufferbuilder.vertex(0.0, this.height, 0.0).texture(0.0, 0.0).color(255, 255, 255, 255).nextVertex();
        bufferbuilder.vertex(this.width, this.height, 0.0).texture(0.0, 0.0).color(255, 255, 255, 255).nextVertex();
        bufferbuilder.vertex(this.width, 0.0, 0.0).texture(0.0, 0.0).color(255, 255, 255, 255).nextVertex();
        bufferbuilder.vertex(0.0, 0.0, 0.0).texture(0.0, 0.0).color(255, 255, 255, 255).nextVertex();
        tesselator.end();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        int j = 256;
        int k = 256;
        this.draw((window.getWidth() - j) / 2, (window.getHeight() - k) / 2, 0, 0, j, k, 255, 255, 255, 255);
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        rendertarget.unbindWrite();
        rendertarget.draw(window.getWidth() * i, window.getHeight() * i);
        GlStateManager.enableAlphaTest();
        GlStateManager.alphaFunc(516, 0.1F);
        this.updateDisplay();
    }

    public void draw(int x, int y, int u, int v, int width, int height, int r, int g, int b, int a) {
        float f = 0.00390625F;
        float f1 = 0.00390625F;
        BufferBuilder bufferbuilder = Tesselator.getInstance().getBuffer();
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR);
        bufferbuilder.vertex(x, y + height, 0.0).texture(u * f, (v + height) * f1).color(r, g, b, a).nextVertex();
        bufferbuilder.vertex(x + width, y + height, 0.0).texture((u + width) * f, (v + height) * f1).color(r, g, b, a).nextVertex();
        bufferbuilder.vertex(x + width, y, 0.0).texture((u + width) * f, v * f1).color(r, g, b, a).nextVertex();
        bufferbuilder.vertex(x, y, 0.0).texture(u * f, v * f1).color(r, g, b, a).nextVertex();
        Tesselator.getInstance().end();
    }

    public WorldStorageSource getWorldStorageSource() {
        return this.worldStorageSource;
    }

    public void openScreen(Screen screen) {
        if (this.screen != null) {
            this.screen.removed();
        }

        if (screen == null && this.world == null) {
            screen = new TitleScreen();
        } else if (screen == null && this.player.getHealth() <= 0.0F) {
            screen = new DeathScreen();
        }

        if (screen instanceof TitleScreen) {
            this.options.debugEnabled = false;
            this.gui.getChat().clear();
        }

        this.screen = screen;
        if (screen != null) {
            this.unlockMouse();
            Window window = new Window(this);
            int i = window.getWidth();
            int j = window.getHeight();
            screen.init(this, i, j);
            this.skipGameRender = false;
        } else {
            this.soundManager.resume();
            this.lockMouse();
        }
    }

    private void logGlError(String message) {
        if (this.logGlErrors) {
            int i = GL11.glGetError();
            if (i != 0) {
                String s = GLU.gluErrorString(i);
                LOGGER.error("########## GL ERROR ##########");
                LOGGER.error("@ " + message);
                LOGGER.error(i + ": " + s);
            }
        }
    }

    public void shutdown() {
        try {
            this.twitchStream.shutdown();
            LOGGER.info("Stopping!");

            try {
                this.setWorld(null);
            } catch (Throwable throwable) {
            }

            this.soundManager.close();
        } finally {
            Display.destroy();
            if (!this.crashed) {
                System.exit(0);
            }
        }

        System.gc();
    }

    private void runGame() {
        long i = System.nanoTime();
        this.profiler.push("root");
        if (Display.isCreated() && Display.isCloseRequested()) {
            this.stop();
        }

        if (this.paused && this.world != null) {
            float f = this.timer.partialTick;
            this.timer.advance();
            this.timer.partialTick = f;
        } else {
            this.timer.advance();
        }

        this.profiler.push("scheduledExecutables");
        synchronized (this.tasks) {
            while (!this.tasks.isEmpty()) {
                Utils.run(this.tasks.poll(), LOGGER);
            }
        }

        this.profiler.pop();
        long l = System.nanoTime();
        this.profiler.push("tick");

        for (int j = 0; j < this.timer.ticksThisFrame; j++) {
            this.tick();
        }

        this.profiler.swap("preRenderErrors");
        long i1 = System.nanoTime() - l;
        this.logGlError("Pre render");
        this.profiler.swap("sound");
        this.soundManager.updateListener(this.player, this.timer.partialTick);
        this.profiler.pop();
        this.profiler.push("render");
        GlStateManager.pushMatrix();
        GlStateManager.clear(16640);
        this.renderTarget.bindWrite(true);
        this.profiler.push("display");
        GlStateManager.enableTexture();
        if (this.player != null && this.player.isInWall()) {
            this.options.perspective = 0;
        }

        this.profiler.pop();
        if (!this.skipGameRender) {
            this.profiler.swap("gameRenderer");
            this.gameRenderer.render(this.timer.partialTick, i);
            this.profiler.pop();
        }

        this.profiler.pop();
        if (this.options.debugEnabled && this.options.debugProfilerEnabled && !this.options.hideGui) {
            if (!this.profiler.profiling) {
                this.profiler.reset();
            }

            this.profiler.profiling = true;
            this.renderProfilerChart(i1);
        } else {
            this.profiler.profiling = false;
            this.timeAfterLastTick = System.nanoTime();
        }

        this.toast.render();
        this.renderTarget.unbindWrite();
        GlStateManager.popMatrix();
        GlStateManager.pushMatrix();
        this.renderTarget.draw(this.width, this.height);
        GlStateManager.popMatrix();
        GlStateManager.pushMatrix();
        this.gameRenderer.renderStreamOverlay(this.timer.partialTick);
        GlStateManager.popMatrix();
        this.profiler.push("root");
        this.updateDisplay();
        Thread.yield();
        this.profiler.push("stream");
        this.profiler.push("update");
        this.twitchStream.update();
        this.profiler.swap("submit");
        this.twitchStream.submit();
        this.profiler.pop();
        this.profiler.pop();
        this.logGlError("Post render");
        this.fpsCounter++;
        this.paused = this.isSingleplayer() && this.screen != null && this.screen.shouldPauseGame() && !this.server.isPublished();
        long k = System.nanoTime();
        this.frameTimeLogger.log(k - this.lastNanoTime);
        this.lastNanoTime = k;

        while (getTime() >= this.timeAtLastSecond + 1000L) {
            currentFps = this.fpsCounter;
            this.fpsDebugInfo = String.format(
                "%d fps (%d chunk update%s) T: %s%s%s%s%s",
                currentFps,
                RenderChunk.updateCounter,
                RenderChunk.updateCounter != 1 ? "s" : "",
                this.options.fpsLimit == GameOptions.Option.FRAMERATE_LIMIT.getMax() ? "inf" : this.options.fpsLimit,
                this.options.vsync ? " vsync" : "",
                this.options.fancyGraphics ? "" : " fast",
                this.options.cloudRenderMode == 0 ? "" : (this.options.cloudRenderMode == 1 ? " fast-clouds" : " fancy-clouds"),
                GLX.useVbo() ? " vbo" : ""
            );
            RenderChunk.updateCounter = 0;
            this.timeAtLastSecond += 1000L;
            this.fpsCounter = 0;
            this.snooper.populate();
            if (!this.snooper.isInitialized()) {
                this.snooper.init();
            }
        }

        if (this.isFramerateValid()) {
            this.profiler.push("fpslimit_wait");
            Display.sync(this.getMaxFramerate());
            this.profiler.pop();
        }

        this.profiler.pop();
    }

    public void updateDisplay() {
        this.profiler.push("display_update");
        Display.update();
        this.profiler.pop();
        this.updateWindow();
    }

    protected void updateWindow() {
        if (!this.fullscreen && Display.wasResized()) {
            int i = this.width;
            int j = this.height;
            this.width = Display.getWidth();
            this.height = Display.getHeight();
            if (this.width != i || this.height != j) {
                if (this.width <= 0) {
                    this.width = 1;
                }

                if (this.height <= 0) {
                    this.height = 1;
                }

                this.resize(this.width, this.height);
            }
        }
    }

    public int getMaxFramerate() {
        return this.world == null && this.screen != null ? 30 : this.options.fpsLimit;
    }

    public boolean isFramerateValid() {
        return this.getMaxFramerate() < GameOptions.Option.FRAMERATE_LIMIT.getMax();
    }

    public void cleanHeap() {
        try {
            MEMORY_RESERVED_FOR_CRASH = new byte[0];
            this.worldRenderer.releaseGlLists();
        } catch (Throwable throwable1) {
        }

        try {
            System.gc();
            this.setWorld(null);
        } catch (Throwable throwable) {
        }

        System.gc();
    }

    private void selectProfilerChartSection(int section) {
        List<Profiler.Result> list = this.profiler.getResults(this.openProfilerSection);
        if (list != null && !list.isEmpty()) {
            Profiler.Result profiler$result = list.remove(0);
            if (section == 0) {
                if (profiler$result.location.length() > 0) {
                    int i = this.openProfilerSection.lastIndexOf(".");
                    if (i >= 0) {
                        this.openProfilerSection = this.openProfilerSection.substring(0, i);
                    }
                }
            } else {
                section--;
                if (section < list.size() && !list.get(section).location.equals("unspecified")) {
                    if (this.openProfilerSection.length() > 0) {
                        this.openProfilerSection = this.openProfilerSection + ".";
                    }

                    this.openProfilerSection = this.openProfilerSection + list.get(section).location;
                }
            }
        }
    }

    private void renderProfilerChart(long tickTime) {
        if (this.profiler.profiling) {
            List<Profiler.Result> list = this.profiler.getResults(this.openProfilerSection);
            Profiler.Result profiler$result = list.remove(0);
            GlStateManager.clear(256);
            GlStateManager.matrixMode(5889);
            GlStateManager.enableColorMaterial();
            GlStateManager.loadIdentity();
            GlStateManager.ortho(0.0, this.width, this.height, 0.0, 1000.0, 3000.0);
            GlStateManager.matrixMode(5888);
            GlStateManager.loadIdentity();
            GlStateManager.translatef(0.0F, 0.0F, -2000.0F);
            GL11.glLineWidth(1.0F);
            GlStateManager.disableTexture();
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferbuilder = tesselator.getBuffer();
            int i = 160;
            int j = this.width - i - 10;
            int k = this.height - i * 2;
            GlStateManager.enableBlend();
            bufferbuilder.begin(7, DefaultVertexFormat.POSITION_COLOR);
            bufferbuilder.vertex(j - i * 1.1F, k - i * 0.6F - 16.0F, 0.0).color(200, 0, 0, 0).nextVertex();
            bufferbuilder.vertex(j - i * 1.1F, k + i * 2, 0.0).color(200, 0, 0, 0).nextVertex();
            bufferbuilder.vertex(j + i * 1.1F, k + i * 2, 0.0).color(200, 0, 0, 0).nextVertex();
            bufferbuilder.vertex(j + i * 1.1F, k - i * 0.6F - 16.0F, 0.0).color(200, 0, 0, 0).nextVertex();
            tesselator.end();
            GlStateManager.disableBlend();
            double d0 = 0.0;

            for (int l = 0; l < list.size(); l++) {
                Profiler.Result profiler$result1 = list.get(l);
                int i1 = MathHelper.floor(profiler$result1.percentageOfParent / 4.0) + 1;
                bufferbuilder.begin(6, DefaultVertexFormat.POSITION_COLOR);
                int j1 = profiler$result1.getColor();
                int k1 = j1 >> 16 & 0xFF;
                int l1 = j1 >> 8 & 0xFF;
                int i2 = j1 & 0xFF;
                bufferbuilder.vertex(j, k, 0.0).color(k1, l1, i2, 255).nextVertex();

                for (int j2 = i1; j2 >= 0; j2--) {
                    float f = (float)((d0 + profiler$result1.percentageOfParent * j2 / i1) * (float) Math.PI * 2.0 / 100.0);
                    float f1 = MathHelper.sin(f) * i;
                    float f2 = MathHelper.cos(f) * i * 0.5F;
                    bufferbuilder.vertex(j + f1, k - f2, 0.0).color(k1, l1, i2, 255).nextVertex();
                }

                tesselator.end();
                bufferbuilder.begin(5, DefaultVertexFormat.POSITION_COLOR);

                for (int i3 = i1; i3 >= 0; i3--) {
                    float f3 = (float)((d0 + profiler$result1.percentageOfParent * i3 / i1) * (float) Math.PI * 2.0 / 100.0);
                    float f4 = MathHelper.sin(f3) * i;
                    float f5 = MathHelper.cos(f3) * i * 0.5F;
                    bufferbuilder.vertex(j + f4, k - f5, 0.0).color(k1 >> 1, l1 >> 1, i2 >> 1, 255).nextVertex();
                    bufferbuilder.vertex(j + f4, k - f5 + 10.0F, 0.0).color(k1 >> 1, l1 >> 1, i2 >> 1, 255).nextVertex();
                }

                tesselator.end();
                d0 += profiler$result1.percentageOfParent;
            }

            DecimalFormat decimalformat = new DecimalFormat("##0.00");
            GlStateManager.enableTexture();
            String s = "";
            if (!profiler$result.location.equals("unspecified")) {
                s = s + "[0] ";
            }

            if (profiler$result.location.length() == 0) {
                s = s + "ROOT ";
            } else {
                s = s + profiler$result.location + " ";
            }

            int l2 = 16777215;
            this.textRenderer.drawWithShadow(s, j - i, k - i / 2 - 16, l2);
            this.textRenderer
                .drawWithShadow(s = decimalformat.format(profiler$result.percentageOfTotal) + "%", j + i - this.textRenderer.getWidth(s), k - i / 2 - 16, l2);

            for (int k2 = 0; k2 < list.size(); k2++) {
                Profiler.Result profiler$result2 = list.get(k2);
                String s1 = "";
                if (profiler$result2.location.equals("unspecified")) {
                    s1 = s1 + "[?] ";
                } else {
                    s1 = s1 + "[" + (k2 + 1) + "] ";
                }

                s1 = s1 + profiler$result2.location;
                this.textRenderer.drawWithShadow(s1, j - i, k + i / 2 + k2 * 8 + 20, profiler$result2.getColor());
                this.textRenderer
                    .drawWithShadow(
                        s1 = decimalformat.format(profiler$result2.percentageOfParent) + "%",
                        j + i - 50 - this.textRenderer.getWidth(s1),
                        k + i / 2 + k2 * 8 + 20,
                        profiler$result2.getColor()
                    );
                this.textRenderer
                    .drawWithShadow(
                        s1 = decimalformat.format(profiler$result2.percentageOfTotal) + "%",
                        j + i - this.textRenderer.getWidth(s1),
                        k + i / 2 + k2 * 8 + 20,
                        profiler$result2.getColor()
                    );
            }
        }
    }

    public void stop() {
        this.running = false;
    }

    public void lockMouse() {
        if (Display.isActive()) {
            if (!this.focused) {
                this.focused = true;
                this.mouse.lock();
                this.openScreen(null);
                this.attackCooldown = 10000;
            }
        }
    }

    public void unlockMouse() {
        if (this.focused) {
            KeyBinding.releaseAll();
            this.focused = false;
            this.mouse.unlock();
        }
    }

    public void pauseGame() {
        if (this.screen == null) {
            this.openScreen(new GameMenuScreen());
            if (this.isSingleplayer() && !this.server.isPublished()) {
                this.soundManager.pause();
            }
        }
    }

    private void handleMouseDown(boolean holdingAttack) {
        if (!holdingAttack) {
            this.attackCooldown = 0;
        }

        if (this.attackCooldown <= 0 && !this.player.hasItemInUse()) {
            if (holdingAttack && this.crosshairTarget != null && this.crosshairTarget.type == HitResult.Type.BLOCK) {
                BlockPos blockpos = this.crosshairTarget.getPos();
                if (this.world.getBlockState(blockpos).getBlock().getMaterial() != Material.AIR
                    && this.interactionManager.tickBlockMining(blockpos, this.crosshairTarget.face)) {
                    this.particleManager.addBlockMiningParticles(blockpos, this.crosshairTarget.face);
                    this.player.swingArm();
                }
            } else {
                this.interactionManager.stopMiningBlock();
            }
        }
    }

    private void doAttack() {
        if (this.attackCooldown <= 0) {
            this.player.swingArm();
            if (this.crosshairTarget == null) {
                LOGGER.error("Null returned as 'hitResult', this shouldn't happen!");
                if (this.interactionManager.hasAttackCooldown()) {
                    this.attackCooldown = 10;
                }
            } else {
                switch (this.crosshairTarget.type) {
                    case ENTITY:
                        this.interactionManager.attackEntity(this.player, this.crosshairTarget.entity);
                        break;
                    case BLOCK:
                        BlockPos blockpos = this.crosshairTarget.getPos();
                        if (this.world.getBlockState(blockpos).getBlock().getMaterial() != Material.AIR) {
                            this.interactionManager.startMiningBlock(blockpos, this.crosshairTarget.face);
                            break;
                        }
                    case MISS:
                    default:
                        if (this.interactionManager.hasAttackCooldown()) {
                            this.attackCooldown = 10;
                        }
                }
            }
        }
    }

    private void doUse() {
        if (!this.interactionManager.isMiningBlock()) {
            this.useKeyCooldown = 4;
            boolean flag = true;
            ItemStack itemstack = this.player.inventory.getSelectedItem();
            if (this.crosshairTarget == null) {
                LOGGER.warn("Null returned as 'hitResult', this shouldn't happen!");
            } else {
                switch (this.crosshairTarget.type) {
                    case ENTITY:
                        if (this.interactionManager.interactEntityAt(this.player, this.crosshairTarget.entity, this.crosshairTarget)) {
                            flag = false;
                        } else if (this.interactionManager.interactEntity(this.player, this.crosshairTarget.entity)) {
                            flag = false;
                        }
                        break;
                    case BLOCK:
                        BlockPos blockpos = this.crosshairTarget.getPos();
                        if (this.world.getBlockState(blockpos).getBlock().getMaterial() != Material.AIR) {
                            int i = itemstack != null ? itemstack.size : 0;
                            if (this.interactionManager
                                .useBlock(this.player, this.world, itemstack, blockpos, this.crosshairTarget.face, this.crosshairTarget.facePos)) {
                                flag = false;
                                this.player.swingArm();
                            }

                            if (itemstack == null) {
                                return;
                            }

                            if (itemstack.size == 0) {
                                this.player.inventory.items[this.player.inventory.selectedSlot] = null;
                            } else if (itemstack.size != i || this.interactionManager.hasCreativeInventory()) {
                                this.gameRenderer.itemInHandRenderer.onBlockUsed();
                            }
                        }
                }
            }

            if (flag) {
                ItemStack itemstack1 = this.player.inventory.getSelectedItem();
                if (itemstack1 != null && this.interactionManager.useItem(this.player, this.world, itemstack1)) {
                    this.gameRenderer.itemInHandRenderer.onItemUsed();
                }
            }
        }
    }

    public void toggleFullscreen() {
        try {
            this.fullscreen = !this.fullscreen;
            this.options.fullscreen = this.fullscreen;
            if (this.fullscreen) {
                this.updateDisplayMode();
                this.width = Display.getDisplayMode().getWidth();
                this.height = Display.getDisplayMode().getHeight();
                if (this.width <= 0) {
                    this.width = 1;
                }

                if (this.height <= 0) {
                    this.height = 1;
                }
            } else {
                Display.setDisplayMode(new DisplayMode(this.initWidth, this.initHeight));
                this.width = this.initWidth;
                this.height = this.initHeight;
                if (this.width <= 0) {
                    this.width = 1;
                }

                if (this.height <= 0) {
                    this.height = 1;
                }
            }

            if (this.screen != null) {
                this.resize(this.width, this.height);
            } else {
                this.onResolutionChanged();
            }

            Display.setFullscreen(this.fullscreen);
            Display.setVSyncEnabled(this.options.vsync);
            this.updateDisplay();
        } catch (Exception exception) {
            LOGGER.error("Couldn't toggle fullscreen", exception);
        }
    }

    private void resize(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        if (this.screen != null) {
            Window window = new Window(this);
            this.screen.resize(this, window.getWidth(), window.getHeight());
        }

        this.progressRenderer = new ProgressRenderer(this);
        this.onResolutionChanged();
    }

    private void onResolutionChanged() {
        this.renderTarget.resize(this.width, this.height);
        if (this.gameRenderer != null) {
            this.gameRenderer.onResolutionChanged(this.width, this.height);
        }
    }

    public MusicManager getMusicManager() {
        return this.musicManager;
    }

    public void tick() {
        if (this.useKeyCooldown > 0) {
            this.useKeyCooldown--;
        }

        this.profiler.push("gui");
        if (!this.paused) {
            this.gui.tick();
        }

        this.profiler.pop();
        this.gameRenderer.pick(1.0F);
        this.profiler.push("gameMode");
        if (!this.paused && this.world != null) {
            this.interactionManager.tick();
        }

        this.profiler.swap("textures");
        if (!this.paused) {
            this.textureManager.tick();
        }

        if (this.screen == null && this.player != null) {
            if (this.player.getHealth() <= 0.0F) {
                this.openScreen(null);
            } else if (this.player.isSleeping() && this.world != null) {
                this.openScreen(new SleepingChatScreen());
            }
        } else if (this.screen != null && this.screen instanceof SleepingChatScreen && !this.player.isSleeping()) {
            this.openScreen(null);
        }

        if (this.screen != null) {
            this.attackCooldown = 10000;
        }

        if (this.screen != null) {
            try {
                this.screen.handleInputs();
            } catch (Throwable throwable1) {
                CrashReport crashreport = CrashReport.of(throwable1, "Updating screen events");
                CrashReportCategory crashreportcategory = crashreport.addCategory("Affected screen");
                crashreportcategory.add("Screen name", new Callable<String>() {
                    public String call() throws Exception {
                        return Minecraft.this.screen.getClass().getCanonicalName();
                    }
                });
                throw new CrashException(crashreport);
            }

            if (this.screen != null) {
                try {
                    this.screen.tick();
                } catch (Throwable throwable) {
                    CrashReport crashreport1 = CrashReport.of(throwable, "Ticking screen");
                    CrashReportCategory crashreportcategory1 = crashreport1.addCategory("Affected screen");
                    crashreportcategory1.add("Screen name", new Callable<String>() {
                        public String call() throws Exception {
                            return Minecraft.this.screen.getClass().getCanonicalName();
                        }
                    });
                    throw new CrashException(crashreport1);
                }
            }
        }

        if (this.screen == null || this.screen.passEvents) {
            this.profiler.swap("mouse");

            while (org.lwjgl.input.Mouse.next()) {
                int i = org.lwjgl.input.Mouse.getEventButton();
                KeyBinding.set(i - 100, org.lwjgl.input.Mouse.getEventButtonState());
                if (org.lwjgl.input.Mouse.getEventButtonState()) {
                    if (this.player.isSpectator() && i == 2) {
                        this.gui.getSpectatorGui().mouseMiddleClicked();
                    } else {
                        KeyBinding.click(i - 100);
                    }
                }

                long i1 = getTime() - this.lastTickTime;
                if (i1 <= 200L) {
                    int j = org.lwjgl.input.Mouse.getEventDWheel();
                    if (j != 0) {
                        if (this.player.isSpectator()) {
                            j = j < 0 ? -1 : 1;
                            if (this.gui.getSpectatorGui().isMenuActive()) {
                                this.gui.getSpectatorGui().mouseScrolled(-j);
                            } else {
                                float f = MathHelper.clamp(this.player.abilities.getFlySpeed() + j * 0.005F, 0.0F, 0.2F);
                                this.player.abilities.setFlySpeed(f);
                            }
                        } else {
                            this.player.inventory.scrollInHotbar(j);
                        }
                    }

                    if (this.screen == null) {
                        if (!this.focused && org.lwjgl.input.Mouse.getEventButtonState()) {
                            this.lockMouse();
                        }
                    } else if (this.screen != null) {
                        this.screen.handleMouse();
                    }
                }
            }

            if (this.attackCooldown > 0) {
                this.attackCooldown--;
            }

            this.profiler.swap("keyboard");

            while (Keyboard.next()) {
                int k = Keyboard.getEventKey() == 0 ? Keyboard.getEventCharacter() + 256 : Keyboard.getEventKey();
                KeyBinding.set(k, Keyboard.getEventKeyState());
                if (Keyboard.getEventKeyState()) {
                    KeyBinding.click(k);
                }

                if (this.f3CTime > 0L) {
                    if (getTime() - this.f3CTime >= 6000L) {
                        throw new CrashException(new CrashReport("Manually triggered debug crash", new Throwable()));
                    }

                    if (!Keyboard.isKeyDown(46) || !Keyboard.isKeyDown(61)) {
                        this.f3CTime = -1L;
                    }
                } else if (Keyboard.isKeyDown(46) && Keyboard.isKeyDown(61)) {
                    this.f3CTime = getTime();
                }

                this.handleGuiKeyBindings();
                if (Keyboard.getEventKeyState()) {
                    if (k == 62 && this.gameRenderer != null) {
                        this.gameRenderer.disableShader();
                    }

                    if (this.screen != null) {
                        this.screen.handleKeyboard();
                    } else {
                        if (k == 1) {
                            this.pauseGame();
                        }

                        if (k == 32 && Keyboard.isKeyDown(61) && this.gui != null) {
                            this.gui.getChat().clear();
                        }

                        if (k == 31 && Keyboard.isKeyDown(61)) {
                            this.reloadResources();
                        }

                        if (k == 17 && Keyboard.isKeyDown(61)) {
                        }

                        if (k == 18 && Keyboard.isKeyDown(61)) {
                        }

                        if (k == 47 && Keyboard.isKeyDown(61)) {
                        }

                        if (k == 38 && Keyboard.isKeyDown(61)) {
                        }

                        if (k == 22 && Keyboard.isKeyDown(61)) {
                        }

                        if (k == 20 && Keyboard.isKeyDown(61)) {
                            this.reloadResources();
                        }

                        if (k == 33 && Keyboard.isKeyDown(61)) {
                            this.options.set(GameOptions.Option.RENDER_DISTANCE, Screen.isShiftDown() ? -1 : 1);
                        }

                        if (k == 30 && Keyboard.isKeyDown(61)) {
                            this.worldRenderer.reload();
                        }

                        if (k == 35 && Keyboard.isKeyDown(61)) {
                            this.options.advancedItemTooltips = !this.options.advancedItemTooltips;
                            this.options.save();
                        }

                        if (k == 48 && Keyboard.isKeyDown(61)) {
                            this.entityRenderDispatcher.setRenderHitboxes(!this.entityRenderDispatcher.shouldRenderHitboxes());
                        }

                        if (k == 25 && Keyboard.isKeyDown(61)) {
                            this.options.pauseOnUnfocus = !this.options.pauseOnUnfocus;
                            this.options.save();
                        }

                        if (k == 59) {
                            this.options.hideGui = !this.options.hideGui;
                        }

                        if (k == 61) {
                            this.options.debugEnabled = !this.options.debugEnabled;
                            this.options.debugProfilerEnabled = Screen.isShiftDown();
                            this.options.debugTpsEnabled = Screen.isAltDown();
                        }

                        if (this.options.togglePerspectiveKey.consumeClick()) {
                            this.options.perspective++;
                            if (this.options.perspective > 2) {
                                this.options.perspective = 0;
                            }

                            if (this.options.perspective == 0) {
                                this.gameRenderer.updateShader(this.getCamera());
                            } else if (this.options.perspective == 1) {
                                this.gameRenderer.updateShader(null);
                            }

                            this.worldRenderer.onViewChanged();
                        }

                        if (this.options.smoothCameraKey.consumeClick()) {
                            this.options.smoothCamera = !this.options.smoothCamera;
                        }
                    }

                    if (this.options.debugEnabled && this.options.debugProfilerEnabled) {
                        if (k == 11) {
                            this.selectProfilerChartSection(0);
                        }

                        for (int j1 = 0; j1 < 9; j1++) {
                            if (k == 2 + j1) {
                                this.selectProfilerChartSection(j1 + 1);
                            }
                        }
                    }
                }
            }

            for (int l = 0; l < 9; l++) {
                if (this.options.hotbarKeyBindings[l].consumeClick()) {
                    if (this.player.isSpectator()) {
                        this.gui.getSpectatorGui().selectSlot(l);
                    } else {
                        this.player.inventory.selectedSlot = l;
                    }
                }
            }

            boolean flag = this.options.chatVisibility != PlayerEntity.ChatVisibility.HIDDEN;

            while (this.options.inventoryKey.consumeClick()) {
                if (this.interactionManager.hasRidingInventory()) {
                    this.player.openRidingInventory();
                } else {
                    this.getNetworkHandler().sendPacket(new ClientStatusC2SPacket(ClientStatusC2SPacket.Status.OPEN_INVENTORY_ACHIEVEMENT));
                    this.openScreen(new SurvivalInventoryScreen(this.player));
                }
            }

            while (this.options.dropKey.consumeClick()) {
                if (!this.player.isSpectator()) {
                    this.player.dropItem(Screen.isControlDown());
                }
            }

            while (this.options.chatKey.consumeClick() && flag) {
                this.openScreen(new ChatScreen());
            }

            if (this.screen == null && this.options.commandKey.consumeClick() && flag) {
                this.openScreen(new ChatScreen("/"));
            }

            if (this.player.hasItemInUse()) {
                if (!this.options.useKey.isPressed()) {
                    this.interactionManager.stopUsingHand(this.player);
                }

                while (this.options.attackKey.consumeClick()) {
                }

                while (this.options.useKey.consumeClick()) {
                }

                while (this.options.pickItemKey.consumeClick()) {
                }
            } else {
                while (this.options.attackKey.consumeClick()) {
                    this.doAttack();
                }

                while (this.options.useKey.consumeClick()) {
                    this.doUse();
                }

                while (this.options.pickItemKey.consumeClick()) {
                    this.doPick();
                }
            }

            if (this.options.useKey.isPressed() && this.useKeyCooldown == 0 && !this.player.hasItemInUse()) {
                this.doUse();
            }

            this.handleMouseDown(this.screen == null && this.options.attackKey.isPressed() && this.focused);
        }

        if (this.world != null) {
            if (this.player != null) {
                this.joinPlayerCounter++;
                if (this.joinPlayerCounter == 30) {
                    this.joinPlayerCounter = 0;
                    this.world.addEntityAlways(this.player);
                }
            }

            this.profiler.swap("gameRenderer");
            if (!this.paused) {
                this.gameRenderer.tick();
            }

            this.profiler.swap("levelRenderer");
            if (!this.paused) {
                this.worldRenderer.tick();
            }

            this.profiler.swap("level");
            if (!this.paused) {
                if (this.world.getLightningCooldown() > 0) {
                    this.world.setLightningCooldown(this.world.getLightningCooldown() - 1);
                }

                this.world.tickEntities();
            }
        } else if (this.gameRenderer.hasShader()) {
            this.gameRenderer.closeShader();
        }

        if (!this.paused) {
            this.musicManager.tick();
            this.soundManager.tick();
        }

        if (this.world != null) {
            if (!this.paused) {
                this.world.setAllowedMobSpawns(this.world.getDifficulty() != Difficulty.PEACEFUL, true);

                try {
                    this.world.tick();
                } catch (Throwable throwable2) {
                    CrashReport crashreport2 = CrashReport.of(throwable2, "Exception in world tick");
                    if (this.world == null) {
                        CrashReportCategory crashreportcategory2 = crashreport2.addCategory("Affected level");
                        crashreportcategory2.add("Problem", "Level is null!");
                    } else {
                        this.world.populateCrashReport(crashreport2);
                    }

                    throw new CrashException(crashreport2);
                }
            }

            this.profiler.swap("animateTick");
            if (!this.paused && this.world != null) {
                this.world.doRandomDisplayTicks(MathHelper.floor(this.player.x), MathHelper.floor(this.player.y), MathHelper.floor(this.player.z));
            }

            this.profiler.swap("particles");
            if (!this.paused) {
                this.particleManager.tick();
            }
        } else if (this.connection != null) {
            this.profiler.swap("pendingConnection");
            this.connection.tick();
        }

        this.profiler.pop();
        this.lastTickTime = getTime();
    }

    public void startGame(String saveName, String name, WorldSettings settings) {
        this.setWorld(null);
        System.gc();
        WorldStorage worldstorage = this.worldStorageSource.get(saveName, false);
        WorldData worlddata = worldstorage.loadData();
        if (worlddata == null && settings != null) {
            worlddata = new WorldData(settings, saveName);
            worldstorage.saveData(worlddata);
        }

        if (settings == null) {
            settings = new WorldSettings(worlddata);
        }

        try {
            this.server = new IntegratedServer(this, saveName, name, settings);
            this.server.start();
            this.isIntegratedServerRunning = true;
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.of(throwable, "Starting integrated server");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Starting integrated server");
            crashreportcategory.add("Level ID", saveName);
            crashreportcategory.add("Level Name", name);
            throw new CrashException(crashreport);
        }

        this.progressRenderer.progressStartNoAbort(I18n.translate("menu.loadingLevel"));

        while (!this.server.isLoading()) {
            String s = this.server.getLoadingStage();
            if (s != null) {
                this.progressRenderer.progressStage(I18n.translate(s));
            } else {
                this.progressRenderer.progressStage("");
            }

            try {
                Thread.sleep(200L);
            } catch (InterruptedException interruptedexception) {
            }
        }

        this.openScreen(null);
        SocketAddress socketaddress = this.server.getConnection().bindLocal();
        Connection connection = Connection.connectLocal(socketaddress);
        connection.setListener(new ClientLoginNetworkHandler(connection, this, null));
        connection.send(new HandshakeC2SPacket(47, socketaddress.toString(), 0, NetworkProtocol.LOGIN));
        connection.send(new HelloC2SPacket(this.getSession().getProfile()));
        this.connection = connection;
    }

    public void setWorld(ClientWorld world) {
        this.setWorld(world, "");
    }

    public void setWorld(ClientWorld world, String message) {
        if (world == null) {
            ClientPlayNetworkHandler clientplaynetworkhandler = this.getNetworkHandler();
            if (clientplaynetworkhandler != null) {
                clientplaynetworkhandler.closeWorld();
            }

            if (this.server != null && this.server.hasGameDirectory()) {
                this.server.stop();
                this.server.resetInstance();
            }

            this.server = null;
            this.toast.clear();
            this.gameRenderer.getMapRenderer().clearStateTextures();
        }

        this.camera = null;
        this.connection = null;
        if (this.progressRenderer != null) {
            this.progressRenderer.updateTitle(message);
            this.progressRenderer.progressStage("");
        }

        if (world == null && this.world != null) {
            this.resourcePacks.removeServerPack();
            this.gui.resetPlayerTabOverlay();
            this.setCurrentServerEntry(null);
            this.isIntegratedServerRunning = false;
        }

        this.soundManager.stop();
        this.world = world;
        if (world != null) {
            if (this.worldRenderer != null) {
                this.worldRenderer.setWorld(world);
            }

            if (this.particleManager != null) {
                this.particleManager.setWorld(world);
            }

            if (this.player == null) {
                this.player = this.interactionManager.createPlayer(world, new PlayerStats());
                this.interactionManager.initPlayer(this.player);
            }

            this.player.resetPos();
            world.addEntity(this.player);
            this.player.input = new KeyboardInput(this.options);
            this.interactionManager.adjustPlayer(this.player);
            this.camera = this.player;
        } else {
            this.worldStorageSource.flush();
            this.player = null;
        }

        System.gc();
        this.lastTickTime = 0L;
    }

    public void respawnPlayer(int dimension) {
        this.world.resetSpawnPoint();
        this.world.unloadEntities();
        int i = 0;
        String s = null;
        if (this.player != null) {
            i = this.player.getNetworkId();
            this.world.removeEntity(this.player);
            s = this.player.getServerBrand();
        }

        this.camera = null;
        LocalClientPlayerEntity localclientplayerentity = this.player;
        this.player = this.interactionManager.createPlayer(this.world, this.player == null ? new PlayerStats() : this.player.getStats());
        this.player.getSyncedData().update(localclientplayerentity.getSyncedData().getAll());
        this.player.dimension = dimension;
        this.camera = this.player;
        this.player.resetPos();
        this.player.setServerBrand(s);
        this.world.addEntity(this.player);
        this.interactionManager.initPlayer(this.player);
        this.player.input = new KeyboardInput(this.options);
        this.player.setNetworkId(i);
        this.interactionManager.adjustPlayer(this.player);
        this.player.setReducedDebugInfo(localclientplayerentity.hasReducedDebugInfo());
        if (this.screen instanceof DeathScreen) {
            this.openScreen(null);
        }
    }

    public final boolean isDemo() {
        return this.demo;
    }

    public ClientPlayNetworkHandler getNetworkHandler() {
        return this.player != null ? this.player.networkHandler : null;
    }

    public static boolean isDisplayGui() {
        return INSTANCE == null || !INSTANCE.options.hideGui;
    }

    public static boolean isFancyGraphicsEnabled() {
        return INSTANCE != null && INSTANCE.options.fancyGraphics;
    }

    public static boolean isAmbientOcclusionEnabled() {
        return INSTANCE != null && INSTANCE.options.ambientOcclusion != 0;
    }

    private void doPick() {
        if (this.crosshairTarget != null) {
            boolean flag = this.player.abilities.creativeMode;
            int i = 0;
            boolean flag1 = false;
            BlockEntity blockentity = null;
            Item item;
            if (this.crosshairTarget.type == HitResult.Type.BLOCK) {
                BlockPos blockpos = this.crosshairTarget.getPos();
                Block block = this.world.getBlockState(blockpos).getBlock();
                if (block.getMaterial() == Material.AIR) {
                    return;
                }

                item = block.getPickItem(this.world, blockpos);
                if (item == null) {
                    return;
                }

                if (flag && Screen.isControlDown()) {
                    blockentity = this.world.getBlockEntity(blockpos);
                }

                Block block1 = item instanceof BlockItem && !block.hasPickItemMetadata() ? Block.byItem(item) : block;
                i = block1.getPickItemMetadata(this.world, blockpos);
                flag1 = item.hasCustomData();
            } else {
                if (this.crosshairTarget.type != HitResult.Type.ENTITY || this.crosshairTarget.entity == null || !flag) {
                    return;
                }

                if (this.crosshairTarget.entity instanceof PaintingEntity) {
                    item = Items.PAINTING;
                } else if (this.crosshairTarget.entity instanceof LeadKnotEntity) {
                    item = Items.LEAD;
                } else if (this.crosshairTarget.entity instanceof ItemFrameEntity) {
                    ItemFrameEntity itemframeentity = (ItemFrameEntity)this.crosshairTarget.entity;
                    ItemStack itemstack = itemframeentity.getDisplayItem();
                    if (itemstack == null) {
                        item = Items.ITEM_FRAME;
                    } else {
                        item = itemstack.getItem();
                        i = itemstack.getMetadata();
                        flag1 = true;
                    }
                } else if (this.crosshairTarget.entity instanceof MinecartEntity) {
                    MinecartEntity minecartentity = (MinecartEntity)this.crosshairTarget.entity;
                    switch (minecartentity.getMinecartType()) {
                        case FURNACE:
                            item = Items.FURNACE_MINECART;
                            break;
                        case CHEST:
                            item = Items.CHEST_MINECART;
                            break;
                        case TNT:
                            item = Items.TNT_MINECART;
                            break;
                        case HOPPER:
                            item = Items.HOPPER_MINECART;
                            break;
                        case COMMAND_BLOCK:
                            item = Items.COMMAND_BLOCK_MINECART;
                            break;
                        default:
                            item = Items.MINECART;
                    }
                } else if (this.crosshairTarget.entity instanceof BoatEntity) {
                    item = Items.BOAT;
                } else if (this.crosshairTarget.entity instanceof ArmorStandEntity) {
                    item = Items.ARMOR_STAND;
                } else {
                    item = Items.SPAWN_EGG;
                    i = Entities.getId(this.crosshairTarget.entity);
                    flag1 = true;
                    if (!Entities.SPAWN_EGG_DATA.containsKey(i)) {
                        return;
                    }
                }
            }

            PlayerInventory playerinventory = this.player.inventory;
            if (blockentity == null) {
                playerinventory.selectSlot(item, i, flag1, flag);
            } else {
                ItemStack itemstack1 = this.pickBlockEntity(item, i, blockentity);
                playerinventory.setItem(playerinventory.selectedSlot, itemstack1);
            }

            if (flag) {
                int j = this.player.playerMenu.slots.size() - 9 + playerinventory.selectedSlot;
                this.interactionManager.addItemToCreativeMenu(playerinventory.getItem(playerinventory.selectedSlot), j);
            }
        }
    }

    private ItemStack pickBlockEntity(Item item, int metadata, BlockEntity blockEntity) {
        ItemStack itemstack = new ItemStack(item, 1, metadata);
        NbtCompound nbtcompound = new NbtCompound();
        blockEntity.writeNbt(nbtcompound);
        if (item == Items.SKULL && nbtcompound.contains("Owner")) {
            NbtCompound nbtcompound2 = nbtcompound.getCompound("Owner");
            NbtCompound nbtcompound3 = new NbtCompound();
            nbtcompound3.put("SkullOwner", nbtcompound2);
            itemstack.setNbt(nbtcompound3);
            return itemstack;
        } else {
            itemstack.addToNbt("BlockEntityTag", nbtcompound);
            NbtCompound nbtcompound1 = new NbtCompound();
            NbtList nbtlist = new NbtList();
            nbtlist.addElement(new NbtString("(+NBT)"));
            nbtcompound1.put("Lore", nbtlist);
            itemstack.addToNbt("display", nbtcompound1);
            return itemstack;
        }
    }

    public CrashReport populateCrashReport(CrashReport report) {
        report.getSystemDetails().add("Launched Version", new Callable<String>() {
            public String call() {
                return Minecraft.this.gameVersion;
            }
        });
        report.getSystemDetails().add("LWJGL", new Callable<String>() {
            public String call() {
                return Sys.getVersion();
            }
        });
        report.getSystemDetails().add("OpenGL", new Callable<String>() {
            public String call() {
                return GL11.glGetString(7937) + " GL version " + GL11.glGetString(7938) + ", " + GL11.glGetString(7936);
            }
        });
        report.getSystemDetails().add("GL Caps", new Callable<String>() {
            public String call() {
                return GLX.getGlCapsInfo();
            }
        });
        report.getSystemDetails().add("Using VBOs", new Callable<String>() {
            public String call() {
                return Minecraft.this.options.useVbo ? "Yes" : "No";
            }
        });
        report.getSystemDetails()
            .add(
                "Is Modded",
                new Callable<String>() {
                    public String call() throws Exception {
                        String s = ClientBrandRetriever.getClientModName();
                        if (!s.equals("vanilla")) {
                            return "Definitely; Client brand changed to '" + s + "'";
                        } else {
                            return Minecraft.class.getSigners() == null
                                ? "Very likely; Jar signature invalidated"
                                : "Probably not. Jar signature remains and client brand is untouched.";
                        }
                    }
                }
            );
        report.getSystemDetails().add("Type", new Callable<String>() {
            public String call() throws Exception {
                return "Client (map_client.txt)";
            }
        });
        report.getSystemDetails().add("Resource Packs", new Callable<String>() {
            public String call() throws Exception {
                StringBuilder stringbuilder = new StringBuilder();

                for (String s : Minecraft.this.options.resourcePacks) {
                    if (stringbuilder.length() > 0) {
                        stringbuilder.append(", ");
                    }

                    stringbuilder.append(s);
                    if (Minecraft.this.options.incompatibleResourcePacks.contains(s)) {
                        stringbuilder.append(" (incompatible)");
                    }
                }

                return stringbuilder.toString();
            }
        });
        report.getSystemDetails().add("Current Language", new Callable<String>() {
            public String call() throws Exception {
                return Minecraft.this.languageManager.getLanguage().toString();
            }
        });
        report.getSystemDetails().add("Profiler Position", new Callable<String>() {
            public String call() throws Exception {
                return Minecraft.this.profiler.profiling ? Minecraft.this.profiler.getCurrentLocation() : "N/A (disabled)";
            }
        });
        report.getSystemDetails().add("CPU", new Callable<String>() {
            public String call() {
                return GLX.getCpuInfo();
            }
        });
        if (this.world != null) {
            this.world.populateCrashReport(report);
        }

        return report;
    }

    public static Minecraft getInstance() {
        return INSTANCE;
    }

    public ListenableFuture<Object> reloadResourcesAsync() {
        return this.execute(new Runnable() {
            @Override
            public void run() {
                Minecraft.this.reloadResources();
            }
        });
    }

    @Override
    public void populateSnooper(Snooper snooper) {
        snooper.putDynamic("fps", currentFps);
        snooper.putDynamic("vsync_enabled", this.options.vsync);
        snooper.putDynamic("display_frequency", Display.getDisplayMode().getFrequency());
        snooper.putDynamic("display_type", this.fullscreen ? "fullscreen" : "windowed");
        snooper.putDynamic("run_time", (MinecraftServer.getTimeMillis() - snooper.getInitTime()) / 60L * 1000L);
        snooper.putDynamic("current_action", this.getCurrentAction());
        String s = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? "little" : "big";
        snooper.putDynamic("endianness", s);
        snooper.putDynamic("resource_packs", this.resourcePacks.getApplied().size());
        int i = 0;

        for (ResourcePacks.Entry resourcepacks$entry : this.resourcePacks.getApplied()) {
            snooper.putDynamic("resource_pack[" + i++ + "]", resourcepacks$entry.getName());
        }

        if (this.server != null && this.server.getSnooper() != null) {
            snooper.putDynamic("snooper_partner", this.server.getSnooper().getToken());
        }
    }

    private String getCurrentAction() {
        if (this.server != null) {
            return this.server.isPublished() ? "hosting_lan" : "singleplayer";
        } else if (this.currentServerEntry != null) {
            return this.currentServerEntry.isLocal() ? "playing_lan" : "multiplayer";
        } else {
            return "out_of_game";
        }
    }

    @Override
    public void initSnooper(Snooper snooper) {
        snooper.putFixed("opengl_version", GL11.glGetString(7938));
        snooper.putFixed("opengl_vendor", GL11.glGetString(7936));
        snooper.putFixed("client_brand", ClientBrandRetriever.getClientModName());
        snooper.putFixed("launched_version", this.gameVersion);
        ContextCapabilities contextcapabilities = GLContext.getCapabilities();
        snooper.putFixed("gl_caps[ARB_arrays_of_arrays]", contextcapabilities.GL_ARB_arrays_of_arrays);
        snooper.putFixed("gl_caps[ARB_base_instance]", contextcapabilities.GL_ARB_base_instance);
        snooper.putFixed("gl_caps[ARB_blend_func_extended]", contextcapabilities.GL_ARB_blend_func_extended);
        snooper.putFixed("gl_caps[ARB_clear_buffer_object]", contextcapabilities.GL_ARB_clear_buffer_object);
        snooper.putFixed("gl_caps[ARB_color_buffer_float]", contextcapabilities.GL_ARB_color_buffer_float);
        snooper.putFixed("gl_caps[ARB_compatibility]", contextcapabilities.GL_ARB_compatibility);
        snooper.putFixed("gl_caps[ARB_compressed_texture_pixel_storage]", contextcapabilities.GL_ARB_compressed_texture_pixel_storage);
        snooper.putFixed("gl_caps[ARB_compute_shader]", contextcapabilities.GL_ARB_compute_shader);
        snooper.putFixed("gl_caps[ARB_copy_buffer]", contextcapabilities.GL_ARB_copy_buffer);
        snooper.putFixed("gl_caps[ARB_copy_image]", contextcapabilities.GL_ARB_copy_image);
        snooper.putFixed("gl_caps[ARB_depth_buffer_float]", contextcapabilities.GL_ARB_depth_buffer_float);
        snooper.putFixed("gl_caps[ARB_compute_shader]", contextcapabilities.GL_ARB_compute_shader);
        snooper.putFixed("gl_caps[ARB_copy_buffer]", contextcapabilities.GL_ARB_copy_buffer);
        snooper.putFixed("gl_caps[ARB_copy_image]", contextcapabilities.GL_ARB_copy_image);
        snooper.putFixed("gl_caps[ARB_depth_buffer_float]", contextcapabilities.GL_ARB_depth_buffer_float);
        snooper.putFixed("gl_caps[ARB_depth_clamp]", contextcapabilities.GL_ARB_depth_clamp);
        snooper.putFixed("gl_caps[ARB_depth_texture]", contextcapabilities.GL_ARB_depth_texture);
        snooper.putFixed("gl_caps[ARB_draw_buffers]", contextcapabilities.GL_ARB_draw_buffers);
        snooper.putFixed("gl_caps[ARB_draw_buffers_blend]", contextcapabilities.GL_ARB_draw_buffers_blend);
        snooper.putFixed("gl_caps[ARB_draw_elements_base_vertex]", contextcapabilities.GL_ARB_draw_elements_base_vertex);
        snooper.putFixed("gl_caps[ARB_draw_indirect]", contextcapabilities.GL_ARB_draw_indirect);
        snooper.putFixed("gl_caps[ARB_draw_instanced]", contextcapabilities.GL_ARB_draw_instanced);
        snooper.putFixed("gl_caps[ARB_explicit_attrib_location]", contextcapabilities.GL_ARB_explicit_attrib_location);
        snooper.putFixed("gl_caps[ARB_explicit_uniform_location]", contextcapabilities.GL_ARB_explicit_uniform_location);
        snooper.putFixed("gl_caps[ARB_fragment_layer_viewport]", contextcapabilities.GL_ARB_fragment_layer_viewport);
        snooper.putFixed("gl_caps[ARB_fragment_program]", contextcapabilities.GL_ARB_fragment_program);
        snooper.putFixed("gl_caps[ARB_fragment_shader]", contextcapabilities.GL_ARB_fragment_shader);
        snooper.putFixed("gl_caps[ARB_fragment_program_shadow]", contextcapabilities.GL_ARB_fragment_program_shadow);
        snooper.putFixed("gl_caps[ARB_framebuffer_object]", contextcapabilities.GL_ARB_framebuffer_object);
        snooper.putFixed("gl_caps[ARB_framebuffer_sRGB]", contextcapabilities.GL_ARB_framebuffer_sRGB);
        snooper.putFixed("gl_caps[ARB_geometry_shader4]", contextcapabilities.GL_ARB_geometry_shader4);
        snooper.putFixed("gl_caps[ARB_gpu_shader5]", contextcapabilities.GL_ARB_gpu_shader5);
        snooper.putFixed("gl_caps[ARB_half_float_pixel]", contextcapabilities.GL_ARB_half_float_pixel);
        snooper.putFixed("gl_caps[ARB_half_float_vertex]", contextcapabilities.GL_ARB_half_float_vertex);
        snooper.putFixed("gl_caps[ARB_instanced_arrays]", contextcapabilities.GL_ARB_instanced_arrays);
        snooper.putFixed("gl_caps[ARB_map_buffer_alignment]", contextcapabilities.GL_ARB_map_buffer_alignment);
        snooper.putFixed("gl_caps[ARB_map_buffer_range]", contextcapabilities.GL_ARB_map_buffer_range);
        snooper.putFixed("gl_caps[ARB_multisample]", contextcapabilities.GL_ARB_multisample);
        snooper.putFixed("gl_caps[ARB_multitexture]", contextcapabilities.GL_ARB_multitexture);
        snooper.putFixed("gl_caps[ARB_occlusion_query2]", contextcapabilities.GL_ARB_occlusion_query2);
        snooper.putFixed("gl_caps[ARB_pixel_buffer_object]", contextcapabilities.GL_ARB_pixel_buffer_object);
        snooper.putFixed("gl_caps[ARB_seamless_cube_map]", contextcapabilities.GL_ARB_seamless_cube_map);
        snooper.putFixed("gl_caps[ARB_shader_objects]", contextcapabilities.GL_ARB_shader_objects);
        snooper.putFixed("gl_caps[ARB_shader_stencil_export]", contextcapabilities.GL_ARB_shader_stencil_export);
        snooper.putFixed("gl_caps[ARB_shader_texture_lod]", contextcapabilities.GL_ARB_shader_texture_lod);
        snooper.putFixed("gl_caps[ARB_shadow]", contextcapabilities.GL_ARB_shadow);
        snooper.putFixed("gl_caps[ARB_shadow_ambient]", contextcapabilities.GL_ARB_shadow_ambient);
        snooper.putFixed("gl_caps[ARB_stencil_texturing]", contextcapabilities.GL_ARB_stencil_texturing);
        snooper.putFixed("gl_caps[ARB_sync]", contextcapabilities.GL_ARB_sync);
        snooper.putFixed("gl_caps[ARB_tessellation_shader]", contextcapabilities.GL_ARB_tessellation_shader);
        snooper.putFixed("gl_caps[ARB_texture_border_clamp]", contextcapabilities.GL_ARB_texture_border_clamp);
        snooper.putFixed("gl_caps[ARB_texture_buffer_object]", contextcapabilities.GL_ARB_texture_buffer_object);
        snooper.putFixed("gl_caps[ARB_texture_cube_map]", contextcapabilities.GL_ARB_texture_cube_map);
        snooper.putFixed("gl_caps[ARB_texture_cube_map_array]", contextcapabilities.GL_ARB_texture_cube_map_array);
        snooper.putFixed("gl_caps[ARB_texture_non_power_of_two]", contextcapabilities.GL_ARB_texture_non_power_of_two);
        snooper.putFixed("gl_caps[ARB_uniform_buffer_object]", contextcapabilities.GL_ARB_uniform_buffer_object);
        snooper.putFixed("gl_caps[ARB_vertex_blend]", contextcapabilities.GL_ARB_vertex_blend);
        snooper.putFixed("gl_caps[ARB_vertex_buffer_object]", contextcapabilities.GL_ARB_vertex_buffer_object);
        snooper.putFixed("gl_caps[ARB_vertex_program]", contextcapabilities.GL_ARB_vertex_program);
        snooper.putFixed("gl_caps[ARB_vertex_shader]", contextcapabilities.GL_ARB_vertex_shader);
        snooper.putFixed("gl_caps[EXT_bindable_uniform]", contextcapabilities.GL_EXT_bindable_uniform);
        snooper.putFixed("gl_caps[EXT_blend_equation_separate]", contextcapabilities.GL_EXT_blend_equation_separate);
        snooper.putFixed("gl_caps[EXT_blend_func_separate]", contextcapabilities.GL_EXT_blend_func_separate);
        snooper.putFixed("gl_caps[EXT_blend_minmax]", contextcapabilities.GL_EXT_blend_minmax);
        snooper.putFixed("gl_caps[EXT_blend_subtract]", contextcapabilities.GL_EXT_blend_subtract);
        snooper.putFixed("gl_caps[EXT_draw_instanced]", contextcapabilities.GL_EXT_draw_instanced);
        snooper.putFixed("gl_caps[EXT_framebuffer_multisample]", contextcapabilities.GL_EXT_framebuffer_multisample);
        snooper.putFixed("gl_caps[EXT_framebuffer_object]", contextcapabilities.GL_EXT_framebuffer_object);
        snooper.putFixed("gl_caps[EXT_framebuffer_sRGB]", contextcapabilities.GL_EXT_framebuffer_sRGB);
        snooper.putFixed("gl_caps[EXT_geometry_shader4]", contextcapabilities.GL_EXT_geometry_shader4);
        snooper.putFixed("gl_caps[EXT_gpu_program_parameters]", contextcapabilities.GL_EXT_gpu_program_parameters);
        snooper.putFixed("gl_caps[EXT_gpu_shader4]", contextcapabilities.GL_EXT_gpu_shader4);
        snooper.putFixed("gl_caps[EXT_multi_draw_arrays]", contextcapabilities.GL_EXT_multi_draw_arrays);
        snooper.putFixed("gl_caps[EXT_packed_depth_stencil]", contextcapabilities.GL_EXT_packed_depth_stencil);
        snooper.putFixed("gl_caps[EXT_paletted_texture]", contextcapabilities.GL_EXT_paletted_texture);
        snooper.putFixed("gl_caps[EXT_rescale_normal]", contextcapabilities.GL_EXT_rescale_normal);
        snooper.putFixed("gl_caps[EXT_separate_shader_objects]", contextcapabilities.GL_EXT_separate_shader_objects);
        snooper.putFixed("gl_caps[EXT_shader_image_load_store]", contextcapabilities.GL_EXT_shader_image_load_store);
        snooper.putFixed("gl_caps[EXT_shadow_funcs]", contextcapabilities.GL_EXT_shadow_funcs);
        snooper.putFixed("gl_caps[EXT_shared_texture_palette]", contextcapabilities.GL_EXT_shared_texture_palette);
        snooper.putFixed("gl_caps[EXT_stencil_clear_tag]", contextcapabilities.GL_EXT_stencil_clear_tag);
        snooper.putFixed("gl_caps[EXT_stencil_two_side]", contextcapabilities.GL_EXT_stencil_two_side);
        snooper.putFixed("gl_caps[EXT_stencil_wrap]", contextcapabilities.GL_EXT_stencil_wrap);
        snooper.putFixed("gl_caps[EXT_texture_3d]", contextcapabilities.GL_EXT_texture_3d);
        snooper.putFixed("gl_caps[EXT_texture_array]", contextcapabilities.GL_EXT_texture_array);
        snooper.putFixed("gl_caps[EXT_texture_buffer_object]", contextcapabilities.GL_EXT_texture_buffer_object);
        snooper.putFixed("gl_caps[EXT_texture_integer]", contextcapabilities.GL_EXT_texture_integer);
        snooper.putFixed("gl_caps[EXT_texture_lod_bias]", contextcapabilities.GL_EXT_texture_lod_bias);
        snooper.putFixed("gl_caps[EXT_texture_sRGB]", contextcapabilities.GL_EXT_texture_sRGB);
        snooper.putFixed("gl_caps[EXT_vertex_shader]", contextcapabilities.GL_EXT_vertex_shader);
        snooper.putFixed("gl_caps[EXT_vertex_weighting]", contextcapabilities.GL_EXT_vertex_weighting);
        snooper.putFixed("gl_caps[gl_max_vertex_uniforms]", GL11.glGetInteger(35658));
        GL11.glGetError();
        snooper.putFixed("gl_caps[gl_max_fragment_uniforms]", GL11.glGetInteger(35657));
        GL11.glGetError();
        snooper.putFixed("gl_caps[gl_max_vertex_attribs]", GL11.glGetInteger(34921));
        GL11.glGetError();
        snooper.putFixed("gl_caps[gl_max_vertex_texture_image_units]", GL11.glGetInteger(35660));
        GL11.glGetError();
        snooper.putFixed("gl_caps[gl_max_texture_image_units]", GL11.glGetInteger(34930));
        GL11.glGetError();
        snooper.putFixed("gl_caps[gl_max_texture_image_units]", GL11.glGetInteger(35071));
        GL11.glGetError();
        snooper.putFixed("gl_max_texture_size", getMaxTextureSize());
    }

    public static int getMaxTextureSize() {
        for (int i = 16384; i > 0; i >>= 1) {
            GL11.glTexImage2D(32868, 0, 6408, i, i, 0, 6408, 5121, (ByteBuffer)null);
            int j = GL11.glGetTexLevelParameteri(32868, 0, 4096);
            if (j != 0) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public boolean isSnooperEnabled() {
        return this.options.snooperEnabled;
    }

    public void setCurrentServerEntry(ServerListEntry serverEntry) {
        this.currentServerEntry = serverEntry;
    }

    public ServerListEntry getCurrentServerEntry() {
        return this.currentServerEntry;
    }

    public boolean isIntegratedServerRunning() {
        return this.isIntegratedServerRunning;
    }

    public boolean isSingleplayer() {
        return this.isIntegratedServerRunning && this.server != null;
    }

    public IntegratedServer getServer() {
        return this.server;
    }

    public static void stopServer() {
        if (INSTANCE != null) {
            IntegratedServer integratedserver = INSTANCE.getServer();
            if (integratedserver != null) {
                integratedserver.shutdown();
            }
        }
    }

    public Snooper getSnooper() {
        return this.snooper;
    }

    public static long getTime() {
        return Sys.getTime() * 1000L / Sys.getTimerResolution();
    }

    public boolean isFullscreen() {
        return this.fullscreen;
    }

    public Session getSession() {
        return this.session;
    }

    public PropertyMap getUserProperties() {
        return this.userProperties;
    }

    public PropertyMap getProfileProperties() {
        if (this.profileProperties.isEmpty()) {
            GameProfile gameprofile = this.getSessionService().fillProfileProperties(this.session.getProfile(), false);
            this.profileProperties.putAll(gameprofile.getProperties());
        }

        return this.profileProperties;
    }

    public Proxy getNetworkProxy() {
        return this.proxy;
    }

    public TextureManager getTextureManager() {
        return this.textureManager;
    }

    public ResourceManager getResourceManager() {
        return this.resourceManager;
    }

    public ResourcePacks getResourcePacks() {
        return this.resourcePacks;
    }

    public LanguageManager getLanguageManager() {
        return this.languageManager;
    }

    public TextureAtlas getBlocksAtlas() {
        return this.blocksAtlas;
    }

    public boolean is64Bit() {
        return this.is64Bit;
    }

    public boolean isPaused() {
        return this.paused;
    }

    public SoundManager getSoundManager() {
        return this.soundManager;
    }

    public MusicManager.Music getMusicEnvironment() {
        if (this.player != null) {
            if (this.player.world.dimension instanceof NetherDimension) {
                return MusicManager.Music.NETHER;
            } else if (this.player.world.dimension instanceof TheEndDimension) {
                return BossBar.name != null && BossBar.timer > 0 ? MusicManager.Music.END_BOSS : MusicManager.Music.END;
            } else {
                return this.player.abilities.creativeMode && this.player.abilities.canFly ? MusicManager.Music.CREATIVE : MusicManager.Music.GAME;
            }
        } else {
            return MusicManager.Music.MENU;
        }
    }

    public TwitchStream getTwitchStream() {
        return this.twitchStream;
    }

    public void handleGuiKeyBindings() {
        int i = Keyboard.getEventKey() == 0 ? Keyboard.getEventCharacter() : Keyboard.getEventKey();
        if (i != 0 && !Keyboard.isRepeatEvent()) {
            if (!(this.screen instanceof ControlsOptionsScreen) || ((ControlsOptionsScreen)this.screen).time <= getTime() - 20L) {
                if (Keyboard.getEventKeyState()) {
                    if (i == this.options.streamStartStopKey.getKeyCode()) {
                        if (this.getTwitchStream().isBroadcasting()) {
                            this.getTwitchStream().stop();
                        } else if (this.getTwitchStream().isReadyToBroadcast()) {
                            this.openScreen(new ConfirmScreen(new ConfirmationListener() {
                                @Override
                                public void confirmResult(boolean result, int id) {
                                    if (result) {
                                        Minecraft.this.getTwitchStream().start();
                                    }

                                    Minecraft.this.openScreen(null);
                                }
                            }, I18n.translate("stream.confirm_start"), "", 0));
                        } else if (!this.getTwitchStream().m_2070231() || !this.getTwitchStream().canBroadcast()) {
                            StreamUnavailableScreen.m_8434758(this.screen);
                        } else if (this.world != null) {
                            this.gui.getChat().addMessage(new LiteralText("Not ready to start streaming yet!"));
                        }
                    } else if (i == this.options.streamPauseKey.getKeyCode()) {
                        if (this.getTwitchStream().isBroadcasting()) {
                            if (this.getTwitchStream().isPaused()) {
                                this.getTwitchStream().resume();
                            } else {
                                this.getTwitchStream().pause();
                            }
                        }
                    } else if (i == this.options.streamCommercialKey.getKeyCode()) {
                        if (this.getTwitchStream().isBroadcasting()) {
                            this.getTwitchStream().runCommercial();
                        }
                    } else if (i == this.options.streamToggleMicKey.getKeyCode()) {
                        this.twitchStream.m_9441213(true);
                    } else if (i == this.options.fullscreenKey.getKeyCode()) {
                        this.toggleFullscreen();
                    } else if (i == this.options.screenshotKey.getKeyCode()) {
                        this.gui.getChat().addMessage(Screenshot.take(this.gameDir, this.width, this.height, this.renderTarget));
                    }
                } else if (i == this.options.streamToggleMicKey.getKeyCode()) {
                    this.twitchStream.m_9441213(false);
                }
            }
        }
    }

    public MinecraftSessionService getSessionService() {
        return this.sessionService;
    }

    public SkinManager getSkinManager() {
        return this.skinManager;
    }

    public Entity getCamera() {
        return this.camera;
    }

    public void setCamera(Entity camera) {
        this.camera = camera;
        this.gameRenderer.updateShader(camera);
    }

    public <V> ListenableFuture<V> execute(Callable<V> task) {
        Validate.notNull(task);
        if (!this.isOnSameThread()) {
            ListenableFutureTask<V> listenablefuturetask = ListenableFutureTask.create(task);
            synchronized (this.tasks) {
                this.tasks.add(listenablefuturetask);
                return listenablefuturetask;
            }
        } else {
            try {
                return Futures.immediateFuture(task.call());
            } catch (Exception exception) {
                return Futures.immediateFailedCheckedFuture(exception);
            }
        }
    }

    @Override
    public ListenableFuture<Object> execute(Runnable task) {
        Validate.notNull(task);
        return this.execute(Executors.callable(task));
    }

    @Override
    public boolean isOnSameThread() {
        return Thread.currentThread() == this.thread;
    }

    public BlockRenderDispatcher getBlockRenderDispatcher() {
        return this.blockRenderer;
    }

    public EntityRenderDispatcher getEntityRenderDispatcher() {
        return this.entityRenderDispatcher;
    }

    public ItemRenderer getItemRenderer() {
        return this.itemRenderer;
    }

    public ItemInHandRenderer getItemInHandRenderer() {
        return this.itemInHandRenderer;
    }

    public static int getCurrentFps() {
        return currentFps;
    }

    public FrameTimeLogger getFrameTimeLogger() {
        return this.frameTimeLogger;
    }

    public static Map<String, String> getHttpRequestProperties() {
        Map<String, String> map = Maps.newHashMap();
        map.put("X-Minecraft-Username", getInstance().getSession().getUsername());
        map.put("X-Minecraft-UUID", getInstance().getSession().getUuid());
        map.put("X-Minecraft-Version", "1.8.8");
        return map;
    }

    public boolean isConnectedToRealms() {
        return this.connectedToRealms;
    }

    public void setConnectedToRealms(boolean connectedToRealms) {
        this.connectedToRealms = connectedToRealms;
    }
}
