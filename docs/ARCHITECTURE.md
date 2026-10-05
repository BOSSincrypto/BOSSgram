# Архитектура BOSSgram: как пережить обновление Telegram

## Почему не TDLib / Xposed / APK-патчер

- **TDLib с нуля**: обновления не ломают, но это не "модуль к оф. клиенту". Пишешь весь UI сам. Месяцы работы. Твои хотелки (темы, анти-удаление) — быстрее на форке.
- **Xposed/LSPosed**: реально drop-in без пересборки, но нужен root, каждый релиз Telegram ломает хуки по обфусцированным именам, в Play такое не выложишь.
- **APK-патчер (ReVanced-стайл)**: работает без исходников, но dex-патчи хрупкие, отладка ад, фичи сложнее анти-удаления с UI — боль.
- **Выбрано: fork-sync + изолированный `bossgram/`**. Компромисс: пересборка 5 минут на релиз, зато полный доступ к UI/БД, нормальная отладка, можно в Play (со своим api_id, иконкой, названием — требует Telegram).

## Слой касания (только это правится в upstream)

Всего 5 однострочников (`patches/0001-boss-hooks.patch`, проверен `git apply --check` на master Oct 2026).
Все остальное — свой код.

1. `org.telegram.messenger.ApplicationLoader#onCreate` после `super.onCreate()`
   ```java
   com.bossgram.core.BossHooks.init(this);
   ```
2. `MessagesController#deleteMessages` полная 12-арг версия (строка ~9319), первая строка метода.
   Все перегрузки стекаются сюда — одна точка покрывает свои удаления:
   ```java
   com.bossgram.core.BossHooks.onOwnMessagesDeleted(currentAccount, dialogId, messages);
   ```
3. `MessagesController#processUpdateArray` в `if (deletedMessages != null)` — чужие удаления (собеседник/с сервера), ДО постановки в storage queue, память еще читаема:
   ```java
   com.bossgram.core.BossHooks.onRemoteMessagesDeleted(currentAccount, deletedMessages);
   ```
   Ключи map: `-channelId` для каналов напрямую, `0` для личек (dialog резолвится по каждому id через `dialogMessagesByIds` + `MessageObject.getDialogId()`, нерезолвимое пропускается).
4. `org.telegram.ui.ChatActivity#onResume` после `super.onResume()` — зарезервировано под бейдж:
   ```java
   com.bossgram.core.BossHooks.onChatOpened(this);
   ```
5. `org.telegram.ui.LaunchActivity#onNewIntent` после `super.onNewIntent()` — вход в настройки без хирургии UI:
   ```java
   if (com.bossgram.core.BossHooks.onNewIntent(this, intent)) return;
   ```
   Открывает `BossSettingsActivity` по диплинку `bossgram://settings`.

Снапшот текста: только из памяти (`dialogMessagesByIds` + `dialogMessage`), fallback `""`.
Запись в sidecar — на `MessagesStorage` queue, не на UI-потоке. Хуки никогда не кидают наружу.

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

- Переименовали `deleteMessages` / `processUpdateArray` -> правишь 1 строку в `BossHooks` + контекст патча. Свои модули не трогаешь.
- Поменяли `ChatActivity.onResume` / `LaunchActivity.onNewIntent` -> правишь только хук.
- Поменяли БД -> тебя не касается (sidecar).
- Поменяли `SettingsActivity`/ячейки -> тебя не касается (свои экраны на стабильных `TextCheckCell`/`TextSettingsCell`/`HeaderCell`).

## Как открыть настройки

Диплинк `bossgram://settings` (обрабатывает хук 5/5). Отправить себе в Избранное ссылку `bossgram://settings` и тапнуть.
Экран: `BossSettingsActivity` — тумблер, режим списка, ID чатов, лимиты, "Сохраненные" (`BossSavedActivity`: просмотр + очистка по чату/всё).
