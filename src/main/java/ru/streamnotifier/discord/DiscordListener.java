package ru.streamnotifier.discord;

import github.scarsz.discordsrv.dependencies.jda.api.entities.Message;
import github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed;
import github.scarsz.discordsrv.dependencies.jda.api.events.message.guild.GuildMessageReceivedEvent;
import github.scarsz.discordsrv.dependencies.jda.api.events.message.guild.GuildMessageUpdateEvent;
import github.scarsz.discordsrv.dependencies.jda.api.hooks.ListenerAdapter;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.streamnotifier.config.PluginConfig;
import ru.streamnotifier.service.StreamNotificationService;

import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Listens for stream announcements in the designated Discord channel using DiscordSRV's shaded JDA.
 * Supports human messages, bot messages, webhooks, and embedded announcements.
 */
public class DiscordListener extends ListenerAdapter {

    private static final Pattern STREAM_LABEL_URL_PATTERN = Pattern.compile(
            "(?i)(?:ссылка\\s+на\\s+стрим|stream\\s*url|twitch\\s*url)\\s*[:\\*\\s]*<?(https?://(?:www\\.)?twitch\\.tv/([a-zA-Z0-9_]+))>?|(?i)<(https?://(?:www\\.)?twitch\\.tv/([a-zA-Z0-9_]+))>",
            Pattern.UNICODE_CASE
    );

    private static final Pattern TWITCH_ANY_URL_PATTERN = Pattern.compile(
            "https?://(?:www\\.)?twitch\\.tv/([a-zA-Z0-9_]+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern STREAM_TITLE_PATTERN = Pattern.compile(
            "(?im)(?:название\\s+стрима|stream\\s*title|тема\\s*стрима)\\s*[:\\*\\s]*([^\r\n]+)",
            Pattern.UNICODE_CASE
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
        processMessage(event.getMessage());
    }

    @Override
    public void onGuildMessageUpdate(@NotNull GuildMessageUpdateEvent event) {
        processMessage(event.getMessage());
    }

    /**
     * Unified message processor for both new messages and message updates (when embeds load asynchronously).
     *
     * @param message the received or updated Discord message
     */
    public void processMessage(@NotNull Message message) {
        try {
            // 1. Verify channel ID
            String targetChannelId = config.getStreamChannelId();
            if (targetChannelId.isEmpty() || !message.getChannel().getId().equals(targetChannelId)) {
                return;
            }

            // 2. Ignore messages sent by DiscordSRV's own bot account to avoid loops
            if (message.getJDA().getSelfUser() != null &&
                    message.getAuthor().getId().equals(message.getJDA().getSelfUser().getId())) {
                return;
            }

            // 3. Extract combined text from message content, embeds (description, title, fields)
            String combinedText = extractCombinedText(message);
            if (combinedText.isBlank()) {
                return;
            }

            // 4. Extract Twitch stream URL and streamer login
            StreamInfo streamInfo = extractStreamInfo(combinedText, message);
            if (streamInfo == null) {
                return;
            }

            String twitchLogin = streamInfo.twitchLogin();
            String streamUrl = streamInfo.streamUrl();

            // 5. Cooldown check per streamer channel
            if (notificationService.isOnCooldown(twitchLogin)) {
                plugin.getLogger().fine("Stream announcement for '" + twitchLogin + "' ignored due to active cooldown.");
                return;
            }

            // 6. Extract stream title from combined text or fallback to default
            String streamTitle = extractStreamTitle(combinedText);

            // 7. Resolve streamer display name according to priority rules
            String streamerName = resolveStreamerName(message, twitchLogin);

            // 8. Record cooldown and broadcast notification to Minecraft
            notificationService.recordAnnouncement(twitchLogin);
            notificationService.broadcastStreamNotification(streamerName, streamTitle, streamUrl);

            String senderType = message.isWebhookMessage() ? "webhook" : (message.getAuthor().isBot() ? "bot" : "user");
            plugin.getLogger().info("Successfully announced stream for '" + streamerName + "' [" + twitchLogin + "] (" + streamUrl + ") sent by " + senderType + " (" + message.getAuthor().getName() + ").");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Unexpected error in DiscordListener message processing: " + e.getMessage(), e);
        }
    }

