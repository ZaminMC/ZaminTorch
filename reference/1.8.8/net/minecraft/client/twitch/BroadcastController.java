package net.minecraft.client.twitch;

import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.List;
import net.minecraft.util.ThreadSafeBuffer;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tv.twitch.AuthToken;
import tv.twitch.Core;
import tv.twitch.ErrorCode;
import tv.twitch.MessageLevel;
import tv.twitch.StandardCoreAPI;
import tv.twitch.broadcast.ArchivingState;
import tv.twitch.broadcast.AudioDeviceType;
import tv.twitch.broadcast.AudioParams;
import tv.twitch.broadcast.ChannelInfo;
import tv.twitch.broadcast.DesktopStreamAPI;
import tv.twitch.broadcast.EncodingCpuUsage;
import tv.twitch.broadcast.FrameBuffer;
import tv.twitch.broadcast.GameInfo;
import tv.twitch.broadcast.GameInfoList;
import tv.twitch.broadcast.IStatCallbacks;
import tv.twitch.broadcast.IStreamCallbacks;
import tv.twitch.broadcast.IngestList;
import tv.twitch.broadcast.IngestServer;
import tv.twitch.broadcast.PixelFormat;
import tv.twitch.broadcast.StartFlags;
import tv.twitch.broadcast.StatType;
import tv.twitch.broadcast.Stream;
import tv.twitch.broadcast.StreamInfo;
import tv.twitch.broadcast.StreamInfoForSetting;
import tv.twitch.broadcast.UserInfo;
import tv.twitch.broadcast.VideoParams;

