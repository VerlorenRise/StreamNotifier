# StreamNotifier

Легковесный плагин для Minecraft-серверов на базе ядра **Purpur / Paper** (поддержка версии 26.2 / 1.21+, Java 21), предназначенный для автоматического перехвата анонсов стримов из Discord через плагин **DiscordSRV** (1.30.5) и отправки красивых оповещений в глобальный чат игры.

## 🚀 Особенности
- **Интеграция с DiscordSRV без дублирования бота:** Плагин не запускает собственный инстанс Discord-бота, а безопасно подписывается на события через внутренний JDA DiscordSRV (`github.scarsz.discordsrv.dependencies.jda.api...`).
- **Корректный жизненный цикл:** Отслеживание готовности через `DiscordReadyEvent`, регистрация слушателя при старте/перезагрузке и безопасная отписка при выключении (`onDisable`).
- **Автоматический парсинг сообщений Discord:** Извлечение названия стрима и ссылки на Twitch напрямую из текста сообщения:
  ```text
  название стрима: Ищем людей на NovaVanilla !ip
  ссылка на стрим: https://www.twitch.tv/vinomudok
  ```
- **Защита от дублей:** Встроенный кулдаун по логину стримера (`cooldown-seconds`).
- **Интеллектуальное сопоставление ников (по приоритету):**
  1. Ручной маппинг в `config.yml` (`twitch-to-minecraft`).
  2. Сохранение красивого регистра букв никнейма из заголовка Embed (`^(.*?) запустил вещание на Twitch!`).
  3. Фоллбек на логин канала Twitch из ссылки.
- **Современный Adventure MiniMessage:** Поддержка градиентов, цветов, названий стрима с безопасным экранированием спецсимволов (`Placeholder.unparsed`), кликабельных ссылок (`<click:open_url:'...'>`) и всплывающих подсказок (`<hover:show_text:'...'>`).
- **Синхронизация с сервером:** Отправка сообщений в игровой чат строго в главном потоке сервера через `BukkitScheduler`.
- **Команды администрирования:** Перезагрузка конфигурации на лету (`/streamnotifier reload`) и тестовая отправка (`/streamnotifier test <twitch_login> [название стрима]`).

---

## ⚙️ Структура проекта
```text
StreamNotifier/
├── pom.xml                                           # Сборка Maven (Purpur 26.2, DiscordSRV 1.30.5)
└── src/
    └── main/
        ├── resources/
        │   ├── plugin.yml                            # Дескриптор плагина (depend: [DiscordSRV])
        │   └── config.yml                            # Конфигурация по умолчанию
        └── java/
            └── ru/streamnotifier/
                ├── StreamNotifierPlugin.java         # Главный класс плагина
                ├── config/
                │   └── PluginConfig.java             # Загрузка и валидация конфига
                ├── service/
                │   └── StreamNotificationService.java# Безопасная сборка MiniMessage и рассылка
                ├── discord/
                │   └── DiscordListener.java          # JDA ListenerAdapter для сообщений Discord
                └── command/
                    └── StreamNotifierCommand.java    # Обработчик /streamnotifier [reload|test]
```

---

## 🛠️ Сборка плагина
Для сборки требуется **Java 21**:
```bash
mvn clean package
```
Собранный jar-файл будет находиться в `target/StreamNotifier-1.0.0.jar`.

---

## 📋 Конфигурация (`config.yml`)
```yaml
# ID текстового канала в Discord, куда бот/вебхук присылает уведомления о стримах.
stream-channel-id: "123456789012345678"

# Кулдаун (в секундах) между повторными оповещениями одного и того же стримера.
cooldown-seconds: 180

# Регулярное выражение для извлечения имени стримера из заголовка Discord Embed.
embed-title-regex: "^(.*?) запустил вещание на Twitch!"

# Название стрима по умолчанию, если оно не указано или не найдено.
default-stream-title: "Ищем людей на NovaVanilla !ip"

# Ручное сопоставление логинов Twitch с никами игроков в Minecraft (Шаг 1).
twitch-to-minecraft:
  vinomudok: "VinoMudok"
  shroud: "Shroud"

# Формат сообщения в чате Minecraft (Adventure MiniMessage).
message-format: "<gray>Стример <gradient:#9146FF:#6441A5><bold><streamer></bold></gradient> <white>запустил стрим</white> <yellow><title></yellow> - <click:open_url:'<url>'><hover:show_text:'<gray>Нажмите, чтобы открыть Twitch'><underlined><color:#A970FF><url></color></underlined></hover></click></gray>"
```

---

## 🎮 Команды и права
| Команда | Описание | Право |
|---|---|---|
| `/streamnotifier reload` | Перезагрузить файл `config.yml` | `streamnotifier.admin` (по умолчанию `op`) |
| `/streamnotifier test <twitch_login> [название стрима]` | Отправить тестовое сообщение о стриме в глобальный чат | `streamnotifier.admin` (по умолчанию `op`) |
