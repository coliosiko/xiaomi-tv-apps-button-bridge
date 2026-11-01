# Отличия от оригинала

Первоисточник: [kostyay/xiaomi-tv-apps-button-bridge](https://github.com/kostyay/xiaomi-tv-apps-button-bridge), MIT.
База форка: v2.0.0, `8d1dd584670a1f7a1734b7f0b53bda2f49252784`.

| Область | Оригинал v2.0.0 | Этот форк |
|---|---|---|
| Проверенное устройство | MiTV-AFMU0 / Android TV 14 | MiTV-MOOQ1 / Android TV 11 |
| Package | `com.kostyay.xiaomiappsbridge` | `com.kostyay.xiaomiappsbridge.diagnostic` |
| Kadb | 2.1.1 | 2.1.5 |
| SDK | min 26 / target 36 / compile 36 | min 26 / target 36 / compile 37 |
| Toolchain | Исходная конфигурация upstream | JDK 17 / Gradle 9.3.1 / AGP 9.1.1 / Kotlin 2.3.20 |
| Receiver | Вызов нового API без совместимого пути для Android 11 | API 33+ с NOT_EXPORTED; до 33 — signature permission |
| ADB | Базовые статусы | Отдельные этапы RSA / connect / listener, цепочка причин и сохранённый stack trace |
| Recent apps | Нет собственной панели | Панель из ADB recents с названиями и иконками |
| Sleep timer | Нет | 30/60/90/120 минут, отмена, статус, AlarmManager + sleep key |
| Маппинги | Projectivy, apps, intents | Те же варианты плюс RECENTS и SLEEP_TIMER; прежняя JSON-схема сохранена |

## 2.0.2-mitv-diagnostic.2 — versionCode 4

- Добавлены новые action types, отдельная диалоговая TV Activity, фильтрация пакетов и unit-тесты парсера recents.
- Добавлены TimerReceiver, точное планирование AlarmManager, сохранение elapsed deadline и boot count, отмена и статус.
- При reboot срок очищается; при новом процессе предусмотрено восстановление, без восстановления просроченных более чем на две минуты таймеров.
- `KEYCODE_SLEEP` выбран вместо переключателя Power: уже уснувшее устройство не должно просыпаться от команды.
- Сохранены package, ADB identity, ключ подписи локального APK и маппинги diagnostic.1.

## 2.0.1-mitv-diagnostic.1 — versionCode 3

- Kadb 2.1.5 и обновление сборочного окружения для его AAR.
- Совместимая регистрация BroadcastReceiver на Android 11.
- Подготовка RSA в рабочем потоке с обработкой исключений; диагностика этапов и ошибок ADB.
- Переподключение через 5 секунд, закрытие соединений и проверка exit code назначенных команд.
- TV launcher entry и отдельный диагностический package.

## Что форк не меняет автоматически

- Default launcher, системные пакеты и обработчики фирменных кнопок.
- Пользовательские назначения кнопок.
- Разрешения и настройки сетевого ADB.

Функции upstream и его авторство не приписываются этому форку. Оригинальный README сохранён в [UPSTREAM-README.md](UPSTREAM-README.md).

## История upstream после базовой версии

При публикации форка в истории сохранён также upstream-коммит 47b05d8 (Add Kotlin static analysis). Его дополнительные инструменты и изменения не перенесены в рабочий снимок diagnostic.2: форк намеренно сохраняет проверенную базу v2.0.0 и описанные выше доработки. Это отражено отдельным merge-коммитом; дальнейшая синхронизация upstream остаётся отдельной задачей.