public class BroadcastController {
    private static final Logger LOGGER = LogManager.getLogger();
    protected final int f_5109033 = 30;
    protected final int f_0359243 = 3;
    private static final ThreadSafeBuffer<String> ERRORS = new ThreadSafeBuffer<>(String.class, 50);
    private String f_2037199 = null;
    protected BroadcastController.Listener listener = null;
    protected String f_5892149 = "";
    protected String f_3175205 = "";
    protected String f_9541622 = "";
    protected boolean f_0473721 = true;
    protected Core core = null;
    protected Stream stream = null;
    protected List<FrameBuffer> captureBuffers = Lists.newArrayList();
    protected List<FrameBuffer> freeBuffers = Lists.newArrayList();
    protected boolean authenticated = false;
    protected boolean loggedIn = false;
    protected boolean f_1883860 = false;
    protected BroadcastController.State status = BroadcastController.State.UNINITIALIZED;
    protected String username = null;
    protected VideoParams videoParams = null;
    protected AudioParams audioParams = null;
    protected IngestList servers = new IngestList(new IngestServer[0]);
    protected IngestServer defaultServer = null;
    protected AuthToken token = new AuthToken();
    protected ChannelInfo channelInfo = new ChannelInfo();
    protected UserInfo userInfo = new UserInfo();
    protected StreamInfo streamInfo = new StreamInfo();
    protected ArchivingState archivingState = new ArchivingState();
    protected long f_5305953 = 0L;
    protected IngestTester ingestTester = null;
    private ErrorCode initializationError;
    protected IStreamCallbacks f_8506417 = new IStreamCallbacks() {
        @Override
        public void requestAuthTokenCallback(ErrorCode error, AuthToken authToken) {
            if (ErrorCode.succeeded(error)) {
                BroadcastController.this.token = authToken;
                BroadcastController.this.setStatus(BroadcastController.State.AUTHENTICATED);
            } else {
                BroadcastController.this.token.data = "";
                BroadcastController.this.setStatus(BroadcastController.State.INITIALIZED);
                String s = ErrorCode.getString(error);
                BroadcastController.this.error(String.format("RequestAuthTokenDoneCallback got failure: %s", s));
            }

            try {
                if (BroadcastController.this.listener != null) {
                    BroadcastController.this.listener.requestAuthTokenCallback(error, authToken);
                }
            } catch (Exception exception) {
                BroadcastController.this.error(exception.toString());
            }
        }

        @Override
        public void loginCallback(ErrorCode errorCode, ChannelInfo channelInfo) {
            if (ErrorCode.succeeded(errorCode)) {
                BroadcastController.this.channelInfo = channelInfo;
                BroadcastController.this.setStatus(BroadcastController.State.LOGGED_IN);
                BroadcastController.this.loggedIn = true;
            } else {
                BroadcastController.this.setStatus(BroadcastController.State.INITIALIZED);
                BroadcastController.this.loggedIn = false;
                String s = ErrorCode.getString(errorCode);
                BroadcastController.this.error(String.format("LoginCallback got failure: %s", s));
            }

            try {
                if (BroadcastController.this.listener != null) {
                    BroadcastController.this.listener.loginCallback(errorCode);
                }
            } catch (Exception exception) {
                BroadcastController.this.error(exception.toString());
            }
        }

        @Override
        public void getIngestServersCallback(ErrorCode errorCode, IngestList ingestList) {
            if (ErrorCode.succeeded(errorCode)) {
                BroadcastController.this.servers = ingestList;
                BroadcastController.this.defaultServer = BroadcastController.this.servers.getDefaultServer();
                BroadcastController.this.setStatus(BroadcastController.State.RECEIVED_INGEST_SERVERS);

                try {
                    if (BroadcastController.this.listener != null) {
                        BroadcastController.this.listener.getIngestServersCallback(ingestList);
                    }
                } catch (Exception exception) {
                    BroadcastController.this.error(exception.toString());
                }
            } else {
                String s = ErrorCode.getString(errorCode);
                BroadcastController.this.error(String.format("IngestListCallback got failure: %s", s));
                BroadcastController.this.setStatus(BroadcastController.State.LOGGING_IN);
            }
        }

        @Override
        public void getUserInfoCallback(ErrorCode errorCode, UserInfo userInfo) {
            BroadcastController.this.userInfo = userInfo;
            if (ErrorCode.failed(errorCode)) {
                String s = ErrorCode.getString(errorCode);
                BroadcastController.this.error(String.format("UserInfoDoneCallback got failure: %s", s));
            }
        }

        @Override
        public void getStreamInfoCallback(ErrorCode errorCode, StreamInfo streamInfo) {
            if (ErrorCode.succeeded(errorCode)) {
                BroadcastController.this.streamInfo = streamInfo;

                try {
                    if (BroadcastController.this.listener != null) {
                        BroadcastController.this.listener.getStreamInfoCallback(streamInfo);
                    }
                } catch (Exception exception) {
                    BroadcastController.this.error(exception.toString());
                }
            } else {
                String s = ErrorCode.getString(errorCode);
                BroadcastController.this.warn(String.format("StreamInfoDoneCallback got failure: %s", s));
            }
        }

        @Override
        public void getArchivingStateCallback(ErrorCode errorCode, ArchivingState archivingState) {
            BroadcastController.this.archivingState = archivingState;
            if (ErrorCode.failed(errorCode)) {
            }
        }

        @Override
        public void runCommercialCallback(ErrorCode errorCode) {
            if (ErrorCode.failed(errorCode)) {
                String s = ErrorCode.getString(errorCode);
                BroadcastController.this.warn(String.format("RunCommercialCallback got failure: %s", s));
            }
        }

        @Override
        public void setStreamInfoCallback(ErrorCode errorCode) {
            if (ErrorCode.failed(errorCode)) {
                String s = ErrorCode.getString(errorCode);
                BroadcastController.this.warn(String.format("SetStreamInfoCallback got failure: %s", s));
            }
        }

        @Override
        public void getGameNameListCallback(ErrorCode errorCode, GameInfoList gameInfoList) {
            if (ErrorCode.failed(errorCode)) {
                String s = ErrorCode.getString(errorCode);
                BroadcastController.this.error(String.format("GameNameListCallback got failure: %s", s));
            }

            try {
                if (BroadcastController.this.listener != null) {
                    BroadcastController.this.listener.getGameNameListCallback(errorCode, gameInfoList == null ? new GameInfo[0] : gameInfoList.list);
                }
            } catch (Exception exception) {
                BroadcastController.this.error(exception.toString());
            }
        }

        @Override
        public void bufferUnlockCallback(long l) {
            FrameBuffer framebuffer = FrameBuffer.lookupBuffer(l);
            BroadcastController.this.freeBuffers.add(framebuffer);
        }

        @Override
        public void startCallback(ErrorCode errorCode) {
            if (ErrorCode.succeeded(errorCode)) {
                try {
                    if (BroadcastController.this.listener != null) {
                        BroadcastController.this.listener.broadcastStartCallback();
                    }
                } catch (Exception exception1) {
                    BroadcastController.this.error(exception1.toString());
                }

                BroadcastController.this.setStatus(BroadcastController.State.BROADCASTING);
            } else {
                BroadcastController.this.videoParams = null;
                BroadcastController.this.audioParams = null;
                BroadcastController.this.setStatus(BroadcastController.State.READY_TO_BROADCAST);

                try {
                    if (BroadcastController.this.listener != null) {
                        BroadcastController.this.listener.broadcastStartFailed(errorCode);
                    }
                } catch (Exception exception) {
                    BroadcastController.this.error(exception.toString());
                }

                String s = ErrorCode.getString(errorCode);
                BroadcastController.this.error(String.format("startCallback got failure: %s", s));
            }
        }

        @Override
        public void stopCallback(ErrorCode errorCode) {
            if (ErrorCode.succeeded(errorCode)) {
                BroadcastController.this.videoParams = null;
                BroadcastController.this.audioParams = null;
                BroadcastController.this.m_5595691();

                try {
                    if (BroadcastController.this.listener != null) {
                        BroadcastController.this.listener.broadcastStopCallback();
                    }
                } catch (Exception exception) {
                    BroadcastController.this.error(exception.toString());
                }

                if (BroadcastController.this.loggedIn) {
                    BroadcastController.this.setStatus(BroadcastController.State.READY_TO_BROADCAST);
                } else {
                    BroadcastController.this.setStatus(BroadcastController.State.INITIALIZED);
                }
            } else {
                BroadcastController.this.setStatus(BroadcastController.State.READY_TO_BROADCAST);
                String s = ErrorCode.getString(errorCode);
                BroadcastController.this.error(String.format("stopCallback got failure: %s", s));
            }
        }

        @Override
        public void sendActionMetaDataCallback(ErrorCode errorCode) {
            if (ErrorCode.failed(errorCode)) {
                String s = ErrorCode.getString(errorCode);
                BroadcastController.this.error(String.format("sendActionMetaDataCallback got failure: %s", s));
            }
        }

        @Override
        public void sendStartSpanMetaDataCallback(ErrorCode errorCode) {
            if (ErrorCode.failed(errorCode)) {
                String s = ErrorCode.getString(errorCode);
                BroadcastController.this.error(String.format("sendStartSpanMetaDataCallback got failure: %s", s));
            }
        }

        @Override
        public void sendEndSpanMetaDataCallback(ErrorCode errorCode) {
            if (ErrorCode.failed(errorCode)) {
                String s = ErrorCode.getString(errorCode);
                BroadcastController.this.error(String.format("sendEndSpanMetaDataCallback got failure: %s", s));
            }
        }
    };
    protected IStatCallbacks f_6662802 = new IStatCallbacks() {
        @Override
        public void statCallback(StatType statType, long l) {
        }
    };

