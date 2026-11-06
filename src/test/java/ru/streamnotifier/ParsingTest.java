package ru.streamnotifier;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class ParsingTest {

    private static final Pattern STREAM_URL_PATTERN = Pattern.compile(
            "ссылка на стрим:\\s*(https?://(?:www\\.)?twitch\\.tv/([a-zA-Z0-9_]+))",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern STREAM_TITLE_PATTERN = Pattern.compile(
            "(?im)^название стрима:\\s*\\**(.+?)\\**\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.UNICODE_CASE
    );

    @Test
    void testStandardDiscordMessageParsing() {
        String discordMessage = """
                название стрима: Ищем людей на NovaVanilla !ip
                ссылка на стрим: https://www.twitch.tv/vinomudok
                """;

        // URL matcher
        Matcher urlMatcher = STREAM_URL_PATTERN.matcher(discordMessage);
        assertTrue(urlMatcher.find(), "Twitch URL should match");
        assertEquals("https://www.twitch.tv/vinomudok", urlMatcher.group(1));
        assertEquals("vinomudok", urlMatcher.group(2));

        // Title matcher
        Matcher titleMatcher = STREAM_TITLE_PATTERN.matcher(discordMessage);
        assertTrue(titleMatcher.find(), "Title should match");
        String title = titleMatcher.group(1).trim().replaceAll("^\\*+|\\*+$", "").trim();
        assertEquals("Ищем людей на NovaVanilla !ip", title);
    }

    @Test
    void testMarkdownBoldTitleParsing() {
        String discordMessage = """
                название стрима: **Ищем людей на NovaVanilla !ip**
                ссылка на стрим: https://www.twitch.tv/shroud
                """;

        Matcher titleMatcher = STREAM_TITLE_PATTERN.matcher(discordMessage);
        assertTrue(titleMatcher.find(), "Markdown bold title should match");
        String title = titleMatcher.group(1).trim().replaceAll("^\\*+|\\*+$", "").trim();
        assertEquals("Ищем людей на NovaVanilla !ip", title);
    }

    @Test
    void testMiniMessageSafetyWithSpecialChars() {
        String template = "<gray>Стример <gradient:#9146FF:#6441A5><bold><streamer></bold></gradient> <white>запустил стрим</white> <yellow><title></yellow> - <click:open_url:'<url>'><hover:show_text:'<gray>Нажмите, чтобы открыть Twitch'><underlined><color:#A970FF><url></color></underlined></hover></click></gray>";

        String streamer = "VinoMudok";
        String titleWithSpecialChars = "Ищем людей на NovaVanilla !ip <test> & special 'quotes'";
        String url = "https://www.twitch.tv/vinomudok";

        String prepared = template
                .replace("<url>", url)
                .replace("%url%", url);

        Component component = MiniMessage.miniMessage().deserialize(
                prepared,
                Placeholder.component("streamer", Component.text(streamer)),
                Placeholder.unparsed("streamer", streamer),
                Placeholder.component("title", Component.text(titleWithSpecialChars)),
                Placeholder.unparsed("title", titleWithSpecialChars)
        );

        assertNotNull(component);
        // Verify component serialization contains the unparsed title safely
        String serialized = MiniMessage.miniMessage().serialize(component);
        assertTrue(serialized.contains("NovaVanilla !ip"));
    }
}
