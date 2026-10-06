# Обновление BOSSgram на новую версию Telegram

Цель: 5 минут, без разбора "что сломалось везде".

## Разово (уже сделано в этом репо)

```powershell
git remote add upstream https://github.com/DrKLO/Telegram.git
```

## Каждый релиз Telegram

```powershell
# PowerShell
.\tools\sync-upstream.ps1
# или bash
# ./tools/sync-upstream.sh
```

Скрипт делает:

1. `git fetch upstream`
2. показывает тег/коммит upstream (`git ls-remote upstream`)
3. `git rebase upstream/master` (или на тег: `.\tools\sync-upstream.ps1 -Ref v12.1.0`)
4. `git apply --check patches/0001-boss-hooks.patch`, если хуки не в истории
5. `python tools/check-touch-points.py` — ругается если свой код вылез из `bossgram/`
6. напоминает скопировать `bossgram/` в сборку и собрать

## Если rebase встал на конфликте

99% конфликт будет в 1 из 3 хуков. Чинишь так:

```powershell
git status # покажет файл, напр. TMessagesProj/.../MessagesController.java
# открыть, найти <<<<<<<, оставить обе части: код Telegram + 1 строку BossHooks
git add <файл>
git rebase --continue
```

Свой код в `bossgram/` при этом не трогаешь.

## Проверка после обновления

```powershell
python tools/check-touch-points.py
python tools/security-check.py
# OK: upstream touched only by hooks, blockers 0
```

## Сборка (проверено на Windows, Oct 2026)

Нужно: Java 17, SDK с `platforms;android-36` + `build-tools;36.0.0`, `ndk;27.2.12479018`,
`cmake;3.22.1` (докинь через sdkmanager), Gradle 8.13 (в репо только unix `gradlew`,
под Windows качай `gradle-8.13-bin.zip`). В корне сборки создать `local.properties`
(gitignored, в коммит не идет):

```
sdk.dir=C\:\\Users\\boss\\AppData\\Local\\Android\\Sdk
```

```powershell
# 1. быстрая проверка Java (без native): должны скомпилиться com.bossgram.* + хуки
gradle :TMessagesProj:compileDebugJavaWithJavac
# 2. AAR библиотеки (~25 мин первый раз, native)
gradle :TMessagesProj:assembleDebug
# 3. APK (~3 мин): TMessagesProj_App/build/outputs/apk/afat/debug/app.apk
gradle :TMessagesProj_App:assembleDebug
```

Проверка что наш код в APK: классы `com/bossgram/...` в `classes*.dex`
(`tools/` скрипт проверки — следующий шаг). Smoke: открыть чат, удалить сообщение,
проверить сохранение + очистку. Полный регламент: `docs/SECURITY.md`.

## Правила BuildVars / ключи (важно, иначе не соберется)

Telegram требует свои `api_id`, `release.keystore`, `google-services.json`, `BuildVars.java`. Это не часть BOSSgram-логики, это сборка. Храни вне гита:

- `TMessagesProj/config/release.keystore` (у репо dummy)
- `gradle.properties`: `RELEASE_KEY_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_STORE_PASSWORD`
- `TMessagesProj/google-services.json`
- `BuildVars.java`: свой api_id с https://core.telegram.org/api/obtaining_api_id

Никогда не коммить ключи. Для Play: другое название (не Telegram), другая иконка (не бумажный самолетик), публикуй код (GPL-2.0).
