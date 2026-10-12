package net.minecraft.client.twitch;

import com.google.common.collect.Lists;
import java.util.List;
import tv.twitch.AuthToken;
import tv.twitch.ErrorCode;
import tv.twitch.broadcast.ArchivingState;
import tv.twitch.broadcast.AudioParams;
import tv.twitch.broadcast.ChannelInfo;
import tv.twitch.broadcast.EncodingCpuUsage;
import tv.twitch.broadcast.FrameBuffer;
import tv.twitch.broadcast.GameInfoList;
import tv.twitch.broadcast.IStatCallbacks;
import tv.twitch.broadcast.IStreamCallbacks;
import tv.twitch.broadcast.IngestList;
import tv.twitch.broadcast.IngestServer;
import tv.twitch.broadcast.PixelFormat;
import tv.twitch.broadcast.RTMPState;
import tv.twitch.broadcast.StartFlags;
import tv.twitch.broadcast.StatType;
import tv.twitch.broadcast.Stream;
import tv.twitch.broadcast.StreamInfo;
import tv.twitch.broadcast.UserInfo;
import tv.twitch.broadcast.VideoParams;

public class IngestTester {
    protected IngestTester.Listener listener = null;
    protected Stream stream = null;
    protected IngestList servers = null;
    protected IngestTester.State state = IngestTester.State.UNINITALIZED;
    protected long f_1253051 = 8000L;
    protected long f_8059168 = 2000L;
    protected long f_3206753 = 0L;
    protected RTMPState rtmpState = RTMPState.Invalid;
    protected VideoParams videoParams = null;
    protected AudioParams audioParams = null;
    protected long f_8968290 = 0L;
    protected List<FrameBuffer> f_3182503 = null;
    protected boolean f_7881613 = false;
    protected IStreamCallbacks f_8136025 = null;
    protected IStatCallbacks f_7527648 = null;
    protected IngestServer currentServer = null;
    protected boolean f_0838846 = false;
    protected boolean f_2355741 = false;
    protected int currentServerIndex = -1;
    protected int f_9883386 = 0;
    protected long f_5132760 = 0L;
    protected float f_6099674 = 0.0F;
    protected float progress = 0.0F;
    protected boolean f_1564509 = false;
    protected boolean f_4001227 = false;
    protected boolean f_8624031 = false;
    protected IStreamCallbacks f_8417941 = new IStreamCallbacks() {
        @Override
        public void requestAuthTokenCallback(ErrorCode errorCode, AuthToken authToken) {
        }

        @Override
        public void loginCallback(ErrorCode errorCode, ChannelInfo channelInfo) {
        }

        @Override
        public void getIngestServersCallback(ErrorCode errorCode, IngestList ingestList) {
        }

        @Override
        public void getUserInfoCallback(ErrorCode errorCode, UserInfo userInfo) {
        }

        @Override
        public void getStreamInfoCallback(ErrorCode errorCode, StreamInfo streamInfo) {
        }

        @Override
        public void getArchivingStateCallback(ErrorCode errorCode, ArchivingState archivingState) {
        }

        @Override
        public void runCommercialCallback(ErrorCode errorCode) {
        }

        @Override
        public void setStreamInfoCallback(ErrorCode errorCode) {
        }

        @Override
        public void getGameNameListCallback(ErrorCode errorCode, GameInfoList gameInfoList) {
        }

        @Override
        public void bufferUnlockCallback(long l) {
        }

        @Override
        public void startCallback(ErrorCode errorCode) {
            IngestTester.this.f_4001227 = false;
            if (ErrorCode.succeeded(errorCode)) {
                IngestTester.this.f_1564509 = true;
                IngestTester.this.f_8968290 = System.currentTimeMillis();
                IngestTester.this.setState(IngestTester.State.CONNECTING_TO_SERVER);
            } else {
                IngestTester.this.f_7881613 = false;
                IngestTester.this.setState(IngestTester.State.DONE_TESTING_SERVER);
            }
        }

        @Override
        public void stopCallback(ErrorCode errorCode) {
            if (ErrorCode.failed(errorCode)) {
                System.out.println("IngestTester.stopCallback failed to stop - " + IngestTester.this.currentServer.serverName + ": " + errorCode.toString());
            }

            IngestTester.this.f_8624031 = false;
            IngestTester.this.f_1564509 = false;
            IngestTester.this.setState(IngestTester.State.DONE_TESTING_SERVER);
            IngestTester.this.currentServer = null;
            if (IngestTester.this.f_0838846) {
                IngestTester.this.setState(IngestTester.State.CANCELLING);
            }
        }

        @Override
        public void sendActionMetaDataCallback(ErrorCode errorCode) {
        }

        @Override
        public void sendStartSpanMetaDataCallback(ErrorCode errorCode) {
        }

        @Override
        public void sendEndSpanMetaDataCallback(ErrorCode errorCode) {
        }
    };
    protected IStatCallbacks statCallbacks = new IStatCallbacks() {
        @Override
        public void statCallback(StatType statType, long l) {
            switch (statType) {
                case TTV_ST_RTMPSTATE:
                    IngestTester.this.rtmpState = RTMPState.lookupValue((int)l);
                    break;
                case TTV_ST_RTMPDATASENT:
                    IngestTester.this.f_3206753 = l;
            }
        }
    };

