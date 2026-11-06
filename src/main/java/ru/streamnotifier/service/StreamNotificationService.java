package ru.streamnotifier.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import ru.streamnotifier.config.PluginConfig;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Service responsible for cooldown checks and broadcasting stream notifications to Minecraft chat.
 */
public class StreamNotificationService {

    private final Plugin plugin;
    private final PluginConfig config;
    private final Map<String, Long> lastAnnounceTimestamps = new ConcurrentHashMap<>();

    public StreamNotificationService(@NotNull Plugin plugin, @NotNull PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Checks whether the specified Twitch login is currently on cooldown.
     *
     * @param twitchLogin the streamer login
     * @return true if currently on cooldown, false otherwise
     */
    public boolean isOnCooldown(@NotNull String twitchLogin) {
        long cooldownMs = config.getCooldownSeconds() * 1000L;
        if (cooldownMs <= 0) {
            return false;
        }

        String key = twitchLogin.toLowerCase(Locale.ROOT);
        Long lastTime = lastAnnounceTimestamps.get(key);
        if (lastTime == null) {
            return false;
        }

        return (System.currentTimeMillis() - lastTime) < cooldownMs;
    }

    /**
     * Records the announcement timestamp for the given streamer to prevent duplicates.
     *
     * @param twitchLogin the streamer login
     */
    public void recordAnnouncement(@NotNull String twitchLogin) {
        lastAnnounceTimestamps.put(twitchLogin.toLowerCase(Locale.ROOT), System.currentTimeMillis());
    }

    /**
     * Clears all recorded cooldown timestamps.
     */
    public void clearCooldowns() {
        lastAnnounceTimestamps.clear();
    }

    /**
     * Broadcasts the stream notification to the server in the main server thread.
     *
     * @param streamerName the resolved Minecraft or display streamer name
     * @param streamTitle  the title of the stream
     * @param streamUrl    the full Twitch stream URL
     */
    public void broadcastStreamNotification(@NotNull String streamerName, @NotNull String streamTitle, @NotNull String streamUrl) {
        // Enforce execution on Bukkit main server thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Component component = buildComponent(streamerName, streamTitle, streamUrl);
                Bukkit.broadcast(component);
                plugin.getLogger().info("Broadcasted stream notification for '" + streamerName + "' (" + streamUrl + ").");
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to broadcast stream notification: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Overload for broadcasting stream notification without stream title.
     *
     * @param streamerName the resolved Minecraft or display streamer name
     * @param streamUrl    the full Twitch stream URL
     */
    public void broadcastStreamNotification(@NotNull String streamerName, @NotNull String streamUrl) {
        broadcastStreamNotification(streamerName, "", streamUrl);
    }

    /**
     * Builds Adventure Component from the configured MiniMessage template.
     *
     * @param streamerName the streamer name
     * @param streamTitle  the stream title
     * @param streamUrl    the Twitch URL
     * @return deserialized Component
     */
    @NotNull
    public Component buildComponent(@NotNull String streamerName, @NotNull String streamTitle, @NotNull String streamUrl) {
        String template = config.getMessageFormat();

        // MiniMessage attribute arguments like <click:open_url:'<url>'> require direct string substitution.
        // We substitute <url> and %url% safely since Twitch URLs have a strict alphanumeric/URL format.
        // Safely replace legacy percent-placeholders (%streamer% and %title%) by mapping them to safe tag placeholders.
        String prepared = template
                .replace("<url>", streamUrl)
                .replace("%url%", streamUrl)
                .replace("%streamer%", "<streamer>")
                .replace("%title%", "<title>");

        // Inject <streamer> and <title> as safe placeholders to prevent special characters
        // (such as '!ip', quotes, '<', '>') from breaking MiniMessage parsing.
        return MiniMessage.miniMessage().deserialize(
                prepared,
                Placeholder.component("streamer", Component.text(streamerName)),
                Placeholder.unparsed("streamer", streamerName),
                Placeholder.component("title", Component.text(streamTitle)),
                Placeholder.unparsed("title", streamTitle)
        );
    }

    /**
     * Overload for building Adventure Component without stream title.
     *
     * @param streamerName the streamer name
     * @param streamUrl    the Twitch URL
     * @return deserialized Component
     */
    @NotNull
    public Component buildComponent(@NotNull String streamerName, @NotNull String streamUrl) {
        return buildComponent(streamerName, "", streamUrl);
    }
}
