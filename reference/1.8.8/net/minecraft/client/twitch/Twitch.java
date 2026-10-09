package net.minecraft.client.twitch;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.properties.Property;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.TwitchUserInfoScreen;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Formatting;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.HttpUtil;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.Utils;
import net.minecraft.util.math.MathHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.lwjgl.opengl.GL11;
import tv.twitch.AuthToken;
import tv.twitch.ErrorCode;
import tv.twitch.broadcast.EncodingCpuUsage;
import tv.twitch.broadcast.FrameBuffer;
import tv.twitch.broadcast.GameInfo;
import tv.twitch.broadcast.IngestList;
import tv.twitch.broadcast.IngestServer;
import tv.twitch.broadcast.StreamInfo;
import tv.twitch.broadcast.VideoParams;
import tv.twitch.chat.ChatRawMessage;
import tv.twitch.chat.ChatTokenizedMessage;
import tv.twitch.chat.ChatUserInfo;
import tv.twitch.chat.ChatUserMode;
import tv.twitch.chat.ChatUserSubscription;

public class Twitch implements BroadcastController.Listener, TwitchChat.Listener, IngestTester.Listener, TwitchStream {
    private static final Logger LOGGER = LogManager.getLogger();
    public static final Marker MARKER = MarkerManager.getMarker("STREAM");
    private final BroadcastController broadcastController;
    private final TwitchChat chat;
    private String channel;
    private final Minecraft minecraft;
    private final Text title = new LiteralText("Twitch");
    private final Map<String, ChatUserInfo> chatUserInfo = Maps.newHashMap();
    private RenderTarget renderTarget;
    private boolean sendMetadata;
    private int targetFps = 30;
    private long f_2181952 = 0L;
    private boolean f_1738353 = false;
    private boolean loggedIn;
    private boolean paused;
    private boolean f_6553335;
    private TwitchStream.ErrorReason errorReason = TwitchStream.ErrorReason.ERROR;
    private static boolean f_9488135;

    public Twitch(Minecraft minecraft, Property twitchAuth) {
        this.minecraft = minecraft;
        this.broadcastController = new BroadcastController();
        this.chat = new TwitchChat();
        this.broadcastController.setListener(this);
        this.chat.setListener(this);
        this.broadcastController.m_2881112("nmt37qblda36pvonovdkbopzfzw3wlq");
        this.chat.m_5028497("nmt37qblda36pvonovdkbopzfzw3wlq");
        this.title.getStyle().setColor(Formatting.DARK_PURPLE);
        if (twitchAuth != null && !Strings.isNullOrEmpty(twitchAuth.getValue()) && GLX.useFramebufferObjects) {
            Thread thread = new Thread("Twitch authenticator") {
                @Override
                public void run() {
                    try {
                        URL url = new URL("https://api.twitch.tv/kraken?oauth_token=" + URLEncoder.encode(twitchAuth.getValue(), "UTF-8"));
                        String s = HttpUtil.getUrlContents(url);
                        JsonObject jsonobject = JsonUtils.asJsonObject(new JsonParser().parse(s), "Response");
                        JsonObject jsonobject1 = JsonUtils.getJsonObject(jsonobject, "token");
                        if (JsonUtils.getBoolean(jsonobject1, "valid")) {
                            String s1 = JsonUtils.getString(jsonobject1, "user_name");
                            Twitch.LOGGER.debug(Twitch.MARKER, "Authenticated with twitch; username is {}", s1);
                            AuthToken authtoken = new AuthToken();
                            authtoken.data = twitchAuth.getValue();
                            Twitch.this.broadcastController.authenticate(s1, authtoken);
                            Twitch.this.chat.setUserName(s1);
                            Twitch.this.chat.setAuthToken(authtoken);
                            Runtime.getRuntime().addShutdownHook(new Thread("Twitch shutdown hook") {
                                @Override
                                public void run() {
                                    Twitch.this.shutdown();
                                }
                            });
                            Twitch.this.broadcastController.m_1098794();
                            Twitch.this.chat.m_0094708();
                        } else {
                            Twitch.this.errorReason = TwitchStream.ErrorReason.INVALID_TOKEN;
                            Twitch.LOGGER.error(Twitch.MARKER, "Given twitch access token is invalid");
                        }
                    } catch (IOException ioexception) {
                        Twitch.this.errorReason = TwitchStream.ErrorReason.ERROR;
                        Twitch.LOGGER.error(Twitch.MARKER, "Could not authenticate with twitch", ioexception);
                    }
                }
            };
            thread.setDaemon(true);
            thread.start();
        }
    }