    public void setListener(BroadcastController.Listener listener) {
        this.listener = listener;
    }

    public boolean isAuthenticated() {
        return this.authenticated;
    }

    public void m_2881112(String string) {
        this.f_5892149 = string;
    }

    public StreamInfo getStreamInfo() {
        return this.streamInfo;
    }

    public ChannelInfo getChannelInfo() {
        return this.channelInfo;
    }

    public boolean isBroadcasting() {
        return this.status == BroadcastController.State.BROADCASTING || this.status == BroadcastController.State.PAUSED;
    }

    public boolean isReadyToBroadcast() {
        return this.status == BroadcastController.State.READY_TO_BROADCAST;
    }

    public boolean isIngestTesting() {
        return this.status == BroadcastController.State.INGEST_TESTING;
    }

    public boolean isPaused() {
        return this.status == BroadcastController.State.PAUSED;
    }

    public boolean isLoggedIn() {
        return this.loggedIn;
    }

    public IngestServer getDefaultServer() {
        return this.defaultServer;
    }

    public void setDefaultServer(IngestServer server) {
        this.defaultServer = server;
    }

    public IngestList getServers() {
        return this.servers;
    }

    public void setRecorderVolume(float volume) {
        this.stream.setVolume(AudioDeviceType.TTV_RECORDER_DEVICE, volume);
    }