    /**
     * Aggregates all text sources from a Discord message: content, embed titles, descriptions, and fields.
     */
    @NotNull
    private String extractCombinedText(@NotNull Message message) {
        StringBuilder sb = new StringBuilder();

        if (message.getContentRaw() != null && !message.getContentRaw().isEmpty()) {
            sb.append(message.getContentRaw()).append("\n");
        }

        List<MessageEmbed> embeds = message.getEmbeds();
        if (embeds != null && !embeds.isEmpty()) {
            for (MessageEmbed embed : embeds) {
                if (embed == null) continue;
                if (embed.getTitle() != null) {
                    sb.append(embed.getTitle()).append("\n");
                }
                if (embed.getDescription() != null) {
                    sb.append(embed.getDescription()).append("\n");
                }
                if (embed.getUrl() != null) {
                    sb.append(embed.getUrl()).append("\n");
                }
                if (embed.getAuthor() != null) {
                    if (embed.getAuthor().getName() != null) sb.append(embed.getAuthor().getName()).append("\n");
                    if (embed.getAuthor().getUrl() != null) sb.append(embed.getAuthor().getUrl()).append("\n");
                }
                for (MessageEmbed.Field field : embed.getFields()) {
                    if (field.getName() != null) sb.append(field.getName()).append(": ");
                    if (field.getValue() != null) sb.append(field.getValue()).append("\n");
                }
                if (embed.getFooter() != null && embed.getFooter().getText() != null) {
                    sb.append(embed.getFooter().getText()).append("\n");
                }
            }
        }

        return sb.toString();
    }

    /**
     * Extracts Twitch stream URL and login from combined text and embeds.
     */
    @Nullable
    private StreamInfo extractStreamInfo(@NotNull String combinedText, @NotNull Message message) {
        // Priority A: Search for explicit label (ссылка на стрим: ...)
        Matcher labelMatcher = STREAM_LABEL_URL_PATTERN.matcher(combinedText);
        if (labelMatcher.find()) {
            String url = labelMatcher.group(1) != null ? labelMatcher.group(1) : labelMatcher.group(3);
            String login = labelMatcher.group(2) != null ? labelMatcher.group(2) : labelMatcher.group(4);
            if (url != null && login != null) {
                return new StreamInfo(login, cleanUrl(url));
            }
        }

        // Priority B: Search for any Twitch URL in the combined text
        Matcher anyMatcher = TWITCH_ANY_URL_PATTERN.matcher(combinedText);
        if (anyMatcher.find()) {
            return new StreamInfo(anyMatcher.group(1), cleanUrl(anyMatcher.group(0)));
        }

        // Priority C: Direct embed URL checks
        List<MessageEmbed> embeds = message.getEmbeds();
        if (embeds != null && !embeds.isEmpty()) {
            for (MessageEmbed embed : embeds) {
                if (embed == null) continue;

                if (embed.getUrl() != null) {
                    Matcher m = TWITCH_ANY_URL_PATTERN.matcher(embed.getUrl());
                    if (m.find()) {
                        return new StreamInfo(m.group(1), cleanUrl(m.group(0)));
                    }
                }

                if (embed.getAuthor() != null && embed.getAuthor().getUrl() != null) {
                    Matcher m = TWITCH_ANY_URL_PATTERN.matcher(embed.getAuthor().getUrl());
                    if (m.find()) {
                        return new StreamInfo(m.group(1), cleanUrl(m.group(0)));
                    }
                }
            }
        }

        return null;
    }

    private String cleanUrl(@NotNull String url) {
        return url.replaceAll("[<>\\s]", "").trim();
    }

    /**
     * Extracts stream title from combined text, stripping markdown asterisks and trailing spaces.
     * Falls back to default title if missing or blank.
     */
    @NotNull
    private String extractStreamTitle(@NotNull String combinedText) {
        Matcher matcher = STREAM_TITLE_PATTERN.matcher(combinedText);
        if (matcher.find()) {
            String raw = matcher.group(1).trim();
            // Clean markdown characters from both ends: *, `, _, >, ~
            String cleaned = raw.replaceAll("^[\\*`_>~\\s]+|[\\*`_>~\\s]+$", "").trim();
            if (!cleaned.isEmpty()) {
                return cleaned;
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

    private record StreamInfo(@NotNull String twitchLogin, @NotNull String streamUrl) {}
}
