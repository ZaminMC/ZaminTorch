package net.minecraft.client.twitch;

import com.google.common.collect.Lists;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tv.twitch.AuthToken;
import tv.twitch.Core;
import tv.twitch.ErrorCode;
import tv.twitch.StandardCoreAPI;
import tv.twitch.chat.Chat;
import tv.twitch.chat.ChatBadgeData;
import tv.twitch.chat.ChatChannelInfo;
import tv.twitch.chat.ChatEmoticonData;
import tv.twitch.chat.ChatEvent;
import tv.twitch.chat.ChatRawMessage;
import tv.twitch.chat.ChatTokenizationOption;
import tv.twitch.chat.ChatTokenizedMessage;
import tv.twitch.chat.ChatUserInfo;
import tv.twitch.chat.IChatAPIListener;
import tv.twitch.chat.IChatChannelListener;
import tv.twitch.chat.StandardChatAPI;

public class TwitchChat {
    private static final Logger LOGGER = LogManager.getLogger();
    protected TwitchChat.Listener listener = null;
    protected String userName = "";
    protected String f_1978774 = "";
    protected String f_6107685 = "";
    protected Core core = null;
    protected Chat chat = null;
    protected TwitchChat.State state = TwitchChat.State.UNINITIALIZED;
    protected AuthToken authToken = new AuthToken();
    protected HashMap<String, TwitchChat.ChannelListener> channelListeners = new HashMap<>();
    protected int f_9201714 = 128;
    protected TwitchChat.Tokenization f_2510962 = TwitchChat.Tokenization.NONE;
    protected TwitchChat.Tokenization tokenization = TwitchChat.Tokenization.NONE;
    protected ChatEmoticonData emoticonData = null;
    protected int f_2954995 = 500;
    protected int f_6131852 = 2000;
    protected IChatAPIListener apiListener = new IChatAPIListener() {
        @Override
        public void chatInitializationCallback(ErrorCode errorCode) {
            if (ErrorCode.succeeded(errorCode)) {
                TwitchChat.this.chat.setMessageFlushInterval(TwitchChat.this.f_2954995);
                TwitchChat.this.chat.setUserChangeEventInterval(TwitchChat.this.f_6131852);
                TwitchChat.this.downloadEmoticonData();
                TwitchChat.this.setState(TwitchChat.State.INITIALIZED);
            } else {
                TwitchChat.this.setState(TwitchChat.State.UNINITIALIZED);
            }

            try {
                if (TwitchChat.this.listener != null) {
                    TwitchChat.this.listener.chatInitializationCallback(errorCode);
                }
            } catch (Exception exception) {
                TwitchChat.this.error(exception.toString());
            }
        }

        @Override
        public void chatShutdownCallback(ErrorCode errorCode) {
            if (ErrorCode.succeeded(errorCode)) {
                ErrorCode errorcode = TwitchChat.this.core.shutdown();
                if (ErrorCode.failed(errorcode)) {
                    String s = ErrorCode.getString(errorcode);
                    TwitchChat.this.error(String.format("Error shutting down the Twitch sdk: %s", s));
                }

                TwitchChat.this.setState(TwitchChat.State.UNINITIALIZED);
            } else {
                TwitchChat.this.setState(TwitchChat.State.INITIALIZED);
                TwitchChat.this.error(String.format("Error shutting down Twith chat: %s", errorCode));
            }

            try {
                if (TwitchChat.this.listener != null) {
                    TwitchChat.this.listener.chatShutdownCallback(errorCode);
                }
            } catch (Exception exception) {
                TwitchChat.this.error(exception.toString());
            }
        }

        @Override
        public void chatEmoticonDataDownloadCallback(ErrorCode errorCode) {
            if (ErrorCode.succeeded(errorCode)) {
                TwitchChat.this.prepareEmoticonData();
            }
        }
    };

    public void setListener(TwitchChat.Listener listener) {
        this.listener = listener;
    }

    public void setAuthToken(AuthToken authToken) {
        this.authToken = authToken;
    }

