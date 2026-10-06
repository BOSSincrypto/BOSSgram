# Плагины BOSSgram: аудит и нативные порты

Исходники Python-плагинов: `I:\ai\projects\BOSSgram\plugins` (вне репо, 9 файлов).
Они писаны под ЧУЖИЕ хост-SDK (kvuco `base_plugin`, ExteraGram `PluginsController`) —
запустить `.py` напрямую нельзя: нет ни рантайма, ни SDK. Поэтому портируем в натив.

## Аудит (Oct 2026)

| Плагин | Вердикт |
|---|---|
| `AdBlock` 1.1 | ЧИСТ → портирован |
| `accounts_limit_16` 1.0 | ЧИСТ → портирован |
| `advanced_chat_search` 1.0.0 | ЧИСТ → портирован |
| `always-tabs-forums` 1.0.3 | ЧИСТ → портирован |
| `account_age_checker` 5.2 | Логика чиста, ExteraGram UI + paid API → портировано ядро (free-lookup) |
| `ai_tools_beta` 1.0.0 | ВРЕДОНОС: скрытый `rutube...autoplay=1` в fullscreen WebView с JS. НЕ вендорить, НЕ запускать |
| `account_hider`, `add_to_folder`, `anti_spoiler` | БЛОБЫ: `DEX_B64`/`__DEX_BEGIN__`/SHA-пиннед DEX без исходников. Функции переписаны в натив отдельно |

## Нативные порты (патч `0004-boss-plugin-ports.patch`)

| Модуль | Хуки | Управление |
|---|---|---|
| `adblock` | `checkPromoInfo`, `checkPromoInfoInternal`, `isPromoDialog`, `VideoAds.load/schedule/show/isPopupShown` (7) | тумблер, по умолчанию ВКЛ |
| `accountlimit` | `UserConfig.hasPremiumOnAccounts` + stack-check 3 UI-классов | тумблер, по умолчанию ВЫКЛ |
| `forumtabs` | `ChatObject.areTabsEnabled` | тумблер, по умолчанию ВЫКЛ |
| `globalsearch` | `setSearchingMentions`, `searchUsernameOrHashtag`, `showUsersResult` (мерж в scope, без рефлексии) | тумблер, по умолчанию ВЫКЛ |
| `accountage` | без хуков: `TL_contacts_resolveUsername` + `TL_photos_getUserPhotos` по кнопке | тумблер + «Проверить @username» |

Возраст = нижняя граница по дате старейшего фото профиля (аккаунт не моложе своего фото).
Показываем: имя, ID, @username, DC, число фото, старейшее фото, оценка.

## Политика новых плагинов

1. Полный разбор файла + `security-check.py`, ноль BLOCKER
2. Запрет: base64/zlib блобы, `DexClassLoader`, обфусцированные URL, WebView+JS наружу, отправка данных с устройства
3. Сомнительное — в отчет, в код не вендорить
