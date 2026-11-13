package ru.streamnotifier;

import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.JDA;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Message;
import github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed;
import github.scarsz.discordsrv.dependencies.jda.api.entities.SelfUser;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import github.scarsz.discordsrv.dependencies.jda.api.entities.User;
import github.scarsz.discordsrv.dependencies.jda.api.events.message.guild.GuildMessageReceivedEvent;
import github.scarsz.discordsrv.dependencies.jda.api.events.message.guild.GuildMessageUpdateEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.streamnotifier.config.PluginConfig;
import ru.streamnotifier.discord.DiscordListener;
import ru.streamnotifier.service.StreamNotificationService;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordListenerTest {
    private static final String CHANNEL_ID = "123456789012345678";
    private static final String SELF_ID = "111111111111111111";
    private static final String URL = "https://www.twitch.tv/vinomudok";
    private static final String TITLE = "Ищем людей на NovaVanilla !ip";
    private static final String CONTENT = "название стрима: " + TITLE + "\nссылка на стрим: " + URL;

    private final List<List<String>> announcements = new ArrayList<>();
    private DiscordListener listener;

    @BeforeEach
    void setUp() {
        Logger logger = Logger.getLogger(DiscordListenerTest.class.getName());
        Plugin plugin = stub(Plugin.class, Map.of("getLogger", logger));
        PluginConfig config = new PluginConfig(logger) {
            @Override
            public String getStreamChannelId() {
                return CHANNEL_ID;
            }
        };
        StreamNotificationService service = new StreamNotificationService(plugin, config) {
            @Override
            public void broadcastStreamNotification(String streamer, String title, String url) {
                announcements.add(List.of(streamer, title, url));
            }
        };
        listener = new DiscordListener(plugin, service, config);
    }

    @Test
    void acceptsHumanAnnouncement() {
        receive(message(false, false, false, CHANNEL_ID, CONTENT, List.of()));
        assertAnnouncement("vinomudok");
    }

    @Test
    void acceptsOtherBotAnnouncement() {
        receive(message(true, false, false, CHANNEL_ID, CONTENT, List.of()));
        assertAnnouncement("vinomudok");
    }

    @Test
    void ignoresDiscordSrvOwnBotToAvoidLoops() {
        receive(message(true, true, false, CHANNEL_ID, CONTENT, List.of()));
        assertTrue(announcements.isEmpty());
    }

    @Test
    void acceptsStreamerWebhookWithMarkdownLinkAndSingleLineAnnouncement() {
        String text = "@⚔️ | ИГРОК ⸺⸺⸺⸺⸺⸺⸺⸺⸺⸺⸺ Подать заявку можно тут: Текст⁠💾-подать-заявку "
                + "⸺⸺⸺⸺⸺⸺⸺⸺⸺⸺⸺ айпи сервера: novavanilla.burmalda.me версия сервера 26.2 "
                + "⸺⸺⸺⸺⸺⸺⸺⸺⸺⸺⸺ название стрима: Строим метро | novavanilla.burmalda.me | 26.2  "
                + "ссылка на стрим: [https://www.twitch.tv/verlorenrise]"
                + "(https://www.twitch.tv/verlorenrise \"https://www.twitch.tv/verlorenrise\") "
                + "⸺⸺⸺⸺⸺⸺⸺⸺⸺⸺⸺";
        receive(message(true, false, true, CHANNEL_ID, text, List.of()));
        assertEquals(List.of(List.of("verlorenrise", "Строим метро | novavanilla.burmalda.me | 26.2",
                "https://www.twitch.tv/verlorenrise")), announcements);
    }

    @Test
    void acceptsStreamerWebhookWithAnnouncementInEmbedDescription() {
        String text = "@⚔️ | ИГРОК\n⸺⸺⸺⸺⸺\nназвание стрима: **Строим метро**\n"
                + "ссылка на стрим: [Смотреть](https://www.twitch.tv/verlorenrise)\n⸺⸺⸺⸺⸺";
        MessageEmbed embed = new EmbedBuilder().setDescription(text).build();
        receive(message(true, false, true, CHANNEL_ID, "", List.of(embed)));
        assertEquals(List.of(List.of("verlorenrise", "Строим метро",
                "https://www.twitch.tv/verlorenrise")), announcements);
    }

    @Test
    void stripsDecorativeSeparatorAfterTitle() {
        receive(message(true, false, true, CHANNEL_ID,
                "название стрима: " + TITLE + " ⸺⸺⸺\nссылка на стрим: " + URL, List.of()));
        assertAnnouncement("vinomudok");
    }

    @Test
    void emptyTitleDoesNotConsumeNextField() {
        receive(message(true, false, true, CHANNEL_ID,
                "название стрима:\nссылка на стрим: " + URL, List.of()));
        assertAnnouncement("vinomudok");
    }

    @Test
    void acceptsWebhookEmbedWithEmptyContent() {
        MessageEmbed embed = new EmbedBuilder()
                .setTitle("VinoMudok запустил вещание на Twitch!", URL)
                .addField("Название стрима", TITLE, false)
                .build();
        receive(message(true, false, true, CHANNEL_ID, "", List.of(embed)));
        assertAnnouncement("VinoMudok");
    }

    @Test
    void acceptsEmbedAddedByUpdate() {
        receive(message(true, false, false, CHANNEL_ID, "", List.of()));
        assertTrue(announcements.isEmpty());
        MessageEmbed embed = new EmbedBuilder().setDescription(CONTENT).build();
        Message updated = message(true, false, false, CHANNEL_ID, "", List.of(embed));
        listener.onGuildMessageUpdate(new GuildMessageUpdateEvent(updated.getJDA(), 2, updated));
        assertAnnouncement("vinomudok");
    }

    @Test
    void ignoresOtherChannels() {
        receive(message(true, false, false, "999999999999999999", CONTENT, List.of()));
        assertTrue(announcements.isEmpty());
    }

    @Test
    void ignoresMessagesWithoutStreamUrl() {
        receive(message(true, false, false, CHANNEL_ID, "Обычное сообщение бота", List.of()));
        assertTrue(announcements.isEmpty());
    }

    @Test
    void cooldownSuppressesDuplicateBotAnnouncements() {
        Message message = message(true, false, false, CHANNEL_ID, CONTENT, List.of());
        receive(message);
        listener.onGuildMessageUpdate(new GuildMessageUpdateEvent(message.getJDA(), 2, message));
        assertAnnouncement("vinomudok");
    }

    private void receive(Message message) {
        listener.onGuildMessageReceived(new GuildMessageReceivedEvent(message.getJDA(), 1, message));
    }

    private void assertAnnouncement(String streamer) {
        assertEquals(List.of(List.of(streamer, TITLE, URL)), announcements);
    }

    private Message message(boolean bot, boolean self, boolean webhook, String channelId,
                            String content, List<MessageEmbed> embeds) {
        User author = stub(User.class, Map.of("getId", self ? SELF_ID : "222222222222222222",
                "getName", "sender", "isBot", bot));
        SelfUser selfUser = stub(SelfUser.class, Map.of("getId", SELF_ID));
        JDA jda = stub(JDA.class, Map.of("getSelfUser", selfUser));
        TextChannel channel = stub(TextChannel.class, Map.of("getId", channelId));
        return stub(Message.class, Map.of("getIdLong", 333333333333333333L,
                "getJDA", jda, "getAuthor", author, "getChannel", channel,
                "getTextChannel", channel, "getContentRaw", content,
                "getEmbeds", embeds, "isWebhookMessage", webhook));
    }

    private static <T> T stub(Class<T> type, Map<String, Object> values) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> values.get(method.getName())));
    }
}