    @Override
    public void shutdown() {
        LOGGER.debug(MARKER, "Shutdown streaming");
        this.broadcastController.shutdown();
        this.chat.shutdown();
    }

    @Override
    public void update() {
        int i = this.minecraft.options.streamChatEnabled;
        boolean flag = this.channel != null && this.chat.isChannelConnected(this.channel);
        boolean flag1 = this.chat.getState() == TwitchChat.State.INITIALIZED
            && (this.channel == null || this.chat.getChannelState(this.channel) == TwitchChat.ChannelState.DISCONNECTED);
        if (i == 2) {
            if (flag) {
                LOGGER.debug(MARKER, "Disconnecting from twitch chat per user options");
                this.chat.disconnectChannel(this.channel);
            }
        } else if (i == 1) {
            if (flag1 && this.broadcastController.isLoggedIn()) {
                LOGGER.debug(MARKER, "Connecting to twitch chat per user options");
                this.connectChannel();
            }
        } else if (i == 0) {
            if (flag && !this.isBroadcasting()) {
                LOGGER.debug(MARKER, "Disconnecting from twitch chat as user is no longer streaming");
                this.chat.disconnectChannel(this.channel);
            } else if (flag1 && this.isBroadcasting()) {
                LOGGER.debug(MARKER, "Connecting to twitch chat as user is streaming");
                this.connectChannel();
            }
        }

        this.broadcastController.update();
        this.chat.update();
    }

    protected void connectChannel() {
        TwitchChat.State twitchchat$state = this.chat.getState();
        String s = this.broadcastController.getChannelInfo().name;
        this.channel = s;
        if (twitchchat$state != TwitchChat.State.INITIALIZED) {
            LOGGER.warn("Invalid twitch chat state {}", new Object[]{twitchchat$state});
        } else if (this.chat.getChannelState(this.channel) == TwitchChat.ChannelState.DISCONNECTED) {
            this.chat.connectChannel(s);
        } else {
            LOGGER.warn("Invalid twitch chat state {}", new Object[]{twitchchat$state});
        }
    }

