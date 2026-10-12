package net.minecraft.client.twitch;

import tv.twitch.ErrorCode;
import tv.twitch.broadcast.IngestServer;
import tv.twitch.chat.ChatUserInfo;

public interface TwitchStream {
    void shutdown();

    void update();

    void submit();

    boolean canBroadcast();

    boolean isReadyToBroadcast();

    boolean isBroadcasting();

    void sendActionMetadata(StreamMetadata metadata, long time);

    void sendSpanMetadata(StreamMetadata metadata, long start, long end);

    boolean isPaused();

    void runCommercial();

    void pause();

    void resume();

    void updateVolume();

    void start();

    void stop();

    IngestServer[] getServers();

    void startIngestTesting();

    IngestTester getIngestTester();

    boolean isIngestTesting();

    int getViewerCount();

    boolean m_5700177();

    String getChannel();

    ChatUserInfo m_8560465(String string);

    void m_7694443(String string);

    boolean m_2070231();

    ErrorCode m_3195041();

    boolean isLoggedIn();

    void m_9441213(boolean bl);

    boolean m_1283038();

    TwitchStream.ErrorReason getErrorReason();

    enum ErrorReason {
        ERROR,
        INVALID_TOKEN;
    }
}
