package net.minecraft.client.gui.screen;

import com.google.common.collect.Lists;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Session;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.twitch.ErrorTwitchStream;
import net.minecraft.client.twitch.TwitchStream;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;
import tv.twitch.ErrorCode;

public class StreamUnavailableScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Text title = new TranslatableText("stream.unavailable.title");
    private final Screen parent;
    private final StreamUnavailableScreen.Reason reason;
    private final List<TranslatableText> f_0085159;
    private final List<String> f_3763012 = Lists.newArrayList();

    public StreamUnavailableScreen(Screen parent, StreamUnavailableScreen.Reason reason) {
        this(parent, reason, null);
    }

    public StreamUnavailableScreen(Screen parent, StreamUnavailableScreen.Reason reason, List<TranslatableText> list) {
        this.parent = parent;
        this.reason = reason;
        this.f_0085159 = list;
    }

    @Override
    public void init() {
        if (this.f_3763012.isEmpty()) {
            this.f_3763012.addAll(this.textRenderer.split(this.reason.getTitle().getFormattedString(), (int)(this.width * 0.75F)));
            if (this.f_0085159 != null) {
                this.f_3763012.add("");

                for (TranslatableText translatabletext : this.f_0085159) {
                    this.f_3763012.add(translatabletext.getContent());
                }
            }
        }

        if (this.reason.getDescription() != null) {
            this.buttons.add(new ButtonWidget(0, this.width / 2 - 155, this.height - 50, 150, 20, I18n.translate("gui.cancel")));
            this.buttons
                .add(
                    new ButtonWidget(
                        1, this.width / 2 - 155 + 160, this.height - 50, 150, 20, I18n.translate(this.reason.getDescription().getFormattedString())
                    )
                );
        } else {
            this.buttons.add(new ButtonWidget(0, this.width / 2 - 75, this.height - 50, 150, 20, I18n.translate("gui.cancel")));
        }
    }

    @Override
    public void removed() {
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        int i = Math.max((int)(this.height * 0.85 / 2.0 - this.f_3763012.size() * this.textRenderer.fontHeight / 2.0F), 50);
        this.drawCenteredString(this.textRenderer, this.title.getFormattedString(), this.width / 2, i - this.textRenderer.fontHeight * 2, 16777215);

        for (String s : this.f_3763012) {
            this.drawCenteredString(this.textRenderer, s, this.width / 2, i, 10526880);
            i += this.textRenderer.fontHeight;
        }

        super.render(mouseX, mouseY, tickDelta);
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.active) {
            if (button.id == 1) {
                switch (this.reason) {
                    case ACCOUNT_NOT_BOUND:
                    case FAILED_TWITCH_AUTH:
                        this.m_8312728("https://account.mojang.com/me/settings");
                        break;
                    case ACCOUNT_NOT_MIGRATED:
                        this.m_8312728("https://account.mojang.com/migrate");
                        break;
                    case UNSUPPORTED_OS_MAC:
                        this.m_8312728("http://www.apple.com/osx/");
                        break;
                    case UNKNOWN:
                    case LIBRARY_FAILURE:
                    case INITIALIZATION_FAILURE:
                        this.m_8312728("http://bugs.mojang.com/browse/MC");
                }
            }

            this.minecraft.openScreen(this.parent);
        }
    }

    private void m_8312728(String string) {
        try {
            Class<?> oclass = Class.forName("java.awt.Desktop");
            Object object = oclass.getMethod("getDesktop").invoke(null);
            oclass.getMethod("browse", URI.class).invoke(object, new URI(string));
        } catch (Throwable throwable) {
            LOGGER.error("Couldn't open link", throwable);
        }
    }

    public static void m_8434758(Screen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        TwitchStream twitchstream = minecraft.getTwitchStream();
        if (!GLX.useFramebufferObjects) {
            List<TranslatableText> list = Lists.newArrayList();
            list.add(new TranslatableText("stream.unavailable.no_fbo.version", GL11.glGetString(7938)));
            list.add(new TranslatableText("stream.unavailable.no_fbo.blend", GLContext.getCapabilities().GL_EXT_blend_func_separate));
            list.add(new TranslatableText("stream.unavailable.no_fbo.arb", GLContext.getCapabilities().GL_ARB_framebuffer_object));
            list.add(new TranslatableText("stream.unavailable.no_fbo.ext", GLContext.getCapabilities().GL_EXT_framebuffer_object));
            minecraft.openScreen(new StreamUnavailableScreen(screen, StreamUnavailableScreen.Reason.NO_FBO, list));
        } else if (twitchstream instanceof ErrorTwitchStream) {
            if (((ErrorTwitchStream)twitchstream).getError().getMessage().contains("Can't load AMD 64-bit .dll on a IA 32-bit platform")) {
                minecraft.openScreen(new StreamUnavailableScreen(screen, StreamUnavailableScreen.Reason.LIBRARY_ARCH_MISMATCH));
            } else {
                minecraft.openScreen(new StreamUnavailableScreen(screen, StreamUnavailableScreen.Reason.LIBRARY_FAILURE));
            }
        } else if (!twitchstream.m_2070231() && twitchstream.m_3195041() == ErrorCode.TTV_EC_OS_TOO_OLD) {
            switch (Utils.getOS()) {
                case WINDOWS:
                    minecraft.openScreen(new StreamUnavailableScreen(screen, StreamUnavailableScreen.Reason.UNSUPPORTED_OS_WINDOWS));
                    break;
                case MACOS:
                    minecraft.openScreen(new StreamUnavailableScreen(screen, StreamUnavailableScreen.Reason.UNSUPPORTED_OS_MAC));
                    break;
                default:
                    minecraft.openScreen(new StreamUnavailableScreen(screen, StreamUnavailableScreen.Reason.UNSUPPORTED_OS_OTHER));
            }
        } else if (!minecraft.getUserProperties().containsKey("twitch_access_token")) {
            if (minecraft.getSession().getType() == Session.Type.LEGACY) {
                minecraft.openScreen(new StreamUnavailableScreen(screen, StreamUnavailableScreen.Reason.ACCOUNT_NOT_MIGRATED));
            } else {
                minecraft.openScreen(new StreamUnavailableScreen(screen, StreamUnavailableScreen.Reason.ACCOUNT_NOT_BOUND));
            }
        } else if (!twitchstream.isLoggedIn()) {
            switch (twitchstream.getErrorReason()) {
                case INVALID_TOKEN:
                    minecraft.openScreen(new StreamUnavailableScreen(screen, StreamUnavailableScreen.Reason.FAILED_TWITCH_AUTH));
                    break;
                case ERROR:
                default:
                    minecraft.openScreen(new StreamUnavailableScreen(screen, StreamUnavailableScreen.Reason.FAILED_TWITCH_AUTH_ERROR));
            }
        } else if (twitchstream.m_3195041() != null) {
            List<TranslatableText> list1 = Arrays.asList(
                new TranslatableText("stream.unavailable.initialization_failure.extra", ErrorCode.getString(twitchstream.m_3195041()))
            );
            minecraft.openScreen(new StreamUnavailableScreen(screen, StreamUnavailableScreen.Reason.INITIALIZATION_FAILURE, list1));
        } else {
            minecraft.openScreen(new StreamUnavailableScreen(screen, StreamUnavailableScreen.Reason.UNKNOWN));
        }
    }

    public enum Reason {
        NO_FBO(new TranslatableText("stream.unavailable.no_fbo")),
        LIBRARY_ARCH_MISMATCH(new TranslatableText("stream.unavailable.library_arch_mismatch")),
        LIBRARY_FAILURE(new TranslatableText("stream.unavailable.library_failure"), new TranslatableText("stream.unavailable.report_to_mojang")),
        UNSUPPORTED_OS_WINDOWS(new TranslatableText("stream.unavailable.not_supported.windows")),
        UNSUPPORTED_OS_MAC(new TranslatableText("stream.unavailable.not_supported.mac"), new TranslatableText("stream.unavailable.not_supported.mac.okay")),
        UNSUPPORTED_OS_OTHER(new TranslatableText("stream.unavailable.not_supported.other")),
        ACCOUNT_NOT_MIGRATED(
            new TranslatableText("stream.unavailable.account_not_migrated"), new TranslatableText("stream.unavailable.account_not_migrated.okay")
        ),
        ACCOUNT_NOT_BOUND(new TranslatableText("stream.unavailable.account_not_bound"), new TranslatableText("stream.unavailable.account_not_bound.okay")),
        FAILED_TWITCH_AUTH(new TranslatableText("stream.unavailable.failed_auth"), new TranslatableText("stream.unavailable.failed_auth.okay")),
        FAILED_TWITCH_AUTH_ERROR(new TranslatableText("stream.unavailable.failed_auth_error")),
        INITIALIZATION_FAILURE(new TranslatableText("stream.unavailable.initialization_failure"), new TranslatableText("stream.unavailable.report_to_mojang")),
        UNKNOWN(new TranslatableText("stream.unavailable.unknown"), new TranslatableText("stream.unavailable.report_to_mojang"));

        private final Text title;
        private final Text description;

        Reason(Text title) {
            this(title, null);
        }

        Reason(Text title, Text description) {
            this.title = title;
            this.description = description;
        }

        public Text getTitle() {
            return this.title;
        }

        public Text getDescription() {
            return this.description;
        }
    }
}