    public void setPlaybackVolume(float volume) {
        this.stream.setVolume(AudioDeviceType.TTV_PLAYBACK_DEVICE, volume);
    }

    public IngestTester getIngestTester() {
        return this.ingestTester;
    }

    public long getStreamTime() {
        return this.stream.getStreamTime();
    }

    protected boolean m_5814109() {
        return true;
    }

    public ErrorCode getInitializationError() {
        return this.initializationError;
    }

    public BroadcastController() {
        this.core = Core.getInstance();
        if (Core.getInstance() == null) {
            this.core = new Core(new StandardCoreAPI());
        }

        this.stream = new Stream(new DesktopStreamAPI());
    }

    protected PixelFormat getPixelFormat() {
        return PixelFormat.TTV_PF_RGBA;
    }

    public boolean m_1098794() {
        if (this.authenticated) {
            return false;
        } else {
            this.stream.setStreamCallbacks(this.f_8506417);
            ErrorCode errorcode = this.core.initialize(this.f_5892149, System.getProperty("java.library.path"));
            if (!this.error(errorcode)) {
                this.stream.setStreamCallbacks(null);
                this.initializationError = errorcode;
                return false;
            } else {
                errorcode = this.core.setTraceLevel(MessageLevel.TTV_ML_ERROR);
                if (!this.error(errorcode)) {
                    this.stream.setStreamCallbacks(null);
                    this.core.shutdown();
                    this.initializationError = errorcode;
                    return false;
                } else if (ErrorCode.succeeded(errorcode)) {
                    this.authenticated = true;
                    this.setStatus(BroadcastController.State.INITIALIZED);
                    return true;
                } else {
                    this.initializationError = errorcode;
                    this.core.shutdown();
                    return false;
                }
            }
        }
    }

    public boolean m_2775427() {
        if (!this.authenticated) {
            return true;
        }

        if (this.isIngestTesting()) {
            return false;
        }

        this.f_1883860 = true;
        this.init();
        this.stream.setStreamCallbacks(null);
        this.stream.setStatCallbacks(null);
        ErrorCode errorcode = this.core.shutdown();
        this.error(errorcode);
        this.authenticated = false;
        this.f_1883860 = false;
        this.setStatus(BroadcastController.State.UNINITIALIZED);
        return true;
    }

    public void shutdown() {
        if (this.status != BroadcastController.State.UNINITIALIZED) {
            if (this.ingestTester != null) {
                this.ingestTester.m_3952819();
            }

            for (; this.ingestTester != null; this.update()) {
                try {
                    Thread.sleep(200L);
                } catch (Exception exception) {
                    this.error(exception.toString());
                }
            }

            this.m_2775427();
        }
    }

    public boolean authenticate(String username, AuthToken token) {
        if (this.isIngestTesting()) {
            return false;
        }

        this.init();
        if (username == null || username.isEmpty()) {
            this.error("Username must be valid");
            return false;
        }

        if (token != null && token.data != null && !token.data.isEmpty()) {
            this.username = username;
            this.token = token;
            if (this.isAuthenticated()) {
                this.setStatus(BroadcastController.State.AUTHENTICATED);
            }

            return true;
        } else {
            this.error("Auth token must be valid");
            return false;
        }
    }

    public boolean init() {
        if (this.isIngestTesting()) {
            return false;
        }

        if (this.isBroadcasting()) {
            this.stream.stop(false);
        }

        this.username = "";
        this.token = new AuthToken();
        if (!this.loggedIn) {
            return false;
        }

        this.loggedIn = false;
        if (!this.f_1883860) {
            try {
                if (this.listener != null) {
                    this.listener.loggedOut();
                }
            } catch (Exception exception) {
                this.error(exception.toString());
            }
        }

        this.setStatus(BroadcastController.State.INITIALIZED);
        return true;
    }