    public void setListener(IngestTester.Listener listener) {
        this.listener = listener;
    }

    public IngestServer m_4296079() {
        return this.currentServer;
    }

    public int m_5896257() {
        return this.currentServerIndex;
    }

    public boolean m_3456887() {
        return this.state == IngestTester.State.FINISHED || this.state == IngestTester.State.CANCELLED || this.state == IngestTester.State.FAILED;
    }

    public float m_7651052() {
        return this.progress;
    }

    public IngestTester(Stream stream, IngestList ingestList) {
        this.stream = stream;
        this.servers = ingestList;
    }

    public void init() {
        if (this.state == IngestTester.State.UNINITALIZED) {
            this.currentServerIndex = 0;
            this.f_0838846 = false;
            this.f_2355741 = false;
            this.f_1564509 = false;
            this.f_4001227 = false;
            this.f_8624031 = false;
            this.f_7527648 = this.stream.getStatCallbacks();
            this.stream.setStatCallbacks(this.statCallbacks);
            this.f_8136025 = this.stream.getStreamCallbacks();
            this.stream.setStreamCallbacks(this.f_8417941);
            this.videoParams = new VideoParams();
            this.videoParams.targetFps = 60;
            this.videoParams.maxKbps = 3500;
            this.videoParams.outputWidth = 1280;
            this.videoParams.outputHeight = 720;
            this.videoParams.pixelFormat = PixelFormat.TTV_PF_BGRA;
            this.videoParams.encodingCpuUsage = EncodingCpuUsage.TTV_ECU_HIGH;
            this.videoParams.disableAdaptiveBitrate = true;
            this.videoParams.verticalFlip = false;
            this.stream.getDefaultParams(this.videoParams);
            this.audioParams = new AudioParams();
            this.audioParams.audioEnabled = false;
            this.audioParams.enableMicCapture = false;
            this.audioParams.enablePlaybackCapture = false;
            this.audioParams.enablePassthroughAudio = false;
            this.f_3182503 = Lists.newArrayList();
            int i = 3;

            for (int j = 0; j < i; j++) {
                FrameBuffer framebuffer = this.stream.allocateFrameBuffer(this.videoParams.outputWidth * this.videoParams.outputHeight * 4);
                if (!framebuffer.getIsValid()) {
                    this.m_8673432();
                    this.setState(IngestTester.State.FAILED);
                    return;
                }

                this.f_3182503.add(framebuffer);
                this.stream.randomizeFrameBuffer(framebuffer);
            }

            this.setState(IngestTester.State.STARTING);
            this.f_8968290 = System.currentTimeMillis();
        }
    }

    public void update() {
        if (!this.m_3456887() && this.state != IngestTester.State.UNINITALIZED) {
            if (!this.f_4001227 && !this.f_8624031) {
                switch (this.state) {
                    case STARTING:
                    case DONE_TESTING_SERVER:
                        if (this.currentServer != null) {
                            if (this.f_2355741 || !this.f_7881613) {
                                this.currentServer.bitrateKbps = 0.0F;
                            }

                            this.stopTestingServer(this.currentServer);
                        } else {
                            this.f_8968290 = 0L;
                            this.f_2355741 = false;
                            this.f_7881613 = true;
                            if (this.state != IngestTester.State.STARTING) {
                                this.currentServerIndex++;
                            }

                            if (this.currentServerIndex < this.servers.getServers().length) {
                                this.currentServer = this.servers.getServers()[this.currentServerIndex];
                                this.startTestingServer(this.currentServer);
                            } else {
                                this.setState(IngestTester.State.FINISHED);
                            }
                        }
                        break;
                    case CONNECTING_TO_SERVER:
                    case TESTING_SERVER:
                        this.m_1557837(this.currentServer);
                        break;
                    case CANCELLING:
                        this.setState(IngestTester.State.CANCELLED);
                }

                this.m_7789247();
                if (this.state == IngestTester.State.CANCELLED || this.state == IngestTester.State.FINISHED) {
                    this.m_8673432();
                }
            }
        }
    }

    public void m_3952819() {
        if (!this.m_3456887() && !this.f_0838846) {
            this.f_0838846 = true;
            if (this.currentServer != null) {
                this.currentServer.bitrateKbps = 0.0F;
            }
        }
    }