    public void m_5028497(String string) {
        this.f_1978774 = string;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public TwitchChat.State getState() {
        return this.state;
    }

    public boolean isChannelConnected(String channel) {
        if (!this.channelListeners.containsKey(channel)) {
            return false;
        }

        TwitchChat.ChannelListener twitchchat$channellistener = this.channelListeners.get(channel);
        return twitchchat$channellistener.getState() == TwitchChat.ChannelState.CONNECTED;
    }

    public TwitchChat.ChannelState getChannelState(String channel) {
        if (!this.channelListeners.containsKey(channel)) {
            return TwitchChat.ChannelState.DISCONNECTED;
        }

        TwitchChat.ChannelListener twitchchat$channellistener = this.channelListeners.get(channel);
        return twitchchat$channellistener.getState();
    }

    public TwitchChat() {
        this.core = Core.getInstance();
        if (this.core == null) {
            this.core = new Core(new StandardCoreAPI());
        }

        this.chat = new Chat(new StandardChatAPI());
    }

    public boolean m_0094708() {
        if (this.state != TwitchChat.State.UNINITIALIZED) {
            return false;
        }

        this.setState(TwitchChat.State.INITIALIZING);
        ErrorCode errorcode = this.core.initialize(this.f_1978774, null);
        if (ErrorCode.failed(errorcode)) {
            this.setState(TwitchChat.State.UNINITIALIZED);
            String s1 = ErrorCode.getString(errorcode);
            this.error(String.format("Error initializing Twitch sdk: %s", s1));
            return false;
        }

        this.tokenization = this.f_2510962;
        HashSet<ChatTokenizationOption> hashset = new HashSet<>();
        switch (this.f_2510962) {
            case NONE:
                hashset.add(ChatTokenizationOption.TTV_CHAT_TOKENIZATION_OPTION_NONE);
                break;
            case URL:
                hashset.add(ChatTokenizationOption.TTV_CHAT_TOKENIZATION_OPTION_EMOTICON_URLS);
                break;
            case TEXTURE_ATLAS:
                hashset.add(ChatTokenizationOption.TTV_CHAT_TOKENIZATION_OPTION_EMOTICON_TEXTURES);
        }

        errorcode = this.chat.initialize(hashset, this.apiListener);
        if (ErrorCode.failed(errorcode)) {
            this.core.shutdown();
            this.setState(TwitchChat.State.UNINITIALIZED);
            String s = ErrorCode.getString(errorcode);
            this.error(String.format("Error initializing Twitch chat: %s", s));
            return false;
        } else {
            this.setState(TwitchChat.State.INITIALIZED);
            return true;
        }
    }

    public boolean connectChannel(String channel) {
        return this.connectChannel(channel, false);
    }

    protected boolean connectChannel(String channel, boolean bl) {
        if (this.state != TwitchChat.State.INITIALIZED) {
            return false;
        }

        if (this.channelListeners.containsKey(channel)) {
            this.error("Already in channel: " + channel);
            return false;
        }

        if (channel != null && !channel.equals("")) {
            TwitchChat.ChannelListener twitchchat$channellistener = new TwitchChat.ChannelListener(channel);
            this.channelListeners.put(channel, twitchchat$channellistener);
            boolean flag = twitchchat$channellistener.connect(bl);
            if (!flag) {
                this.channelListeners.remove(channel);
            }

            return flag;
        } else {
            return false;
        }
    }

    public boolean disconnectChannel(String channel) {
        if (this.state != TwitchChat.State.INITIALIZED) {
            return false;
        } else if (!this.channelListeners.containsKey(channel)) {
            this.error("Not in channel: " + channel);
            return false;
        } else {
            TwitchChat.ChannelListener twitchchat$channellistener = this.channelListeners.get(channel);
            return twitchchat$channellistener.disconnect();
        }
    }

    public boolean m_7988517() {
        if (this.state != TwitchChat.State.INITIALIZED) {
            return false;
        } else {
            ErrorCode errorcode = this.chat.shutdown();
            if (ErrorCode.failed(errorcode)) {
                String s = ErrorCode.getString(errorcode);
                this.error(String.format("Error shutting down chat: %s", s));
                return false;
            } else {
                this.clearEmoticonData();
                this.setState(TwitchChat.State.SHUTTING_DOWN);
                return true;
            }
        }
    }

    public void shutdown() {
        if (this.getState() != TwitchChat.State.UNINITIALIZED) {
            this.m_7988517();
            if (this.getState() == TwitchChat.State.SHUTTING_DOWN) {
                while (this.getState() != TwitchChat.State.UNINITIALIZED) {
                    try {
                        Thread.sleep(200L);
                        this.update();
                    } catch (InterruptedException interruptedexception) {
                    }
                }
            }
        }
    }

    public void update() {
        if (this.state != TwitchChat.State.UNINITIALIZED) {
            ErrorCode errorcode = this.chat.flushEvents();
            if (ErrorCode.failed(errorcode)) {
                String s = ErrorCode.getString(errorcode);
                this.error(String.format("Error flushing chat events: %s", s));
            }
        }
    }

    public boolean m_3680703(String string, String string2) {
        if (this.state != TwitchChat.State.INITIALIZED) {
            return false;
        } else if (!this.channelListeners.containsKey(string)) {
            this.error("Not in channel: " + string);
            return false;
        } else {
            TwitchChat.ChannelListener twitchchat$channellistener = this.channelListeners.get(string);
            return twitchchat$channellistener.sendMessage(string2);
        }
    }

    protected void setState(TwitchChat.State state) {
        if (state != this.state) {
            this.state = state;

            try {
                if (this.listener != null) {
                    this.listener.chatStateChanged(state);
                }
            } catch (Exception exception) {
                this.error(exception.toString());
            }
        }
    }

    protected void downloadEmoticonData() {
        if (this.tokenization != TwitchChat.Tokenization.NONE) {
            if (this.emoticonData == null) {
                ErrorCode errorcode = this.chat.downloadEmoticonData();
                if (ErrorCode.failed(errorcode)) {
                    String s = ErrorCode.getString(errorcode);
                    this.error(String.format("Error trying to download emoticon data: %s", s));
                }
            }
        }
    }

    protected void prepareEmoticonData() {
        if (this.emoticonData == null) {
            this.emoticonData = new ChatEmoticonData();
            ErrorCode errorcode = this.chat.getEmoticonData(this.emoticonData);
            if (ErrorCode.succeeded(errorcode)) {
                try {
                    if (this.listener != null) {
                        this.listener.prepareChatEmoticonDataCallback();
                    }
                } catch (Exception exception) {
                    this.error(exception.toString());
                }
            } else {
                this.error("Error preparing emoticon data: " + ErrorCode.getString(errorcode));
            }
        }
    }

    protected void clearEmoticonData() {
        if (this.emoticonData != null) {
            ErrorCode errorcode = this.chat.clearEmoticonData();
            if (ErrorCode.succeeded(errorcode)) {
                this.emoticonData = null;

                try {
                    if (this.listener != null) {
                        this.listener.m_2003643();
                    }
                } catch (Exception exception) {
                    this.error(exception.toString());
                }
            } else {
                this.error("Error clearing emoticon data: " + ErrorCode.getString(errorcode));
            }
        }
    }

    protected void error(String error) {
        LOGGER.error(Twitch.MARKER, "[Chat controller] {}", error);
    }

    public class ChannelListener implements IChatChannelListener {
        protected String channel = null;
        protected boolean f_8322107 = false;
        protected TwitchChat.ChannelState channelState = TwitchChat.ChannelState.CREATED;
        protected List<ChatUserInfo> f_6950962 = Lists.newArrayList();
        protected LinkedList<ChatRawMessage> rawMessages = new LinkedList<>();
        protected LinkedList<ChatTokenizedMessage> tokenizedMessages = new LinkedList<>();
        protected ChatBadgeData badgeData = null;

        public ChannelListener(String channel) {
            this.channel = channel;
        }

        public TwitchChat.ChannelState getState() {
            return this.channelState;
        }

        public boolean connect(boolean bl) {
            this.f_8322107 = bl;
            ErrorCode errorcode = ErrorCode.TTV_EC_SUCCESS;
            if (bl) {
                errorcode = TwitchChat.this.chat.connectAnonymous(this.channel, this);
            } else {
                errorcode = TwitchChat.this.chat.connect(this.channel, TwitchChat.this.userName, TwitchChat.this.authToken.data, this);
            }

            if (ErrorCode.failed(errorcode)) {
                String s = ErrorCode.getString(errorcode);
                TwitchChat.this.error(String.format("Error connecting: %s", s));
                this.leftChannelCallback(this.channel);
                return false;
            } else {
                this.setChannelState(TwitchChat.ChannelState.CONNECTING);
                this.downloadBadgeData();
                return true;
            }
        }

        public boolean disconnect() {
            switch (this.channelState) {
                case CONNECTED:
                case CONNECTING:
                    ErrorCode errorcode = TwitchChat.this.chat.disconnect(this.channel);
                    if (ErrorCode.failed(errorcode)) {
                        String s = ErrorCode.getString(errorcode);
                        TwitchChat.this.error(String.format("Error disconnecting: %s", s));
                        return false;
                    }

                    this.setChannelState(TwitchChat.ChannelState.DISCONNECTING);
                    return true;
                case CREATED:
                case DISCONNECTED:
                case DISCONNECTING:
                default:
                    return false;
            }
        }

        protected void setChannelState(TwitchChat.ChannelState state) {
            if (state != this.channelState) {
                this.channelState = state;
            }
        }

        public void clearChat(String userName) {
            if (TwitchChat.this.tokenization == TwitchChat.Tokenization.NONE) {
                this.rawMessages.clear();
                this.tokenizedMessages.clear();
            } else {
                if (this.rawMessages.size() > 0) {
                    ListIterator<ChatRawMessage> listiterator = this.rawMessages.listIterator();

                    while (listiterator.hasNext()) {
                        ChatRawMessage chatrawmessage = listiterator.next();
                        if (chatrawmessage.userName.equals(userName)) {
                            listiterator.remove();
                        }
                    }
                }

                if (this.tokenizedMessages.size() > 0) {
                    ListIterator<ChatTokenizedMessage> listiterator1 = this.tokenizedMessages.listIterator();

                    while (listiterator1.hasNext()) {
                        ChatTokenizedMessage chattokenizedmessage = listiterator1.next();
                        if (chattokenizedmessage.displayName.equals(userName)) {
                            listiterator1.remove();
                        }
                    }
                }
            }

            try {
                if (TwitchChat.this.listener != null) {
                    TwitchChat.this.listener.chatCleared(this.channel, userName);
                }
            } catch (Exception exception) {
                TwitchChat.this.error(exception.toString());
            }
        }

        public boolean sendMessage(String message) {
            if (this.channelState != TwitchChat.ChannelState.CONNECTED) {
                return false;
            } else {
                ErrorCode errorcode = TwitchChat.this.chat.sendMessage(this.channel, message);
                if (ErrorCode.failed(errorcode)) {
                    String s = ErrorCode.getString(errorcode);
                    TwitchChat.this.error(String.format("Error sending chat message: %s", s));
                    return false;
                } else {
                    return true;
                }
            }
        }

        protected void downloadBadgeData() {
            if (TwitchChat.this.tokenization != TwitchChat.Tokenization.NONE) {
                if (this.badgeData == null) {
                    ErrorCode errorcode = TwitchChat.this.chat.downloadBadgeData(this.channel);
                    if (ErrorCode.failed(errorcode)) {
                        String s = ErrorCode.getString(errorcode);
                        TwitchChat.this.error(String.format("Error trying to download badge data: %s", s));
                    }
                }
            }
        }

        protected void prepareBadgeData() {
            if (this.badgeData == null) {
                this.badgeData = new ChatBadgeData();
                ErrorCode errorcode = TwitchChat.this.chat.getBadgeData(this.channel, this.badgeData);
                if (ErrorCode.succeeded(errorcode)) {
                    try {
                        if (TwitchChat.this.listener != null) {
                            TwitchChat.this.listener.m_6004964(this.channel);
                        }
                    } catch (Exception exception) {
                        TwitchChat.this.error(exception.toString());
                    }
                } else {
                    TwitchChat.this.error("Error preparing badge data: " + ErrorCode.getString(errorcode));
                }
            }
        }

        protected void clearBadgeData() {
            if (this.badgeData != null) {
                ErrorCode errorcode = TwitchChat.this.chat.clearBadgeData(this.channel);
                if (ErrorCode.succeeded(errorcode)) {
                    this.badgeData = null;

                    try {
                        if (TwitchChat.this.listener != null) {
                            TwitchChat.this.listener.m_2088486(this.channel);
                        }
                    } catch (Exception exception) {
                        TwitchChat.this.error(exception.toString());
                    }
                } else {
                    TwitchChat.this.error("Error releasing badge data: " + ErrorCode.getString(errorcode));
                }
            }
        }

        protected void joinedChannelCallback(String channel) {
            try {
                if (TwitchChat.this.listener != null) {
                    TwitchChat.this.listener.joinedChannel(channel);
                }
            } catch (Exception exception) {
                TwitchChat.this.error(exception.toString());
            }
        }

        protected void leftChannelCallback(String channel) {
            try {
                if (TwitchChat.this.listener != null) {
                    TwitchChat.this.listener.leftChannel(channel);
                }
            } catch (Exception exception) {
                TwitchChat.this.error(exception.toString());
            }
        }

        private void leftChannelCallback() {
            if (this.channelState != TwitchChat.ChannelState.DISCONNECTED) {
                this.setChannelState(TwitchChat.ChannelState.DISCONNECTED);
                this.leftChannelCallback(this.channel);
                this.clearBadgeData();
            }
        }

        @Override
        public void chatStatusCallback(String string, ErrorCode errorCode) {
            if (!ErrorCode.succeeded(errorCode)) {
                TwitchChat.this.channelListeners.remove(string);
                this.leftChannelCallback();
            }
        }

        @Override
        public void chatChannelMembershipCallback(String channel, ChatEvent chatEvent, ChatChannelInfo chatChannelInfo) {
            switch (chatEvent) {
                case TTV_CHAT_JOINED_CHANNEL:
                    this.setChannelState(TwitchChat.ChannelState.CONNECTED);
                    this.joinedChannelCallback(channel);
                    break;
                case TTV_CHAT_LEFT_CHANNEL:
                    this.leftChannelCallback();
            }
        }

        @Override
        public void chatChannelUserChangeCallback(String string, ChatUserInfo[] chatUserInfos, ChatUserInfo[] chatUserInfos2, ChatUserInfo[] chatUserInfos3) {
            for (int i = 0; i < chatUserInfos2.length; i++) {
                int j = this.f_6950962.indexOf(chatUserInfos2[i]);
                if (j >= 0) {
                    this.f_6950962.remove(j);
                }
            }

            for (int k = 0; k < chatUserInfos3.length; k++) {
                int i1 = this.f_6950962.indexOf(chatUserInfos3[k]);
                if (i1 >= 0) {
                    this.f_6950962.remove(i1);
                }

                this.f_6950962.add(chatUserInfos3[k]);
            }

            for (int l = 0; l < chatUserInfos.length; l++) {
                this.f_6950962.add(chatUserInfos[l]);
            }

            try {
                if (TwitchChat.this.listener != null) {
                    TwitchChat.this.listener.chatChannelUserChangeCallback(this.channel, chatUserInfos, chatUserInfos2, chatUserInfos3);
                }
            } catch (Exception exception) {
                TwitchChat.this.error(exception.toString());
            }
        }

        @Override
        public void chatChannelRawMessageCallback(String string, ChatRawMessage[] chatRawMessages) {
            for (int i = 0; i < chatRawMessages.length; i++) {
                this.rawMessages.addLast(chatRawMessages[i]);
            }

            try {
                if (TwitchChat.this.listener != null) {
                    TwitchChat.this.listener.m_1947729(this.channel, chatRawMessages);
                }
            } catch (Exception exception) {
                TwitchChat.this.error(exception.toString());
            }

            while (this.rawMessages.size() > TwitchChat.this.f_9201714) {
                this.rawMessages.removeFirst();
            }
        }

        @Override
        public void chatChannelTokenizedMessageCallback(String string, ChatTokenizedMessage[] chatTokenizedMessages) {
            for (int i = 0; i < chatTokenizedMessages.length; i++) {
                this.tokenizedMessages.addLast(chatTokenizedMessages[i]);
            }

            try {
                if (TwitchChat.this.listener != null) {
                    TwitchChat.this.listener.m_2764991(this.channel, chatTokenizedMessages);
                }
            } catch (Exception exception) {
                TwitchChat.this.error(exception.toString());
            }

            while (this.tokenizedMessages.size() > TwitchChat.this.f_9201714) {
                this.tokenizedMessages.removeFirst();
            }
        }

        @Override
        public void chatClearCallback(String string, String string2) {
            this.clearChat(string2);
        }

        @Override
        public void chatBadgeDataDownloadCallback(String string, ErrorCode errorCode) {
            if (ErrorCode.succeeded(errorCode)) {
                this.prepareBadgeData();
            }
        }
    }

    public enum ChannelState {
        CREATED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED;
    }

    public interface Listener {
        void chatInitializationCallback(ErrorCode errorCode);

        void chatShutdownCallback(ErrorCode errorCode);

        void prepareChatEmoticonDataCallback();

        void m_2003643();

        void chatStateChanged(TwitchChat.State state);

        void m_2764991(String string, ChatTokenizedMessage[] chatTokenizedMessages);

        void m_1947729(String string, ChatRawMessage[] chatRawMessages);

        void chatChannelUserChangeCallback(String string, ChatUserInfo[] chatUserInfos, ChatUserInfo[] chatUserInfos2, ChatUserInfo[] chatUserInfos3);

        void joinedChannel(String string);

        void leftChannel(String string);

        void chatCleared(String string, String string2);

        void m_6004964(String string);

        void m_2088486(String string);
    }

    public enum State {
        UNINITIALIZED,
        INITIALIZING,
        INITIALIZED,
        SHUTTING_DOWN;
    }

    public enum Tokenization {
        NONE,
        URL,
        TEXTURE_ATLAS;
    }
}
