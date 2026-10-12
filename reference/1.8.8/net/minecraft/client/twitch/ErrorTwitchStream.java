package net.minecraft.client.twitch;

import tv.twitch.ErrorCode;
import tv.twitch.broadcast.IngestServer;
import tv.twitch.chat.ChatUserInfo;

public class ErrorTwitchStream implements TwitchStream {
    private final Throwable error;

    public ErrorTwitchStream(Throwable error) {
        this.error = error;
    }

    @Override
    public void shutdown() {
    }

    @Override
    public void update() {
    }

    @Override
    public void submit() {
    }

    @Override
    public boolean canBroadcast() {
        return false;
    }

    @Override
    public boolean isReadyToBroadcast() {
        return false;
    }

    @Override
    public boolean isBroadcasting() {
        return false;
    }

    @Override
    public void sendActionMetadata(StreamMetadata metadata, long time) {
    }

    @Override
    public void sendSpanMetadata(StreamMetadata metadata, long start, long end) {
    }

    @Override
    public boolean isPaused() {
        return false;
    }

    @Override
    public void runCommercial() {
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void updateVolume() {
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public IngestServer[] getServers() {
        return new IngestServer[0];
    }

    @Override
    public void startIngestTesting() {
    }

    @Override
    public IngestTester getIngestTester() {
        return null;
    }

    @Override
    public boolean isIngestTesting() {
        return false;
    }

    @Override
    public int getViewerCount() {
        return 0;
    }

    @Override
    public boolean m_5700177() {
        return false;
    }

    @Override
    public String getChannel() {
        return null;
    }

    @Override
    public ChatUserInfo m_8560465(String string) {
        return null;
    }

    @Override
    public void m_7694443(String string) {
    }

    @Override
    public boolean m_2070231() {
        return false;
    }

    @Override
    public ErrorCode m_3195041() {
        return null;
    }

    @Override
    public boolean isLoggedIn() {
        return false;
    }

    @Override
    public void m_9441213(boolean bl) {
    }

    @Override
    public boolean m_1283038() {
        return false;
    }

    @Override
    public TwitchStream.ErrorReason getErrorReason() {
        return TwitchStream.ErrorReason.ERROR;
    }

    public Throwable getError() {
        return this.error;
    }
}
