# BOSSgram — модульная надстройка над Telegram Android

База: `DrKLO/Telegram` как `upstream`. Свой код изолирован. Обновление = `fetch + rebase`, 5 минут.

## Правда про "модуль к оф. клиенту"

DrKLO/Telegram — монолит, plugin ABI нет. Внутренние классы (`MessagesController`, `ChatActivity`, `MessagesStorage`) меняются каждый релиз.

100% drop-in `.so`/`.apk-модуль` без форка невозможен. Реально достижимо:

- форк + `upstream` remote
- свой код только в `bossgram/`, чужие файлы не трогать
- касание upstream: 5 однострочных хуков (`patches/0001-boss-hooks.patch`, `git apply --check` зелен)
- обновление: `tools/sync-upstream.ps1`, конфликты только в хуках
- настройки: диплинк `bossgram://settings` → `BossSettingsActivity` (без правок UI Telegram)

Так делают Nekogram, Catogram, etc.

## Структура

```
BOSSgram/
  bossgram/                  # ТОЛЬКО сюда пиши свой код
    api/
      BossModule.java        # интерфейс модуля
      BossContext.java       # prefs,Intl, навигация
    core/
      BossHooks.java         # единая точка входа, вызывается из 3 мест upstream
      ModuleRegistry.java    # регистрация модулей
    modules/
      antidelete/            # сохранение удаленных + фильтр по чатам + очистка
        AntiDeleteModule.java
        ChatFilter.java
        AntiDeleteStore.java
        BossSettingsActivity.java  # экран настроек BossGram
        BossSavedActivity.java     # просмотр сохраненных + очистка
      themes/                # UI/темы-заглушка
        ThemesModule.java
  patches/
    0001-boss-hooks.patch    # 5 однострочных вставок в upstream, больше ничего
  tools/
    sync-upstream.ps1/.sh    # обновление на новую версию Telegram
    check-touch-points.py    # контроль: свой код не расползся по upstream
  docs/
    ARCHITECTURE.md
    UPDATE.md
```

## Быстрый старт

```powershell
# 1. клонировать ВМЕСТЕ с upstream-историей (у тебя уже есть remote upstream)
git clone --recursive https://github.com/BOSSincrypto/BOSSgram.git
cd BOSSgram
git remote add upstream https://github.com/DrKLO/Telegram.git # если нет

# 2. подтянуть исходники Telegram в worktree рядом (не в этот репо мусором):
# вариант A: держать Telegram как отдельную папку для сборки
git clone --recursive --depth=1 https://github.com/DrKLO/Telegram.git ../Telegram-upstream

# 3. накатить хуки
git -C ../Telegram-upstream apply ../BOSSgram/patches/0001-boss-hooks.patch
# + скопировать bossgram/ в ../Telegram-upstream/TMessagesProj/src/main/java/com/bossgram/

# 4. собрать как обычный Telegram (api_id, google-services.json, BuildVars.java)
```

## Проверка перед каждым коммитом (обязательно)

```powershell
python tools/check-touch-points.py
python tools/security-check.py
git status --short   # только intended файлы
git diff             # прочитать весь diff глазами
```

Полный регламент: `docs/SECURITY.md` (секреты, бэкдоры, уязвимости, баги). Ноль BLOCKER — иначе не коммитим.

Полно: `docs/ARCHITECTURE.md`, обновление: `docs/UPDATE.md`.

## Правила чтобы обновление не болело

1. Не редактируй `org.telegram.*` кроме 3 хуков из патча.
2. Новый функционал = новый класс в `bossgram/modules/<name>/`.
3. Общайся с Telegram только через `BossHooks` + `BossContext`.
4. Никаких копипаст больших кусков upstream в свой код — сломается на следующем релизе.
5. GPL-2.0: публикуй код форка.

## Первые модули (по твоему ТЗ)

- `antidelete`: перехват `deleteMessages`, фильтр `ChatFilter` (какие чаты сохранять), экран очистки.
- `themes`: свой пункт в Настройки, палитры поверх `Theme.java`, без правок `Theme.java` — через `SharedPreferences` + `BossHooks.applyCustomTheme()`.
