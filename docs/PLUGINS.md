# Плагины BOSSgram: аудит, статусы, дорога

Исходники: `I:\ai\projects\BOSSgram\plugins` (вне репо). В репо (`plugins/`) вендорятся
ТОЛЬКО прошедшие аудит. Блобам и обфускации — отказ без исключений.

## Вердикты аудита (Oct 2026, 9 файлов, 4273 строки)

| Плагин | Вердикт | Причина |
|---|---|---|
| `AdBlock` 1.1 @kvucoPlugins | ВЗЯТ | Чистый adblock: отмена `TL_help_getPromoData`, no-op хуки promo/VideoAds. Сети наружу нет, секретов нет |
| `accounts_limit_16` 1.0 | ВЗЯТ | Хук `UserConfig.hasPremiumOnAccounts` + проверка стека. Ссылка на ExteraGram-класс безвредна (просто не матчится) |
| `advanced_chat_search` 1.0.0 | ВЗЯТ | Глобальный поиск юзеров через штатный `TL_contacts_search`, мерж в MentionsAdapter. Сеть только Telegram API |
| `always-tabs-forums` 1.0.3 | ВЗЯТ | Подмена `ChatObject.areTabsEnabled`. 50 строк, чисто |
| `account_age_checker` 5.2 | ВЗЯТ С ОГОВОРКОЙ | Логика чистая, но хост — ExteraGram SDK (`PluginsController`, `ui.*`) + платный внешний API `api.datereg.pro` (ключ вводит юзер). Порт требует адаптера |
| `ai_tools_beta` 1.0.0 @AGeekApple | ОТКЛОНЕН: ВРЕДОНОС | Однобуквенная обфускация (`k/u/g/f`), скрытый base64 URL `https://rutube.ru/play/embed/.../?autoplay=1`, fullscreen WebView + JS + автоплей под видом "AI Tools". Кликфрод. НЕ запускать, НЕ вендорить |
| `account_hider` 1.0.1 | ОТКЛОНЕН: БЛОБ | `DEX_B64` + `InMemoryDexClassLoader`, содержимое непроверяемо. Только с исходниками |
| `add_to_folder` 1.0 | ОТКЛОНЕН: БЛОБ | Python-часть чистая, но хвост `__DEX_BEGIN__` (zlib+base64, содержимое неизвестно). Только без блоба |
| `anti_spoiler` 2.0 | ОТКЛОНЕН: БЛОБ | SHA-256 пиннинг — хорошая практика, но пейлоад непроверяем. Только с исходниками |

## Почему не "просто запустить .py"

Плагины писаны под ТРИ разных хост-SDK, которых нет в BOSSgram:
`base_plugin/hook_utils/android_utils` (kvuco), `com.exteragram.messenger.plugins.*` (ExteraGram),
`client_utils/file_utils/ui.*`. Плюс нужен Python-рантайм в APK (Chaquopy, +десятки МБ, свой NDK-билд).
Это отдельная большая задача, не один коммит.

## Дорога (честно)

1. ✅ Аудит + вендор чистых + статусы в настройках (этот коммит)
2. Нативные порты дешевого: `AdBlock` = 1 хук на `ConnectionsManager.sendRequest` + no-op `checkPromoInfo`
   (оценка: +1 хук в патч 0001, +1 BossModule)
3. Хост-рантайм Python (Chaquopy) + адаптеры SDK — отдельным проектом, после стабильности ядра

## Политика добавления новых плагинов

1. `security-check.py` + ручной разбор ВСЕГО файла
2. Запрет: base64/zlib блобы, `DexClassLoader`, обфусцированные URL, WebView+JS наружу, отправка данных с устройства
3. Сомнительное — в карантин, в репо не вендорить, в отчете фиксировать причину
