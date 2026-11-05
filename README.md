# StandBy для Android

Прикроватный режим в духе iOS StandBy для Android-телефона: поставили телефон на бок
(на подставку), экран погас — и вместо экрана блокировки появляются крупные часы с
автояркостью по датчику освещённости. Работает и на зарядке, и без неё (без зарядки —
пока заряд выше порога). Первое устройство — Samsung Galaxy S24 Ultra (Android 17, One UI 9).

Проект личный, в Google Play не публикуется. Подробный план и журнал решений — в
[PLAN.md](PLAN.md).

## Установка

1. Скачать `standby-<версия>.apk` со страницы [Releases](../../releases) и открыть на телефоне
   (разрешить установку из этого источника).
2. Открыть приложение и пройти пункты экрана диагностики: «Поверх других окон»,
   «Без оптимизации батареи», «Уведомления»; в карточке приложения — «Батарея → Без
   ограничений». Системную «Заставку» выключить.

## Сборка

Нужен JDK 21 и Android SDK (platform 37).

```bash
./gradlew :core:test :app:lintDebug :app:assembleDebug
```

Release-сборки собирает и подписывает GitHub Actions ([workflow](.github/workflows/android.yml));
тег `v*` публикует APK в Releases.

## Устройство кода

- `core/` — чистый Kotlin без Android: `TriggerEngine` (когда показывать режим),
  `OrientationClassifier`, `AutoBrightness`, модель настроек. Всё под unit-тестами.
- `app/` — Android-слой: foreground-служба, исполняющая решения `TriggerEngine`,
  Activity режима поверх экрана блокировки, адаптеры датчиков, настройки в DataStore.

## Лицензия

[MIT](LICENSE)

Шрифт экрана режима — [Inter](https://github.com/rsms/inter) (SIL Open Font License 1.1, текст лицензии — [licenses/Inter-OFL.txt](licenses/Inter-OFL.txt)).