    @Override
    public void submit() {
        if (this.broadcastController.isBroadcasting() && !this.broadcastController.isPaused()) {
            long i = System.nanoTime();
            long j = 1000000000 / this.targetFps;
            long k = i - this.f_2181952;
            boolean flag = k >= j;
            if (flag) {
                FrameBuffer framebuffer = this.broadcastController.pollFreeBuffer();
                RenderTarget rendertarget = this.minecraft.getRenderTarget();
                this.renderTarget.bindWrite(true);
                GlStateManager.matrixMode(5889);
                GlStateManager.pushMatrix();
                GlStateManager.loadIdentity();
                GlStateManager.ortho(0.0, this.renderTarget.viewWidth, this.renderTarget.viewHeight, 0.0, 1000.0, 3000.0);
                GlStateManager.matrixMode(5888);
                GlStateManager.pushMatrix();
                GlStateManager.loadIdentity();
                GlStateManager.translatef(0.0F, 0.0F, -2000.0F);
                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                GlStateManager.viewport(0, 0, this.renderTarget.viewWidth, this.renderTarget.viewHeight);
                GlStateManager.enableTexture();
                GlStateManager.disableAlphaTest();
                GlStateManager.disableBlend();
                float f = this.renderTarget.viewWidth;
                float f1 = this.renderTarget.viewHeight;
                float f2 = (float)rendertarget.viewWidth / rendertarget.width;
                float f3 = (float)rendertarget.viewHeight / rendertarget.height;
                rendertarget.bindRead();
                GL11.glTexParameterf(3553, 10241, 9729.0F);
                GL11.glTexParameterf(3553, 10240, 9729.0F);
                Tesselator tesselator = Tesselator.getInstance();
                BufferBuilder bufferbuilder = tesselator.getBuffer();
                bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX);
                bufferbuilder.vertex(0.0, f1, 0.0).texture(0.0, f3).nextVertex();
                bufferbuilder.vertex(f, f1, 0.0).texture(f2, f3).nextVertex();
                bufferbuilder.vertex(f, 0.0, 0.0).texture(f2, 0.0).nextVertex();
                bufferbuilder.vertex(0.0, 0.0, 0.0).texture(0.0, 0.0).nextVertex();
                tesselator.end();
                rendertarget.unbindRead();
                GlStateManager.popMatrix();
                GlStateManager.matrixMode(5889);
                GlStateManager.popMatrix();
                GlStateManager.matrixMode(5888);
                this.broadcastController.readPixels(framebuffer);
                this.renderTarget.unbindWrite();
                this.broadcastController.submitTexturePointer(framebuffer);
                this.f_2181952 = i;
            }
        }
    }

    @Override
    public boolean canBroadcast() {
        return this.broadcastController.isLoggedIn();
    }

    @Override
    public boolean isReadyToBroadcast() {
        return this.broadcastController.isReadyToBroadcast();
    }

    @Override
    public boolean isBroadcasting() {
        return this.broadcastController.isBroadcasting();
    }

    @Override
    public void sendActionMetadata(StreamMetadata metadata, long time) {
        if (this.isBroadcasting() && this.sendMetadata) {
            long i = this.broadcastController.getStreamTime();
            if (!this.broadcastController.sendActionMetadata(metadata.getName(), i + time, metadata.getDescription(), metadata.serialize())) {
                LOGGER.warn(MARKER, "Couldn't send stream metadata action at {}: {}", i + time, metadata);
            } else {
                LOGGER.debug(MARKER, "Sent stream metadata action at {}: {}", i + time, metadata);
            }
        }
    }

    @Override
    public void sendSpanMetadata(StreamMetadata metadata, long start, long end) {
        if (this.isBroadcasting() && this.sendMetadata) {
            long i = this.broadcastController.getStreamTime();
            String s = metadata.getDescription();
            String s1 = metadata.serialize();
            long j = this.broadcastController.sendStartSpanMetadata(metadata.getName(), i + start, s, s1);
            if (j < 0L) {
                LOGGER.warn(MARKER, "Could not send stream metadata sequence from {} to {}: {}", i + start, i + end, metadata);
            } else if (this.broadcastController.sendEndSpanMetadata(metadata.getName(), i + end, j, s, s1)) {
                LOGGER.debug(MARKER, "Sent stream metadata sequence from {} to {}: {}", i + start, i + end, metadata);
            } else {
                LOGGER.warn(MARKER, "Half-sent stream metadata sequence from {} to {}: {}", i + start, i + end, metadata);
            }
        }
    }

    @Override
    public boolean isPaused() {
        return this.broadcastController.isPaused();
    }

    @Override
    public void runCommercial() {
        if (this.broadcastController.runCommercial()) {
            LOGGER.debug(MARKER, "Requested commercial from Twitch");
        } else {
            LOGGER.warn(MARKER, "Could not request commercial from Twitch");
        }
    }

    @Override
    public void pause() {
        this.broadcastController.pause();
        this.paused = true;
        this.updateVolume();
    }

    @Override
    public void resume() {
        this.broadcastController.resume();
        this.paused = false;
        this.updateVolume();
    }

    @Override
    public void updateVolume() {
        if (this.isBroadcasting()) {
            float f = this.minecraft.options.streamSystemVolume;
            boolean flag = this.paused || f <= 0.0F;
            this.broadcastController.setPlaybackVolume(flag ? 0.0F : f);
            this.broadcastController.setRecorderVolume(this.m_1283038() ? 0.0F : this.minecraft.options.streamMicVolume);
        }
    }

    @Override
    public void start() {
        GameOptions gameoptions = this.minecraft.options;
        VideoParams videoparams = this.broadcastController
            .getVideoParams(
                m_1012270(gameoptions.streamKbps),
                m_7422955(gameoptions.streamFps),
                m_2187119(gameoptions.streamBytesPerPixel),
                (float)this.minecraft.width / this.minecraft.height
            );
        switch (gameoptions.streamCompression) {
            case 0:
                videoparams.encodingCpuUsage = EncodingCpuUsage.TTV_ECU_LOW;
                break;
            case 1:
                videoparams.encodingCpuUsage = EncodingCpuUsage.TTV_ECU_MEDIUM;
                break;
            case 2:
                videoparams.encodingCpuUsage = EncodingCpuUsage.TTV_ECU_HIGH;
        }

        if (this.renderTarget == null) {
            this.renderTarget = new RenderTarget(videoparams.outputWidth, videoparams.outputHeight, false);
        } else {
            this.renderTarget.resize(videoparams.outputWidth, videoparams.outputHeight);
        }

        if (gameoptions.streamPreferredServer != null && gameoptions.streamPreferredServer.length() > 0) {
            for (IngestServer ingestserver : this.getServers()) {
                if (ingestserver.serverUrl.equals(gameoptions.streamPreferredServer)) {
                    this.broadcastController.setDefaultServer(ingestserver);
                    break;
                }
            }
        }

        this.targetFps = videoparams.targetFps;
        this.sendMetadata = gameoptions.streamSendMetadata;
        this.broadcastController.start(videoparams);
        LOGGER.info(
            MARKER,
            "Streaming at {}/{} at {} kbps to {}",
            videoparams.outputWidth,
            videoparams.outputHeight,
            videoparams.maxKbps,
            this.broadcastController.getDefaultServer().serverUrl
        );
        this.broadcastController.setStreamInfo(null, "Minecraft", null);
    }

    @Override
    public void stop() {
        if (this.broadcastController.stop()) {
            LOGGER.info(MARKER, "Stopped streaming to Twitch");
        } else {
            LOGGER.warn(MARKER, "Could not stop streaming to Twitch");
        }
    }

    @Override
    public void requestAuthTokenCallback(ErrorCode errorCode, AuthToken authToken) {
    }

    @Override
    public void loginCallback(ErrorCode errorCode) {
        if (ErrorCode.succeeded(errorCode)) {
            LOGGER.debug(MARKER, "Login attempt successful");
            this.loggedIn = true;
        } else {
            LOGGER.warn(MARKER, "Login attempt unsuccessful: {} (error code {})", ErrorCode.getString(errorCode), errorCode.getValue());
            this.loggedIn = false;
        }
    }

    @Override
    public void getGameNameListCallback(ErrorCode errorCode, GameInfo[] gameInfos) {
    }

    @Override
    public void broadcastStateChanged(BroadcastController.State state) {
        LOGGER.debug(MARKER, "Broadcast state changed to {}", state);
        if (state == BroadcastController.State.INITIALIZED) {
            this.broadcastController.setStatus(BroadcastController.State.AUTHENTICATED);
        }
    }

    @Override
    public void loggedOut() {
        LOGGER.info(MARKER, "Logged out of twitch");
    }

    @Override
    public void getStreamInfoCallback(StreamInfo streamInfo) {
        LOGGER.debug(MARKER, "Stream info updated; {} viewers on stream ID {}", streamInfo.viewers, streamInfo.streamId);
    }

    @Override
    public void getIngestServersCallback(IngestList ingestServers) {
    }

    @Override
    public void errorSubmittingFrame(ErrorCode errorCode) {
        LOGGER.warn(MARKER, "Issue submitting frame: {} (Error code {})", ErrorCode.getString(errorCode), errorCode.getValue());
        this.minecraft.gui.getChat().addMessage(new LiteralText("Issue streaming frame: " + errorCode + " (" + ErrorCode.getString(errorCode) + ")"), 2);
    }

    @Override
    public void broadcastStartCallback() {
        this.updateVolume();
        LOGGER.info(MARKER, "Broadcast to Twitch has started");
    }

    @Override
    public void broadcastStopCallback() {
        LOGGER.info(MARKER, "Broadcast to Twitch has stopped");
    }

    @Override
    public void broadcastStartFailed(ErrorCode errorCode) {
        if (errorCode == ErrorCode.TTV_EC_SOUNDFLOWER_NOT_INSTALLED) {
            Text text = new TranslatableText("stream.unavailable.soundflower.chat.link");
            text.getStyle()
                .setClickEvent(
                    new ClickEvent(
                        ClickEvent.Action.OPEN_URL,
                        "https://help.mojang.com/customer/portal/articles/1374877-configuring-soundflower-for-streaming-on-apple-computers"
                    )
                );
            text.getStyle().setUnderlined(true);
            Text text1 = new TranslatableText("stream.unavailable.soundflower.chat", text);
            text1.getStyle().setColor(Formatting.DARK_RED);
            this.minecraft.gui.getChat().addMessage(text1);
        } else {
            Text text2 = new TranslatableText("stream.unavailable.unknown.chat", ErrorCode.getString(errorCode));
            text2.getStyle().setColor(Formatting.DARK_RED);
            this.minecraft.gui.getChat().addMessage(text2);
        }
    }

    @Override
    public void ingestStateChanged(IngestTester ingestTester, IngestTester.State state) {
        LOGGER.debug(MARKER, "Ingest test state changed to {}", state);
        if (state == IngestTester.State.FINISHED) {
            this.f_1738353 = true;
        }
    }

    public static int m_7422955(float f) {
        return MathHelper.floor(10.0F + f * 50.0F);
    }

    public static int m_1012270(float f) {
        return MathHelper.floor(230.0F + f * 3270.0F);
    }

    public static float m_2187119(float f) {
        return 0.1F + f * 0.1F;
    }

    @Override
    public IngestServer[] getServers() {
        return this.broadcastController.getServers().getServers();
    }

    @Override
    public void startIngestTesting() {
        IngestTester ingesttester = this.broadcastController.startIngestTesting();
        if (ingesttester != null) {
            ingesttester.setListener(this);
        }
    }

    @Override
    public IngestTester getIngestTester() {
        return this.broadcastController.getIngestTester();
    }

    @Override
    public boolean isIngestTesting() {
        return this.broadcastController.isIngestTesting();
    }

    @Override
    public int getViewerCount() {
        return this.isBroadcasting() ? this.broadcastController.getStreamInfo().viewers : 0;
    }

    @Override
    public void chatInitializationCallback(ErrorCode errorCode) {
        if (ErrorCode.failed(errorCode)) {
            LOGGER.error(MARKER, "Chat failed to initialize");
        }
    }

    @Override
    public void chatShutdownCallback(ErrorCode errorCode) {
        if (ErrorCode.failed(errorCode)) {
            LOGGER.error(MARKER, "Chat failed to shutdown");
        }
    }

    @Override
    public void chatStateChanged(TwitchChat.State state) {
    }

    @Override
    public void m_1947729(String string, ChatRawMessage[] chatRawMessages) {
        for (ChatRawMessage chatrawmessage : chatRawMessages) {
            this.m_7782418(chatrawmessage.userName, chatrawmessage);
            if (this.m_0254204(chatrawmessage.modes, chatrawmessage.subscriptions, this.minecraft.options.streamChatUserFilter)) {
                Text text = new LiteralText(chatrawmessage.userName);
                Text text1 = new TranslatableText(
                    "chat.stream." + (chatrawmessage.action ? "emote" : "text"), this.title, text, Formatting.strip(chatrawmessage.message)
                );
                if (chatrawmessage.action) {
                    text1.getStyle().setItalic(true);
                }

                Text text2 = new LiteralText("");
                text2.append(new TranslatableText("stream.userinfo.chatTooltip"));

                for (Text text3 : TwitchUserInfoScreen.m_0317031(chatrawmessage.modes, chatrawmessage.subscriptions, null)) {
                    text2.append("\n");
                    text2.append(text3);
                }

                text.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, text2));
                text.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.TWITCH_USER_INFO, chatrawmessage.userName));
                this.minecraft.gui.getChat().addMessage(text1);
            }
        }
    }

    @Override
    public void m_2764991(String string, ChatTokenizedMessage[] chatTokenizedMessages) {
    }

    private void m_7782418(String string, ChatRawMessage chatRawMessage) {
        ChatUserInfo chatuserinfo = this.chatUserInfo.get(string);
        if (chatuserinfo == null) {
            chatuserinfo = new ChatUserInfo();
            chatuserinfo.displayName = string;
            this.chatUserInfo.put(string, chatuserinfo);
        }

        chatuserinfo.subscriptions = chatRawMessage.subscriptions;
        chatuserinfo.modes = chatRawMessage.modes;
        chatuserinfo.nameColorARGB = chatRawMessage.nameColorARGB;
    }

    private boolean m_0254204(Set<ChatUserMode> set, Set<ChatUserSubscription> set2, int i) {
        return !set.contains(ChatUserMode.TTV_CHAT_USERMODE_BANNED)
            && (
                set.contains(ChatUserMode.TTV_CHAT_USERMODE_ADMINSTRATOR)
                    || set.contains(ChatUserMode.TTV_CHAT_USERMODE_MODERATOR)
                    || set.contains(ChatUserMode.TTV_CHAT_USERMODE_STAFF)
                    || i == 0
                    || i == 1 && set2.contains(ChatUserSubscription.TTV_CHAT_USERSUB_SUBSCRIBER)
            );
    }

    @Override
    public void chatChannelUserChangeCallback(String string, ChatUserInfo[] chatUserInfos, ChatUserInfo[] chatUserInfos2, ChatUserInfo[] chatUserInfos3) {
        for (ChatUserInfo chatuserinfo : chatUserInfos2) {
            this.chatUserInfo.remove(chatuserinfo.displayName);
        }

        for (ChatUserInfo chatuserinfo1 : chatUserInfos3) {
            this.chatUserInfo.put(chatuserinfo1.displayName, chatuserinfo1);
        }

        for (ChatUserInfo chatuserinfo2 : chatUserInfos) {
            this.chatUserInfo.put(chatuserinfo2.displayName, chatuserinfo2);
        }
    }

    @Override
    public void joinedChannel(String string) {
        LOGGER.debug(MARKER, "Chat connected");
    }

    @Override
    public void leftChannel(String string) {
        LOGGER.debug(MARKER, "Chat disconnected");
        this.chatUserInfo.clear();
    }

    @Override
    public void chatCleared(String string, String string2) {
    }

    @Override
    public void prepareChatEmoticonDataCallback() {
    }

    @Override
    public void m_2003643() {
    }

    @Override
    public void m_6004964(String string) {
    }

    @Override
    public void m_2088486(String string) {
    }

    @Override
    public boolean m_5700177() {
        return this.channel != null && this.channel.equals(this.broadcastController.getChannelInfo().name);
    }

    @Override
    public String getChannel() {
        return this.channel;
    }

    @Override
    public ChatUserInfo m_8560465(String string) {
        return this.chatUserInfo.get(string);
    }

    @Override
    public void m_7694443(String string) {
        this.chat.m_3680703(this.channel, string);
    }

    @Override
    public boolean m_2070231() {
        return f_9488135 && this.broadcastController.isAuthenticated();
    }

    @Override
    public ErrorCode m_3195041() {
        return !f_9488135 ? ErrorCode.TTV_EC_OS_TOO_OLD : this.broadcastController.getInitializationError();
    }

    @Override
    public boolean isLoggedIn() {
        return this.loggedIn;
    }

    @Override
    public void m_9441213(boolean bl) {
        this.f_6553335 = bl;
        this.updateVolume();
    }

    @Override
    public boolean m_1283038() {
        boolean flag = this.minecraft.options.streamMicToggleBehavior == 1;
        return this.paused || this.minecraft.options.streamMicVolume <= 0.0F || flag != this.f_6553335;
    }

    @Override
    public TwitchStream.ErrorReason getErrorReason() {
        return this.errorReason;
    }

    static {
        try {
            if (Utils.getOS() == Utils.OS.WINDOWS) {
                System.loadLibrary("avutil-ttv-51");
                System.loadLibrary("swresample-ttv-0");
                System.loadLibrary("libmp3lame-ttv");
                if (System.getProperty("os.arch").contains("64")) {
                    System.loadLibrary("libmfxsw64");
                } else {
                    System.loadLibrary("libmfxsw32");
                }
            }

            f_9488135 = true;
        } catch (Throwable throwable) {
            f_9488135 = false;
        }
    }
}