    public boolean setStreamInfo(String string, String string2, String string3) {
        if (!this.loggedIn) {
            return false;
        }

        if (string == null || string.equals("")) {
            string = this.username;
        }

        if (string2 == null) {
            string2 = "";
        }

        if (string3 == null) {
            string3 = "";
        }

        StreamInfoForSetting streaminfoforsetting = new StreamInfoForSetting();
        streaminfoforsetting.streamTitle = string3;
        streaminfoforsetting.gameName = string2;
        ErrorCode errorcode = this.stream.setStreamInfo(this.token, string, streaminfoforsetting);
        this.error(errorcode);
        return ErrorCode.succeeded(errorcode);
    }

    public boolean runCommercial() {
        if (!this.isBroadcasting()) {
            return false;
        }

        ErrorCode errorcode = this.stream.runCommercial(this.token);
        this.error(errorcode);
        return ErrorCode.succeeded(errorcode);
    }

    public VideoParams getVideoParams(int maxKbps, int targetFps, float bytesPerPixel, float aspectRatio) {
        int[] aint = this.stream.getMaxResolution(maxKbps, targetFps, bytesPerPixel, aspectRatio);
        VideoParams videoparams = new VideoParams();
        videoparams.maxKbps = maxKbps;
        videoparams.encodingCpuUsage = EncodingCpuUsage.TTV_ECU_HIGH;
        videoparams.pixelFormat = this.getPixelFormat();
        videoparams.targetFps = targetFps;
        videoparams.outputWidth = aint[0];
        videoparams.outputHeight = aint[1];
        videoparams.disableAdaptiveBitrate = false;
        videoparams.verticalFlip = false;
        return videoparams;
    }

    public boolean start(VideoParams videoParams) {
        if (videoParams != null && this.isReadyToBroadcast()) {
            this.videoParams = videoParams.clone();
            this.audioParams = new AudioParams();
            this.audioParams.audioEnabled = this.f_0473721 && this.m_5814109();
            this.audioParams.enableMicCapture = this.audioParams.audioEnabled;
            this.audioParams.enablePlaybackCapture = this.audioParams.audioEnabled;
            this.audioParams.enablePassthroughAudio = false;
            if (!this.allocateFrameBuffer()) {
                this.videoParams = null;
                this.audioParams = null;
                return false;
            } else {
                ErrorCode errorcode = this.stream.start(videoParams, this.audioParams, this.defaultServer, StartFlags.None, true);
                if (ErrorCode.failed(errorcode)) {
                    this.m_5595691();
                    String s = ErrorCode.getString(errorcode);
                    this.error(String.format("Error while starting to broadcast: %s", s));
                    this.videoParams = null;
                    this.audioParams = null;
                    return false;
                } else {
                    this.setStatus(BroadcastController.State.STARTING);
                    return true;
                }
            }
        } else {
            return false;
        }
    }

    public boolean stop() {
        if (!this.isBroadcasting()) {
            return false;
        } else {
            ErrorCode errorcode = this.stream.stop(true);
            if (ErrorCode.failed(errorcode)) {
                String s = ErrorCode.getString(errorcode);
                this.error(String.format("Error while stopping the broadcast: %s", s));
                return false;
            } else {
                this.setStatus(BroadcastController.State.STOPPING);
                return ErrorCode.succeeded(errorcode);
            }
        }
    }

    public boolean pause() {
        if (!this.isBroadcasting()) {
            return false;
        }

        ErrorCode errorcode = this.stream.pauseVideo();
        if (ErrorCode.failed(errorcode)) {
            this.stop();
            String s = ErrorCode.getString(errorcode);
            this.error(String.format("Error pausing stream: %s\n", s));
        } else {
            this.setStatus(BroadcastController.State.PAUSED);
        }

        return ErrorCode.succeeded(errorcode);
    }

    public boolean resume() {
        if (!this.isPaused()) {
            return false;
        }

        this.setStatus(BroadcastController.State.BROADCASTING);
        return true;
    }

    public boolean sendActionMetadata(String name, long time, String description, String metadata) {
        ErrorCode errorcode = this.stream.sendActionMetaData(this.token, name, time, description, metadata);
        if (ErrorCode.failed(errorcode)) {
            String s = ErrorCode.getString(errorcode);
            this.error(String.format("Error while sending meta data: %s\n", s));
            return false;
        } else {
            return true;
        }
    }

