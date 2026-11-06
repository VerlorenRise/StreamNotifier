package ru.streamnotifier.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Holds and validates plugin configuration options.
 */
public class PluginConfig {

    private final Logger logger;
    private String streamChannelId = "";
    private long cooldownSeconds = 180;
    private Pattern embedTitlePattern = Pattern.compile("^(.*?)\\s+запустил\\s+вещание\\s+на\\s+Twitch!", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private Map<String, String> twitchToMinecraft = Collections.emptyMap();
    private String defaultStreamTitle = "Ищем людей на NovaVanilla !ip";
    private String messageFormat = "<gray>Стример <gradient:#9146FF:#6441A5><bold><streamer></bold></gradient> <white>запустил стрим</white> <yellow><title></yellow> - <click:open_url:'<url>'><hover:show_text:'<gray>Нажмите, чтобы открыть Twitch'><underlined><color:#A970FF><url></color></underlined></hover></click></gray>";

    public PluginConfig(@NotNull Logger logger) {
        this.logger = logger;
    }

    /**
     * Loads and parses values from Bukkit FileConfiguration.
     *
     * @param config the configuration instance
     */
    public void load(@NotNull FileConfiguration config) {
        this.streamChannelId = config.getString("stream-channel-id", "").trim();
        this.cooldownSeconds = Math.max(0, config.getLong("cooldown-seconds", 180));
        this.defaultStreamTitle = config.getString("default-stream-title", "Ищем людей на NovaVanilla !ip");
        if (this.defaultStreamTitle == null) {
            this.defaultStreamTitle = "Ищем людей на NovaVanilla !ip";
        }

        String regex = config.getString("embed-title-regex", "^(.*?) запустил вещание на Twitch!");
        try {
            this.embedTitlePattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        } catch (PatternSyntaxException e) {
            logger.warning("Invalid regex in 'embed-title-regex': " + regex + ". Using default pattern.");
            this.embedTitlePattern = Pattern.compile("^(.*?)\\s+запустил\\s+вещание\\s+на\\s+Twitch!", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        }

        Map<String, String> mapping = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("twitch-to-minecraft");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String mcNick = section.getString(key);
                if (mcNick != null && !mcNick.isBlank()) {
                    mapping.put(key.toLowerCase(Locale.ROOT), mcNick.trim());
                }
            }
        }
        this.twitchToMinecraft = Collections.unmodifiableMap(mapping);

        this.messageFormat = config.getString("message-format", "<gray>Стример <gradient:#9146FF:#6441A5><bold><streamer></bold></gradient> <white>запустил стрим</white> <yellow><title></yellow> - <click:open_url:'<url>'><hover:show_text:'<gray>Нажмите, чтобы открыть Twitch'><underlined><color:#A970FF><url></color></underlined></hover></click></gray>");
        if (this.messageFormat == null) {
            this.messageFormat = "<gray>Стример <gradient:#9146FF:#6441A5><bold><streamer></bold></gradient> <white>запустил стрим</white> <yellow><title></yellow> - <click:open_url:'<url>'><hover:show_text:'<gray>Нажмите, чтобы открыть Twitch'><underlined><color:#A970FF><url></color></underlined></hover></click></gray>";
        }
    }

    @NotNull
    public String getStreamChannelId() {
        return streamChannelId;
    }

    public long getCooldownSeconds() {
        return cooldownSeconds;
    }

    @NotNull
    public Pattern getEmbedTitlePattern() {
        return embedTitlePattern;
    }

    @Nullable
    public String getMappedMinecraftName(@Nullable String twitchLogin) {
        if (twitchLogin == null) {
            return null;
        }
        return twitchToMinecraft.get(twitchLogin.toLowerCase(Locale.ROOT));
    }

    @NotNull
    public Map<String, String> getTwitchToMinecraftMap() {
        return twitchToMinecraft;
    }

    @NotNull
    public String getMessageFormat() {
        return messageFormat;
    }

    @NotNull
    public String getDefaultStreamTitle() {
        return defaultStreamTitle;
    }
}
