# Архитектура BOSSgram: как пережить обновление Telegram

## Почему не TDLib / Xposed / APK-патчер

- **TDLib с нуля**: обновления не ломают, но это не "модуль к оф. клиенту". Пишешь весь UI сам. Месяцы работы. Твои хотелки (темы, анти-удаление) — быстрее на форке.
- **Xposed/LSPosed**: реально drop-in без пересборки, но нужен root, каждый релиз Telegram ломает хуки по обфусцированным именам, в Play такое не выложишь.
- **APK-патчер (ReVanced-стайл)**: работает без исходников, но dex-патчи хрупкие, отладка ад, фичи сложнее анти-удаления с UI — боль.
- **Выбрано: fork-sync + изолированный `bossgram/`**. Компромисс: пересборка 5 минут на релиз, зато полный доступ к UI/БД, нормальная отладка, можно в Play (со своим api_id, иконкой, названием — требует Telegram).

## Слой касания (только это правится в upstream)

Всего 3 точки. Все остальное — свой код.

1. `org.telegram.messenger.ApplicationLoader#onCreate`
   ```java
   com.bossgram.core.BossHooks.init(this);
   ```
2. `org.telegram.messenger.MessagesController#deleteMessages` (или `deleteMessagesByIds`)
   ```java
   com.bossgram.core.BossHooks.onMessagesDeleted(dialogId, messageIds, messages);
   ```
   Точное место зависит от версии — смотри `patches/0001-boss-hooks.patch`, там контекст ±3 строки. При конфликте правишь только эту строку.
3. `org.telegram.ui.ChatActivity#onViewCreated` / `createView`
   ```java
   com.bossgram.core.BossHooks.onChatOpened(this);
   ```
   Нужно для бейджа "сохранено" и кнопки очистки.

Больше ничего в `org.telegram.*` не трогать. Проверяется скриптом `tools/check-touch-points.py`.

## Жизнь модуля

```java
public interface BossModule {
    String id();                       // "antidelete", "themes"
    void onInit(BossContext ctx);      // регистрация, слушатели
    void onDestroy();
}
```

`ModuleRegistry` держит список. `BossHooks.init()` проходит по нему. Новый модуль = 1 папка + 1 строка регистрации. Upstream про него ничего не знает.

## AntiDelete: поток данных

```
Telegram вызывает deleteMessages
  -> BossHooks.onMessagesDeleted(dialogId, ids)
    -> ChatFilter.shouldSave(dialogId) ? (allowlist/blocklist + тип: user/chat/channel)
    -> AntiDeleteStore.save(snapshot) (v1: JSON в файлы + SharedPrefs индекс, без правок MessagesStorage)
    -> UI: тост/бейдж через onChatOpened
Экран "Сохраненные": свой Fragment в bossgram, открывается из настроек BossGram.
Очистка: по чату / по возрасту / по размеру, кнопка "Очистить все".
```

Почему не правим `MessagesStorage` сразу: схема БД Telegram меняется, миграция убьет обновление. v1 — sidecar-хранилище. v2 (опционально) — свой Room рядом.

## Themes: поток

Не правим `Theme.java`. Храним палитру в `bossgram_themes.xml` + `SharedPreferences`. `BossHooks.applyCustomTheme()` вызывается после `Theme.createChatResources()`, перекрашивает через публичные `Theme.setColor()` / `SharedConfig`. Ломается редко.

## Что ломается при обновлении и где чинить

- Переименовали `deleteMessages` -> правишь 1 строку в `BossHooks` + контекст патча. Свои модули не трогаешь.
- Поменяли `ChatActivity` -> правишь только `onChatOpened`.
- Поменяли БД -> тебя не касается (sidecar).
