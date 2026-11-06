package ru.streamnotifier;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordReadyEvent;
import github.scarsz.discordsrv.dependencies.jda.api.JDA;
import github.scarsz.discordsrv.util.DiscordUtil;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import ru.streamnotifier.command.StreamNotifierCommand;
import ru.streamnotifier.config.PluginConfig;
import ru.streamnotifier.discord.DiscordListener;
import ru.streamnotifier.service.StreamNotificationService;

import java.util.logging.Level;

/**
 * Main plugin class for StreamNotifier.
 */
public class StreamNotifierPlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private StreamNotificationService notificationService;
    private DiscordListener discordListener;
    private boolean jdaListenerRegistered = false;
    private boolean subscribedToDiscordSRV = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.pluginConfig = new PluginConfig(getLogger());
        this.pluginConfig.load(getConfig());

        this.notificationService = new StreamNotificationService(this, pluginConfig);

        // Register administration command
        PluginCommand cmd = getCommand("streamnotifier");
        if (cmd != null) {
            StreamNotifierCommand commandExecutor = new StreamNotifierCommand(this);
            cmd.setExecutor(commandExecutor);
            cmd.setTabCompleter(commandExecutor);
        } else {
            getLogger().warning("Command /streamnotifier not found in plugin.yml!");
        }

        // Register DiscordSRV hook
        hookDiscordSRV();

        getLogger().info("StreamNotifier v" + getDescription().getVersion() + " has been successfully enabled!");
    }

    @Override
    public void onDisable() {
        // 1. Unregister JDA listener safely
        unregisterJdaListener();

        // 2. Unsubscribe from DiscordSRV event bus
        if (subscribedToDiscordSRV) {
            try {
                DiscordSRV.api.unsubscribe(this);
                subscribedToDiscordSRV = false;
                getLogger().info("Unsubscribed from DiscordSRV API.");
            } catch (Exception e) {
                getLogger().warning("Failed to unsubscribe from DiscordSRV API: " + e.getMessage());
            }
        }

        // 3. Cancel any remaining scheduled tasks
        getServer().getScheduler().cancelTasks(this);

        getLogger().info("StreamNotifier has been disabled.");
    }

    /**
     * Attempts to hook into DiscordSRV.
     */
    private void hookDiscordSRV() {
        Plugin discordSrv = getServer().getPluginManager().getPlugin("DiscordSRV");
        if (discordSrv == null || !discordSrv.isEnabled()) {
            getLogger().severe("DiscordSRV not found or disabled! Stream announcements will not be received.");
            return;
        }

        try {
            DiscordSRV.api.subscribe(this);
            subscribedToDiscordSRV = true;
            getLogger().info("Subscribed to DiscordSRV API event bus.");

            // Check if DiscordSRV JDA is already ready (e.g. plugin reload or loaded after DiscordSRV)
            checkAndRegisterJda();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to hook into DiscordSRV: " + e.getMessage(), e);
        }
    }

    /**
     * DiscordSRV lifecycle event listener. Called when Discord bot is fully logged in and ready.
     *
     * @param event the DiscordReadyEvent
     */
    @Subscribe
    public void onDiscordReady(DiscordReadyEvent event) {
        getLogger().info("DiscordReadyEvent received. Initializing JDA event listener...");
        checkAndRegisterJda();
    }

    /**
     * Registers the JDA listener if JDA is available and not already registered.
     */
    public synchronized void checkAndRegisterJda() {
        if (jdaListenerRegistered) {
            return;
        }

        try {
            JDA jda = DiscordUtil.getJda();
            if (jda != null && jda.getStatus() == JDA.Status.CONNECTED) {
                if (discordListener == null) {
                    discordListener = new DiscordListener(this, notificationService, pluginConfig);
                }
                jda.addEventListener(discordListener);
                jdaListenerRegistered = true;
                getLogger().info("Successfully registered JDA event listener for stream announcements.");
            } else {
                getLogger().fine("DiscordSRV JDA is not yet CONNECTED (status: " + (jda != null ? jda.getStatus() : "null") + "). Waiting for DiscordReadyEvent.");
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error while checking/registering JDA event listener: " + e.getMessage(), e);
        }
    }

    /**
     * Unregisters the JDA listener from DiscordSRV's JDA instance.
     */
    public synchronized void unregisterJdaListener() {
        if (!jdaListenerRegistered) {
            return;
        }

        try {
            JDA jda = DiscordUtil.getJda();
            if (jda != null && discordListener != null) {
                jda.removeEventListener(discordListener);
                getLogger().info("Successfully removed JDA event listener.");
            }
        } catch (Exception e) {
            getLogger().warning("Error while unregistering JDA listener: " + e.getMessage());
        } finally {
            jdaListenerRegistered = false;
            discordListener = null;
        }
    }

    /**
     * Reloads configuration from disk and updates config parameters.
     */
    public void reloadPluginConfig() {
        reloadConfig();
        pluginConfig.load(getConfig());
        getLogger().info("Plugin configuration reloaded.");
    }

    @NotNull
    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    @NotNull
    public StreamNotificationService getNotificationService() {
        return notificationService;
    }
}