    public long sendStartSpanMetadata(String name, long time, String description, String metadata) {
        long i = this.stream.sendStartSpanMetaData(this.token, name, time, description, metadata);
        if (i == -1L) {
            this.error(String.format("Error in SendStartSpanMetaData\n"));
        }

        return i;
    }

    public boolean sendEndSpanMetadata(String name, long time, long id, String description, String metadata) {
        if (id == -1L) {
            this.error(String.format("Invalid sequence id: %d\n", id));
            return false;
        } else {
            ErrorCode errorcode = this.stream.sendEndSpanMetaData(this.token, name, time, id, description, metadata);
            if (ErrorCode.failed(errorcode)) {
                String s = ErrorCode.getString(errorcode);
                this.error(String.format("Error in SendStopSpanMetaData: %s\n", s));
                return false;
            } else {
                return true;
            }
        }
    }

    protected void setStatus(BroadcastController.State status) {
        if (status != this.status) {
            this.status = status;

            try {
                if (this.listener != null) {
                    this.listener.broadcastStateChanged(status);
                }
            } catch (Exception exception) {
                this.error(exception.toString());
            }
        }
    }

    public void update() {
        if (this.stream != null && this.authenticated) {
            ErrorCode errorcode = this.stream.pollTasks();
            this.error(errorcode);
            if (this.isIngestTesting()) {
                this.ingestTester.update();
                if (this.ingestTester.m_3456887()) {
                    this.ingestTester = null;
                    this.setStatus(BroadcastController.State.READY_TO_BROADCAST);
                }
            }

            switch (this.status) {
                case AUTHENTICATED:
                    this.setStatus(BroadcastController.State.LOGGING_IN);
                    errorcode = this.stream.login(this.token);
                    if (ErrorCode.failed(errorcode)) {
                        String s3 = ErrorCode.getString(errorcode);
                        this.error(String.format("Error in TTV_Login: %s\n", s3));
                    }
                    break;
                case LOGGED_IN:
                    this.setStatus(BroadcastController.State.FINDING_INGEST_SERVER);
                    errorcode = this.stream.getIngestServers(this.token);
                    if (ErrorCode.failed(errorcode)) {
                        this.setStatus(BroadcastController.State.LOGGED_IN);
                        String s2 = ErrorCode.getString(errorcode);
                        this.error(String.format("Error in TTV_GetIngestServers: %s\n", s2));
                    }
                    break;
                case RECEIVED_INGEST_SERVERS:
                    this.setStatus(BroadcastController.State.READY_TO_BROADCAST);
                    errorcode = this.stream.getUserInfo(this.token);
                    if (ErrorCode.failed(errorcode)) {
                        String s = ErrorCode.getString(errorcode);
                        this.error(String.format("Error in TTV_GetUserInfo: %s\n", s));
                    }

                    this.fetchStreamInfo();
                    errorcode = this.stream.getArchivingState(this.token);
                    if (ErrorCode.failed(errorcode)) {
                        String s1 = ErrorCode.getString(errorcode);
                        this.error(String.format("Error in TTV_GetArchivingState: %s\n", s1));
                    }
                case STARTING:
                case STOPPING:
                case FINDING_INGEST_SERVER:
                case AUTHENTICATING:
                case INITIALIZED:
                case UNINITIALIZED:
                case INGEST_TESTING:
                default:
                    break;
                case PAUSED:
                case BROADCASTING:
                    this.fetchStreamInfo();
            }
        }
    }

    protected void fetchStreamInfo() {
        long i = System.nanoTime();
        long j = (i - this.f_5305953) / 1000000000L;
        if (j >= 30L) {
            this.f_5305953 = i;
            ErrorCode errorcode = this.stream.getStreamInfo(this.token, this.username);
            if (ErrorCode.failed(errorcode)) {
                String s = ErrorCode.getString(errorcode);
                this.error(String.format("Error in TTV_GetStreamInfo: %s", s));
            }
        }
    }

    public IngestTester startIngestTesting() {
        if (!this.isReadyToBroadcast() || this.servers == null) {
            return null;
        }

        if (this.isIngestTesting()) {
            return null;
        }

        this.ingestTester = new IngestTester(this.stream, this.servers);
        this.ingestTester.init();
        this.setStatus(BroadcastController.State.INGEST_TESTING);
        return this.ingestTester;
    }

