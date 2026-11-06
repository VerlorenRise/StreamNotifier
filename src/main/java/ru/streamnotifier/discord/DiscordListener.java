package ru.streamnotifier.discord;

import github.scarsz.discordsrv.dependencies.jda.api.entities.Message;
import github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed;
import github.scarsz.discordsrv.dependencies.jda.api.events.message.guild.GuildMessageReceivedEvent;
import github.scarsz.discordsrv.dependencies.jda.api.hooks.ListenerAdapter;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import ru.streamnotifier.config.PluginConfig;
import ru.streamnotifier.service.StreamNotificationService;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Listens for stream announcements in the designated Discord channel using DiscordSRV's shaded JDA.
 */
public class DiscordListener extends ListenerAdapter {

    private static final Pattern STREAM_URL_PATTERN = Pattern.compile(
            "ссылка на стрим:\\s*(https?://(?:www\\.)?twitch\\.tv/([a-zA-Z0-9_]+))",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern STREAM_TITLE_PATTERN = Pattern.compile(
            "(?im)^название стрима:\\s*\\**(.+?)\\**\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.UNICODE_CASE
    );

    private final Plugin plugin;
    private final StreamNotificationService notificationService;
    private final PluginConfig config;

    public DiscordListener(@NotNull Plugin plugin,
                           @NotNull StreamNotificationService notificationService,
                           @NotNull PluginConfig config) {
        this.plugin = plugin;
        this.notificationService = notificationService;
        this.config = config;
    }

    @Override
    public void onGuildMessageReceived(@NotNull GuildMessageReceivedEvent event) {
        // 1. Verify channel ID
        String targetChannelId = config.getStreamChannelId();
        if (targetChannelId.isEmpty() || !event.getChannel().getId().equals(targetChannelId)) {
            return;
        }

        // 2. Ignore messages sent by DiscordSRV's own bot account to avoid loops
        if (event.getJDA().getSelfUser() != null &&
                event.getAuthor().getId().equals(event.getJDA().getSelfUser().getId())) {
            return;
        }

        Message message = event.getMessage();
        String rawContent = message.getContentRaw();
        if (rawContent == null || rawContent.isEmpty()) {
            return;
        }

        // 3. Extract Twitch stream URL and login
        Matcher urlMatcher = STREAM_URL_PATTERN.matcher(rawContent);
        if (!urlMatcher.find()) {
            return;
        }

        String streamUrl = urlMatcher.group(1);
        String twitchLogin = urlMatcher.group(2);

        // 4. Cooldown check per streamer channel
        if (notificationService.isOnCooldown(twitchLogin)) {
            plugin.getLogger().fine("Stream announcement for '" + twitchLogin + "' ignored due to active cooldown.");
            return;
        }

        // 5. Extract stream title from message content or fallback to default
        String streamTitle = extractStreamTitle(rawContent);

        // 6. Resolve streamer display name according to priority rules
        String streamerName = resolveStreamerName(message, twitchLogin);

        // 7. Record cooldown and broadcast notification to Minecraft
        notificationService.recordAnnouncement(twitchLogin);
        notificationService.broadcastStreamNotification(streamerName, streamTitle, streamUrl);
    }

    /**
     * Extracts stream title from message raw content, stripping markdown asterisks and trailing spaces.
     * Falls back to default title if missing or blank.
     */
    @NotNull
    private String extractStreamTitle(@NotNull String rawContent) {
        Matcher matcher = STREAM_TITLE_PATTERN.matcher(rawContent);
        if (matcher.find()) {
            String title = matcher.group(1).trim().replaceAll("^\\*+|\\*+$", "").trim();
            if (!title.isEmpty()) {
                return title;
            }
        }
        String defaultTitle = config.getDefaultStreamTitle();
        return (defaultTitle != null && !defaultTitle.isBlank()) ? defaultTitle : "Ищем людей на NovaVanilla !ip";
    }

    /**
     * Resolves the streamer's name according to 3 priority levels:
     * 1. Manual mapping in config (twitch-to-minecraft).
     * 2. Preserved nickname casing from MessageEmbed title matching twitch login.
     * 3. Fallback to raw twitch login from URL.
     */
    @NotNull
    private String resolveStreamerName(@NotNull Message message, @NotNull String twitchLogin) {
        // Priority 1: config twitch-to-minecraft mapping
        String mapped = config.getMappedMinecraftName(twitchLogin);
        if (mapped != null && !mapped.isBlank()) {
            return mapped;
        }

        // Priority 2: MessageEmbed title with matching nickname (preserves proper capitalization)
        List<MessageEmbed> embeds = message.getEmbeds();
        if (embeds != null && !embeds.isEmpty()) {
            Pattern titlePattern = config.getEmbedTitlePattern();
            for (MessageEmbed embed : embeds) {
                if (embed != null && embed.getTitle() != null) {
                    Matcher matcher = titlePattern.matcher(embed.getTitle().trim());
                    if (matcher.find() && matcher.groupCount() >= 1) {
                        String embedNick = matcher.group(1).trim();
                        if (!embedNick.isEmpty() && embedNick.equalsIgnoreCase(twitchLogin)) {
                            return embedNick;
                        }
                    }
                }
            }
        }

        // Priority 3: Fallback to twitchLogin from URL
        return twitchLogin;
    }
}
