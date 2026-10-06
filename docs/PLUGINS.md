# Плагины BOSSgram: аудит и нативные порты

Исходники Python-плагинов: `I:\ai\projects\BOSSgram\plugins` (вне репо) + `plugins/plugin-need` (chat_summary, chatexport, compact_text).
Они писаны под ЧУЖИЕ хост-SDK (kvuco `base_plugin`, ExteraGram `PluginsController`) —
запустить `.py` напрямую нельзя: нет ни рантайма, ни SDK. Поэтому портируем в натив.
В репо (`plugins/`) вендорятся ТОЛЬКО прошедшие аудит.

## Аудит (Oct 2026)

| Плагин | Вердикт |
|---|---|
| `AdBlock` 1.1 @kvucoPlugins | ЧИСТ → портирован в натив |
| `accounts_limit_16` 1.0 | ЧИСТ → портирован в натив |
| `advanced_chat_search` 1.0.0 | ЧИСТ → портирован в натив |
| `always-tabs-forums` 1.0.3 | ЧИСТ → портирован в натив |
| `account_age_checker` 5.2 | Логика чиста, ExteraGram UI + paid API `datereg.pro` → портировано ядро (free-lookup) |
| `ai_tools_beta` 1.0.0 @AGeekApple | ВРЕДОНОС: однобуквенная обфускация, скрытый base64 `https://rutube.ru/play/embed/.../?autoplay=1`, fullscreen WebView + JS под видом "AI Tools" (кликфрод). НЕ вендорить, НЕ запускать |
| `account_hider` 1.0.1 | БЛОБ `DEX_B64` отклонен → функция переписана: `bossgram/modules/accounthider` |
| `add_to_folder` 1.0 | БЛОБ `__DEX_BEGIN__` отклонен → функция переписана: `bossgram/modules/addtofolder` |
| `anti_spoiler` 2.0 | БЛОБ (SHA-пиннед DEX) отклонен → функция переписана: `bossgram/modules/antispoiler` |
| `chat_summary` 2.0.0 @aaxnet (plugin-need) | ЧИСТ: AI-вызовы только на выбранный юзером endpoint (OpenAI/Anthropic/Gemini/Ollama/custom), ключ в локальных prefs, без DEX/WebView. → `bossgram/modules/chatsummary` |
| `chatexport` 2.0 @mihailkotovski (plugin-need) | DEX-ЯДРО ОТКЛОНЕНО (`InMemoryDexClassLoader`, `chatexport.dex`, перехват openForView) → функция переписана без DEX: `TL_messages_getHistory` + локальные HTML/JSON/TXT. → `bossgram/modules/chatexport` |
| `compact_text` 1.0.5 @incLu_01 (plugin-need) | ЧИСТ: локальная упаковка длинных текстов в файл (TXT/DOCX), `.cpt` команда, без DEX/сети. → `bossgram/modules/compacttext` |

## Нативные порты

| Модуль | Хуки / API | Управление |
|---|---|---|
| `adblock` (патч 0004) | `checkPromoInfo`, `checkPromoInfoInternal`, `isPromoDialog`, `VideoAds.load/schedule/show/isPopupShown` (7) | тумблер, по умолчанию ВКЛ |
| `accountlimit` (патч 0004) | `UserConfig.hasPremiumOnAccounts` + stack-check 3 UI-классов | тумблер, по умолчанию ВЫКЛ |
| `forumtabs` (патч 0004) | `ChatObject.areTabsEnabled` | тумблер, по умолчанию ВЫКЛ |
| `globalsearch` (патч 0004) | `setSearchingMentions`, `searchUsernameOrHashtag`, `showUsersResult` (мерж в scope, без рефлексии) | тумблер, по умолчанию ВЫКЛ |
| `accountage` (патч 0004) | без хуков: `TL_contacts_resolveUsername` + `TL_photos_getUserPhotos` по кнопке | тумблер + «Проверить @username» |
| `accounthider` (патч 0003) | фильтр списков в 3 пикерах | чекбоксы аккаунтов |
| `addtofolder` (патч 0003) | 1 хук в `updateCounters` | тумблер |
| `antispoiler` (патч 0003) | 1 хук в конце `setMessageContent`: флаги + очистка `SpoilerEffect` из блоков (флагов одних мало — эффекты пекутся при layout) | тумблер |
| `chatsummary` (без хуков) | `TL_messages_getHistory` + `HttpURLConnection` к выбранному AI endpoint по кнопке | тумблер (ВКЛ) + «Сделать саммари» + настройки провайдера |
| `chatexport` (без хуков) | `TL_messages_getHistory` постранично, локальные HTML/JSON/TXT | тумблер (ВКЛ) + «Экспортировать чат» |
| `compacttext` (патч 0005) | 1 хук в начале `SendMessagesHelper.sendMessage`: длинные → файл TXT/DOCX, `.cpt` форсит | тумблер (ВКЛ) + порог |
| `showid` (патч 0005) | `ProfileActivity.updateProfileData` + `ChatActivity.updateTitle` (только отображение) + `TL_contacts_resolveUsername` lookup | тумблер (ВКЛ) + «Узнать ID по @username», мой ID всегда виден |

Возраст = нижняя граница по дате старейшего фото профиля (аккаунт не моложе своего фото).
Показываем: имя, ID, @username, DC, число фото, старейшее фото, оценка.

## Почему не "просто запустить .py"

Три разных хост-SDK + нужен Python-рантайм в APK (Chaquopy, +десятки МБ). Отдельный проект.

## Политика новых плагинов

1. Полный разбор файла + `security-check.py`, ноль BLOCKER
2. Запрет: base64/zlib блобы, `DexClassLoader`, обфусцированные URL, WebView+JS наружу, отправка данных с устройства
3. Сомнительное — в отчет, в код не вендорить