    protected boolean startTestingServer(IngestServer server) {
        this.f_7881613 = true;
        this.f_3206753 = 0L;
        this.rtmpState = RTMPState.Idle;
        this.currentServer = server;
        this.f_4001227 = true;
        this.setState(IngestTester.State.CONNECTING_TO_SERVER);
        ErrorCode errorcode = this.stream.start(this.videoParams, this.audioParams, server, StartFlags.TTV_Start_BandwidthTest, true);
        if (ErrorCode.failed(errorcode)) {
            this.f_4001227 = false;
            this.f_7881613 = false;
            this.setState(IngestTester.State.DONE_TESTING_SERVER);
            return false;
        } else {
            this.f_5132760 = this.f_3206753;
            server.bitrateKbps = 0.0F;
            this.f_9883386 = 0;
            return true;
        }
    }

    protected void stopTestingServer(IngestServer server) {
        if (this.f_4001227) {
            this.f_2355741 = true;
        } else if (this.f_1564509) {
            this.f_8624031 = true;
            ErrorCode errorcode = this.stream.stop(true);
            if (ErrorCode.failed(errorcode)) {
                this.f_8417941.stopCallback(ErrorCode.TTV_EC_SUCCESS);
                System.out.println("Stop failed: " + errorcode.toString());
            }

            this.stream.pollStats();
        } else {
            this.f_8417941.stopCallback(ErrorCode.TTV_EC_SUCCESS);
        }
    }

    protected long m_7654688() {
        return System.currentTimeMillis() - this.f_8968290;
    }

    protected void m_7789247() {
        float f = (float)this.m_7654688();
        switch (this.state) {
            case STARTING:
            case CONNECTING_TO_SERVER:
            case UNINITALIZED:
            case FINISHED:
            case CANCELLED:
            case FAILED:
                this.progress = 0.0F;
                break;
            case DONE_TESTING_SERVER:
                this.progress = 1.0F;
                break;
            case TESTING_SERVER:
            case CANCELLING:
            default:
                this.progress = f / (float)this.f_1253051;
        }

        switch (this.state) {
            case FINISHED:
            case CANCELLED:
            case FAILED:
                this.f_6099674 = 1.0F;
                break;
            default:
                this.f_6099674 = (float)this.currentServerIndex / this.servers.getServers().length;
                this.f_6099674 = this.f_6099674 + this.progress / this.servers.getServers().length;
        }
    }

    protected boolean m_1557837(IngestServer ingestServer) {
        if (this.f_2355741 || this.f_0838846 || this.m_7654688() >= this.f_1253051) {
            this.setState(IngestTester.State.DONE_TESTING_SERVER);
            return true;
        }

        if (!this.f_4001227 && !this.f_8624031) {
            ErrorCode errorcode = this.stream.submitVideoFrame(this.f_3182503.get(this.f_9883386));
            if (ErrorCode.failed(errorcode)) {
                this.f_7881613 = false;
                this.setState(IngestTester.State.DONE_TESTING_SERVER);
                return false;
            }

            this.f_9883386 = (this.f_9883386 + 1) % this.f_3182503.size();
            this.stream.pollStats();
            if (this.rtmpState == RTMPState.SendVideo) {
                this.setState(IngestTester.State.TESTING_SERVER);
                long i = this.m_7654688();
                if (i > 0L && this.f_3206753 > this.f_5132760) {
                    ingestServer.bitrateKbps = (float)(this.f_3206753 * 8L) / (float)this.m_7654688();
                    this.f_5132760 = this.f_3206753;
                }
            }

            return true;
        } else {
            return true;
        }
    }

    protected void m_8673432() {
        this.currentServer = null;
        if (this.f_3182503 != null) {
            for (int i = 0; i < this.f_3182503.size(); i++) {
                this.f_3182503.get(i).free();
            }

            this.f_3182503 = null;
        }

        if (this.stream.getStatCallbacks() == this.statCallbacks) {
            this.stream.setStatCallbacks(this.f_7527648);
            this.f_7527648 = null;
        }

        if (this.stream.getStreamCallbacks() == this.f_8417941) {
            this.stream.setStreamCallbacks(this.f_8136025);
            this.f_8136025 = null;
        }
    }

    protected void setState(IngestTester.State state) {
        if (state != this.state) {
            this.state = state;
            if (this.listener != null) {
                this.listener.ingestStateChanged(this, state);
            }
        }
    }

    public interface Listener {
        void ingestStateChanged(IngestTester ingestTester, IngestTester.State state);
    }

    public enum State {
        UNINITALIZED,
        STARTING,
        CONNECTING_TO_SERVER,
        TESTING_SERVER,
        DONE_TESTING_SERVER,
        FINISHED,
        CANCELLING,
        CANCELLED,
        FAILED;
    }
}
