package net.minecraft.client.gui.screen;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.twitch.TwitchStream;
import net.minecraft.text.Formatting;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import tv.twitch.chat.ChatUserInfo;
import tv.twitch.chat.ChatUserMode;
import tv.twitch.chat.ChatUserSubscription;

public class TwitchUserInfoScreen extends Screen {
    private static final Formatting DARK_GREEN = Formatting.DARK_GREEN;
    private static final Formatting RED = Formatting.RED;
    private static final Formatting DARK_PURPLE = Formatting.DARK_PURPLE;
    private final ChatUserInfo userInfo;
    private final Text userDisplayName;
    private final List<Text> f_7324361 = Lists.newArrayList();
    private final TwitchStream stream;
    private int f_7607655;

    public TwitchUserInfoScreen(TwitchStream stream, ChatUserInfo userInfo) {
        this.stream = stream;
        this.userInfo = userInfo;
        this.userDisplayName = new LiteralText(userInfo.displayName);
        this.f_7324361.addAll(m_0317031(userInfo.modes, userInfo.subscriptions, stream));
    }

    public static List<Text> m_0317031(Set<ChatUserMode> set, Set<ChatUserSubscription> set2, TwitchStream twitchStream) {
        String s = twitchStream == null ? null : twitchStream.getChannel();
        boolean flag = twitchStream != null && twitchStream.m_5700177();
        List<Text> list = Lists.newArrayList();

        for (ChatUserMode chatusermode : set) {
            Text text = m_0118282(chatusermode, s, flag);
            if (text != null) {
                Text text1 = new LiteralText("- ");
                text1.append(text);
                list.add(text1);
            }
        }

        for (ChatUserSubscription chatusersubscription : set2) {
            Text text2 = m_5722668(chatusersubscription, s, flag);
            if (text2 != null) {
                Text text3 = new LiteralText("- ");
                text3.append(text2);
                list.add(text3);
            }
        }

        return list;
    }

    public static Text m_5722668(ChatUserSubscription chatUserSubscription, String string, boolean bl) {
        Text text = null;
        if (chatUserSubscription == ChatUserSubscription.TTV_CHAT_USERSUB_SUBSCRIBER) {
            if (string == null) {
                text = new TranslatableText("stream.user.subscription.subscriber");
            } else if (bl) {
                text = new TranslatableText("stream.user.subscription.subscriber.self");
            } else {
                text = new TranslatableText("stream.user.subscription.subscriber.other", string);
            }

            text.getStyle().setColor(DARK_GREEN);
        } else if (chatUserSubscription == ChatUserSubscription.TTV_CHAT_USERSUB_TURBO) {
            text = new TranslatableText("stream.user.subscription.turbo");
            text.getStyle().setColor(DARK_PURPLE);
        }

        return text;
    }

    public static Text m_0118282(ChatUserMode chatUserMode, String string, boolean bl) {
        Text text = null;
        if (chatUserMode == ChatUserMode.TTV_CHAT_USERMODE_ADMINSTRATOR) {
            text = new TranslatableText("stream.user.mode.administrator");
            text.getStyle().setColor(DARK_PURPLE);
        } else if (chatUserMode == ChatUserMode.TTV_CHAT_USERMODE_BANNED) {
            if (string == null) {
                text = new TranslatableText("stream.user.mode.banned");
            } else if (bl) {
                text = new TranslatableText("stream.user.mode.banned.self");
            } else {
                text = new TranslatableText("stream.user.mode.banned.other", string);
            }

            text.getStyle().setColor(RED);
        } else if (chatUserMode == ChatUserMode.TTV_CHAT_USERMODE_BROADCASTER) {
            if (string == null) {
                text = new TranslatableText("stream.user.mode.broadcaster");
            } else if (bl) {
                text = new TranslatableText("stream.user.mode.broadcaster.self");
            } else {
                text = new TranslatableText("stream.user.mode.broadcaster.other");
            }

            text.getStyle().setColor(DARK_GREEN);
        } else if (chatUserMode == ChatUserMode.TTV_CHAT_USERMODE_MODERATOR) {
            if (string == null) {
                text = new TranslatableText("stream.user.mode.moderator");
            } else if (bl) {
                text = new TranslatableText("stream.user.mode.moderator.self");
            } else {
                text = new TranslatableText("stream.user.mode.moderator.other", string);
            }

            text.getStyle().setColor(DARK_GREEN);
        } else if (chatUserMode == ChatUserMode.TTV_CHAT_USERMODE_STAFF) {
            text = new TranslatableText("stream.user.mode.staff");
            text.getStyle().setColor(DARK_PURPLE);
        }

        return text;
    }

    @Override
    public void init() {
        int i = this.width / 3;
        int j = i - 130;
        this.buttons.add(new ButtonWidget(1, i * 0 + j / 2, this.height - 70, 130, 20, I18n.translate("stream.userinfo.timeout")));
        this.buttons.add(new ButtonWidget(0, i * 1 + j / 2, this.height - 70, 130, 20, I18n.translate("stream.userinfo.ban")));
        this.buttons.add(new ButtonWidget(2, i * 2 + j / 2, this.height - 70, 130, 20, I18n.translate("stream.userinfo.mod")));
        this.buttons.add(new ButtonWidget(5, i * 0 + j / 2, this.height - 45, 130, 20, I18n.translate("gui.cancel")));
        this.buttons.add(new ButtonWidget(3, i * 1 + j / 2, this.height - 45, 130, 20, I18n.translate("stream.userinfo.unban")));
        this.buttons.add(new ButtonWidget(4, i * 2 + j / 2, this.height - 45, 130, 20, I18n.translate("stream.userinfo.unmod")));
        int k = 0;

        for (Text text : this.f_7324361) {
            k = Math.max(k, this.textRenderer.getWidth(text.getFormattedString()));
        }

        this.f_7607655 = this.width / 2 - k / 2;
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.active) {
            if (button.id == 0) {
                this.stream.m_7694443("/ban " + this.userInfo.displayName);
            } else if (button.id == 3) {
                this.stream.m_7694443("/unban " + this.userInfo.displayName);
            } else if (button.id == 2) {
                this.stream.m_7694443("/mod " + this.userInfo.displayName);
            } else if (button.id == 4) {
                this.stream.m_7694443("/unmod " + this.userInfo.displayName);
            } else if (button.id == 1) {
                this.stream.m_7694443("/timeout " + this.userInfo.displayName);
            }

            this.minecraft.openScreen(null);
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        this.drawCenteredString(this.textRenderer, this.userDisplayName.getString(), this.width / 2, 70, 16777215);
        int i = 80;

        for (Text text : this.f_7324361) {
            this.drawString(this.textRenderer, text.getFormattedString(), this.f_7607655, i, 16777215);
            i += this.textRenderer.fontHeight;
        }

        super.render(mouseX, mouseY, tickDelta);
    }
}
