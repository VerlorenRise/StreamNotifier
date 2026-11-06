package ru.streamnotifier;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class ParsingTest {

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

    private String cleanTitle(String raw) {
        return raw.replaceAll("^[\\*`_>~\\s]+|[\\*`_>~\\s]+$", "").trim();
    }

    private String cleanUrl(String raw) {
        return raw.replaceAll("[<>\\s]", "").trim();
    }

    @Test
    void testStandardDiscordMessageParsing() {
        String discordMessage = """
                название стрима: Ищем людей на NovaVanilla !ip
                ссылка на стрим: https://www.twitch.tv/vinomudok
                """;

        Matcher urlMatcher = STREAM_LABEL_URL_PATTERN.matcher(discordMessage);
        assertTrue(urlMatcher.find(), "Twitch URL should match");
        assertEquals("https://www.twitch.tv/vinomudok", cleanUrl(urlMatcher.group(1)));
        assertEquals("vinomudok", urlMatcher.group(2));

        Matcher titleMatcher = STREAM_TITLE_PATTERN.matcher(discordMessage);
        assertTrue(titleMatcher.find(), "Title should match");
        assertEquals("Ищем людей на NovaVanilla !ip", cleanTitle(titleMatcher.group(1)));
    }

    @Test
    void testBotMarkdownFormatting() {
        String botMessage = """
                **название стрима:** Ищем людей на NovaVanilla !ip
                **ссылка на стрим:** https://www.twitch.tv/vinomudok
                """;

        Matcher urlMatcher = STREAM_LABEL_URL_PATTERN.matcher(botMessage);
        assertTrue(urlMatcher.find(), "Bot Twitch URL with markdown asterisks should match");
        assertEquals("https://www.twitch.tv/vinomudok", cleanUrl(urlMatcher.group(1)));
        assertEquals("vinomudok", urlMatcher.group(2));

        Matcher titleMatcher = STREAM_TITLE_PATTERN.matcher(botMessage);
        assertTrue(titleMatcher.find(), "Title should match");
        assertEquals("Ищем людей на NovaVanilla !ip", cleanTitle(titleMatcher.group(1)));
    }

    @Test
    void testBotEmbedAndEmojisFormatting() {
        String botMessage = """
                🔴 **Название стрима:** **Ищем людей на NovaVanilla !ip**
                🎮 **Ссылка на стрим:** <https://www.twitch.tv/vinomudok>
                """;

        Matcher urlMatcher = STREAM_LABEL_URL_PATTERN.matcher(botMessage);
        assertTrue(urlMatcher.find(), "URL with brackets and emojis should match");
        assertEquals("https://www.twitch.tv/vinomudok", cleanUrl(urlMatcher.group(1)));
        assertEquals("vinomudok", urlMatcher.group(2));

        Matcher titleMatcher = STREAM_TITLE_PATTERN.matcher(botMessage);
        assertTrue(titleMatcher.find(), "Title with emojis and bolding should match");
        assertEquals("Ищем людей на NovaVanilla !ip", cleanTitle(titleMatcher.group(1)));
    }

    @Test
    void testFallbackAnyTwitchUrl() {
        String messageWithoutLabel = "Shroud is live now playing games! Check it out at https://www.twitch.tv/shroud";

        Matcher anyMatcher = TWITCH_ANY_URL_PATTERN.matcher(messageWithoutLabel);
        assertTrue(anyMatcher.find(), "Any twitch URL should be caught as fallback");
        assertEquals("https://www.twitch.tv/shroud", anyMatcher.group(0));
        assertEquals("shroud", anyMatcher.group(1));
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
        String serialized = MiniMessage.miniMessage().serialize(component);
        assertTrue(serialized.contains("NovaVanilla !ip"));
    }
}
