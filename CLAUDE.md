# StandBy для Android — правила проекта

Источник истины по продукту и этапам — `PLAN.md`. По ходу работы обновлять в нём разделы
«Этапы», «Риски» и «Журнал решений».

## Язык
- Документы, комментарии в коде, строки интерфейса — на русском.
- Идентификаторы, имена API, сообщения коммитов — на английском.

## Архитектура
- `:core` — чистый Kotlin/JVM. Никаких зависимостей от Android SDK: всё, что можно проверить
  unit-тестом без устройства (TriggerEngine, OrientationClassifier, NightModeDecider,
  SolarTimes, модели настроек и раскладки), живёт здесь. Новая логика принятия решений
  пишется в `:core` вместе с тестами.
- `:app` — тонкий Android-слой: адаптеры датчиков/системных событий в `Flow`, служба,
  Activity, Compose-UI, репозитории данных. Компоненты Android берут зависимости только из
  `AppGraph` (ручной DI, без фреймворков).
- Запуск `StandbyService` — только через `ServiceStarter` (единая точка, логирует отказы).
- Пакет и applicationId: `com.realfamousbae.standby.dock`.

## Сборка
- JDK 21 (Homebrew `openjdk@21`): `export JAVA_HOME=/opt/homebrew/opt/openjdk@21`.
- `./gradlew :core:test :app:assembleDebug :app:lintDebug` — должно быть зелёным перед коммитом.
- Установка на телефон: `./gradlew :app:installDebug` (adb: `~/Library/Android/sdk/platform-tools/adb`).
- Версии зависимостей — только в `gradle/libs.versions.toml`.

## Приватность (обязательно)
Репозиторий может стать публичным. Не коммитить персональные данные: координаты, адреса,
события календаря, имена контактов, содержимое уведомлений, серийные номера и IP устройств,
логи с устройства (`diag.log`, logcat), ключи подписи и пароли. Такие значения — только в
локальных настройках приложения или в `local.properties` / `keystore.properties`
(оба в `.gitignore`).
