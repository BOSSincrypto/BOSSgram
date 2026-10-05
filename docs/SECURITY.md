# Регламент проверок BOSSgram: безопасность, баги, бэкдоры, уязвимости

Обязательно перед каждым коммитом и перед каждым обновлением upstream. Без исключений.

## 1. Секреты (блокер)

- `git diff --stat` + `git status`: в коммит только intended файлы.
- Запрещено в гите: `api_id`, `release.keystore`, `*.jks`, `google-services.json`, `BuildVars.java` с реальными ключами, токены, пароли, `gradle.properties` с `RELEASE_*_PASSWORD`.
- Поиск: `tools/security-check.py` (паттерны ключей/токенов).

## 2. Бэкдоры (блокер)

Проверять весь diff руками + скриптом. Красные флаги:

- `DexClassLoader`, `PathClassLoader`, загрузка dex/jar/so из сети или Downloads.
- `Runtime.getRuntime().exec(`, `ProcessBuilder`, shell-команды.
- Сетевые отправки на чужие хосты: `HttpURLConnection`, `OkHttp`, `fetch`, webhook URL в коде.
- `addJavascriptInterface`, `setJavaScriptEnabled(true)` + загрузка remote URL в WebView.
- Новые `RECEIVER`/`SERVICE` с `exported=true`, `REQUEST_INSTALL_PACKAGES`, `READ_SMS`, `BIND_DEVICE_ADMIN`.
- Шифрование/обфускация строк без причины, base64-блобы, reflection по скрытым именам (`getDeclaredMethod`, `setAccessible`) вне `BossHooks`.
- Код, собирающий сообщения/контакты и пишущий не в sidecar-хранилище модуля.

Правило: любой такой код только с явным комментарием ЗАЧЕМ + запись в PR. Молча — reject.

## 3. Уязвимости (блокер)

- Экспортированные Activity/Service/Receiver/Provider: проверить `android:exported`, intent-фильтры, валидацию входящих extras.
- WebView: только локальный контент, никакого JS-моста наружу.
- Хранилище анти-удаления: приватные файлы приложения (`MODE_PRIVATE`), никаких внешних стореджей, никаких логов с текстом сообщений (`FileLog`, `Log.d`).
- SQL: только параметризованные запросы, никаких конкатенаций.
- `PendingIntent`: explicit intent + `FLAG_IMMUTABLE` где возможно.

## 4. Баги (обязательно)

- `python tools/check-touch-points.py` — свой код только в `bossgram/`.
- `python tools/security-check.py` — ноль BLOCKER.
- Null-safety на границах хуков: Telegram вызывает хуки с null/пустыми списками — модуль обязан молча return, не крашить клиент.
- Хуки никогда не бросают исключения наружу: весь код модулей в try/catch, upstream не должен падать из-за BOSSgram.
- Проверить жизненный цикл: `onInit` один раз, `onDestroy` чистит слушатели.
- После rebase upstream: сборка `assembleAfatDebug`, smoke: открыть чат, удалить сообщение, проверить сохранение + очистку.

## Порядок перед пушем

```powershell
python tools/check-touch-points.py
python tools/security-check.py
git status --short   # только intended файлы
git diff             # прочитать весь diff глазами
```
