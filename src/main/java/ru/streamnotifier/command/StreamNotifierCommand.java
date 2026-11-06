package ru.streamnotifier.command;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.streamnotifier.StreamNotifierPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Handles /streamnotifier commands (reload, test).
 */
public class StreamNotifierCommand implements CommandExecutor, TabCompleter {

    private final StreamNotifierPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public StreamNotifierCommand(@NotNull StreamNotifierPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (!sender.hasPermission("streamnotifier.admin")) {
            sender.sendMessage(miniMessage.deserialize("<red>У вас недостаточно прав для выполнения этой команды.</red>"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "reload" -> {
                plugin.reloadPluginConfig();
                sender.sendMessage(miniMessage.deserialize("<green>[StreamNotifier] Конфигурация успешно перезагружена!</green>"));
                return true;
            }
            case "test" -> {
                if (args.length < 2) {
                    sender.sendMessage(miniMessage.deserialize("<yellow>Использование: /" + label + " test <twitch_login> [название стрима]</yellow>"));
                    return true;
                }

                String twitchNick = args[1].trim();

                String streamTitle;
                if (args.length >= 3) {
                    streamTitle = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                } else {
                    String defaultTitle = plugin.getPluginConfig().getDefaultStreamTitle();
                    streamTitle = (defaultTitle != null && !defaultTitle.isBlank()) ? defaultTitle : "Ищем людей на NovaVanilla !ip";
                }

                String streamUrl = "https://www.twitch.tv/" + twitchNick;

                String mapped = plugin.getPluginConfig().getMappedMinecraftName(twitchNick);
                String streamerName = (mapped != null && !mapped.isBlank()) ? mapped : twitchNick;

                plugin.getNotificationService().broadcastStreamNotification(streamerName, streamTitle, streamUrl);

                sender.sendMessage(miniMessage.deserialize(
                        "<green>[StreamNotifier] Тестовое уведомление для стримера '<aqua>" + streamerName + "</aqua>' (\"" + streamTitle + "\") отправлено в глобальный чат.</green>"
                ));
                return true;
            }
            default -> {
                sendHelp(sender, label);
                return true;
            }
        }
    }

    private void sendHelp(@NotNull CommandSender sender, @NotNull String label) {
        sender.sendMessage(miniMessage.deserialize("<gradient:#9146FF:#6441A5><bold>StreamNotifier</bold></gradient> <gray>v" + plugin.getDescription().getVersion() + "</gray>"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/" + label + " reload</yellow> <gray>- Перезагрузить конфиг плагина</gray>"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/" + label + " test <twitch_login> [название стрима]</yellow> <gray>- Протестировать отправку уведомления о стриме</gray>"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        if (!sender.hasPermission("streamnotifier.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> sub = List.of("reload", "test");
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> results = new ArrayList<>();
            for (String s : sub) {
                if (s.startsWith(prefix)) {
                    results.add(s);
                }
            }
            return results;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> results = new ArrayList<>();
            for (String key : plugin.getPluginConfig().getTwitchToMinecraftMap().keySet()) {
                if (key.startsWith(prefix)) {
                    results.add(key);
                }
            }
            return results;
        }

        return Collections.emptyList();
    }
}