    protected boolean allocateFrameBuffer() {
        for (int i = 0; i < 3; i++) {
            FrameBuffer framebuffer = this.stream.allocateFrameBuffer(this.videoParams.outputWidth * this.videoParams.outputHeight * 4);
            if (!framebuffer.getIsValid()) {
                this.error(String.format("Error while allocating frame buffer"));
                return false;
            }

            this.captureBuffers.add(framebuffer);
            this.freeBuffers.add(framebuffer);
        }

        return true;
    }

    protected void m_5595691() {
        for (int i = 0; i < this.captureBuffers.size(); i++) {
            FrameBuffer framebuffer = this.captureBuffers.get(i);
            framebuffer.free();
        }

        this.freeBuffers.clear();
        this.captureBuffers.clear();
    }

    public FrameBuffer pollFreeBuffer() {
        if (this.freeBuffers.size() == 0) {
            this.error(String.format("Out of free buffers, this should never happen"));
            return null;
        } else {
            FrameBuffer framebuffer = this.freeBuffers.get(this.freeBuffers.size() - 1);
            this.freeBuffers.remove(this.freeBuffers.size() - 1);
            return framebuffer;
        }
    }

    public void readPixels(FrameBuffer buffer) {
        try {
            this.stream.captureFrameBuffer_ReadPixels(buffer);
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.of(throwable, "Trying to submit a frame to Twitch");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Broadcast State");
            crashreportcategory.add("Last reported errors", Arrays.toString(ERRORS.array()));
            crashreportcategory.add("Buffer", buffer);
            crashreportcategory.add("Free buffer count", this.freeBuffers.size());
            crashreportcategory.add("Capture buffer count", this.captureBuffers.size());
            throw new CrashException(crashreport);
        }
    }

    public ErrorCode submitTexturePointer(FrameBuffer ptr) {
        if (this.isPaused()) {
            this.resume();
        } else if (!this.isBroadcasting()) {
            return ErrorCode.TTV_EC_STREAM_NOT_STARTED;
        }

        ErrorCode errorcode = this.stream.submitVideoFrame(ptr);
        if (errorcode != ErrorCode.TTV_EC_SUCCESS) {
            String s = ErrorCode.getString(errorcode);
            if (ErrorCode.succeeded(errorcode)) {
                this.warn(String.format("Warning in SubmitTexturePointer: %s\n", s));
            } else {
                this.error(String.format("Error in SubmitTexturePointer: %s\n", s));
                this.stop();
            }

            if (this.listener != null) {
                this.listener.errorSubmittingFrame(errorcode);
            }
        }

        return errorcode;
    }

    protected boolean error(ErrorCode error) {
        if (ErrorCode.failed(error)) {
            this.error(ErrorCode.getString(error));
            return false;
        } else {
            return true;
        }
    }

    protected void error(String message) {
        this.f_2037199 = message;
        ERRORS.add("<Error> " + message);
        LOGGER.error(Twitch.MARKER, "[Broadcast controller] {}", message);
    }

    protected void warn(String message) {
        ERRORS.add("<Warning> " + message);
        LOGGER.warn(Twitch.MARKER, "[Broadcast controller] {}", message);
    }

    public interface Listener {
        void requestAuthTokenCallback(ErrorCode errorCode, AuthToken authToken);

        void loginCallback(ErrorCode errorCode);

        void getGameNameListCallback(ErrorCode errorCode, GameInfo[] gameInfos);

        void broadcastStateChanged(BroadcastController.State state);

        void loggedOut();

        void getStreamInfoCallback(StreamInfo streamInfo);

        void getIngestServersCallback(IngestList ingestServers);

        void errorSubmittingFrame(ErrorCode errorCode);

        void broadcastStartCallback();

        void broadcastStopCallback();

        void broadcastStartFailed(ErrorCode errorCode);
    }

    public enum State {
        UNINITIALIZED,
        INITIALIZED,
        AUTHENTICATING,
        AUTHENTICATED,
        LOGGING_IN,
        LOGGED_IN,
        FINDING_INGEST_SERVER,
        RECEIVED_INGEST_SERVERS,
        READY_TO_BROADCAST,
        STARTING,
        BROADCASTING,
        STOPPING,
        PAUSED,
        INGEST_TESTING;
    }
}
