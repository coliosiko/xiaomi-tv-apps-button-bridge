# Сборка и подпись

## Зафиксированная конфигурация

- JDK **17**; Gradle Wrapper **9.3.1** с SHA-256 дистрибутива.
- Android Gradle Plugin **9.1.1**, Kotlin **2.3.20**, ktlint **1.8.0**.
- Android SDK Platform **37**, Build Tools **36.0.0**.
- `minSdk 26`, `targetSdk 36`, `compileSdk 37`.
- Kadb **2.1.5**, JUnit **4.13.2** для тестов.

`minSdk` разрешает установку начиная с Android 8.0. Проверенная ОС — Android TV 11 / API 30.
`targetSdk` задаёт целевые правила платформы, `compileSdk` — API для компиляции на компьютере: эти числа не требуют Android 16/17 на телевизоре.

## Локально

Установите JDK 17 и Android SDK. SDK Manager должен содержать платформу 37 и Build Tools 36.0.0.
Задайте `JAVA_HOME` на JDK и `ANDROID_HOME` на каталог SDK; либо создайте неотслеживаемый `local.properties`:

```properties
# Пример Windows; используйте собственный путь
sdk.dir=C\:/Android/Sdk
```

Windows / PowerShell:

```powershell
.\gradlew.bat ktlintCheck lintDebug testDebugUnitTest assembleDebug
```

Linux/macOS:

```sh
./gradlew ktlintCheck lintDebug testDebugUnitTest assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Тесты: `app/build/reports/tests/testDebugUnitTest/index.html`.
Lint: `app/build/reports/lint-results-debug.html`.

Если нужно безопасно применить форматирование Kotlin:

```sh
./gradlew ktlintFormat
```

## Подпись: почему install -r может не сработать

Распространяемый `2.0.2-mitv-diagnostic.2` — **диагностический debug APK**, подписанный тем же локальным ключом, что diagnostic.1.
Сертификат SHA-256 (публичный отпечаток, не приватный ключ):

```text
83b0fa55c50a407949d2151ec160d534fc72f78bb3c90ea3b488e510234388c3
```

`adb install -r` сохраняет данные при совпадении package, сертификата и допустимой версии APK.
Самостоятельная debug-сборка или обычный GitHub Actions runner создают собственный debug-ключ.
Такой APK обычно **не сможет обновить** установленную локальную диагностическую сборку: Android вернёт ошибку несовместимой подписи.
Не удаляйте установленное приложение ради обхода ошибки, если хотите сохранить его данные; используйте сборку с прежним ключом.

Приватный signing key, ADB RSA identity, пароли и данные телевизора **не входят в репозиторий**.

## Ручная release-сборка

Существующая конфигурация поддерживает четыре переменные окружения:

- `RELEASE_KEYSTORE_PATH` — путь к keystore вне репозитория;
- `RELEASE_STORE_PASSWORD`;
- `RELEASE_KEY_ALIAS`;
- `RELEASE_KEY_PASSWORD`.

После задания переменных в своей среде:

```sh
./gradlew ktlintCheck lintRelease testReleaseUnitTest assembleRelease
```

Результат: `app/build/outputs/apk/release/app-release.apk`.
Проверка:

```sh
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

Для новой версии измените значения в `app/build.gradle.kts` или передайте `-PVERSION_CODE=5 -PVERSION_NAME=2.0.3-mitv-diagnostic.3`.
Также обновите соответствующие строки версии в UI/диагностике и документации: сейчас они заданы явно.

## GitHub Actions

- **Build APK** — проверки и debug APK на push/PR; сборочный артефакт для тестирования, не автоматически совместимое обновление установленного APK.
- **Build signed APK (manual)** — запускается вручную и требует repository secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
  Использует версию из исходников, проверяет подпись и создаёт артефакт. Не публикует релиз автоматически.

Secrets для подписи не настроены этим форком автоматически. Решение загрузить долгоживущий signing key в GitHub принимается отдельно владельцем.
Теги релизов: `vMAJOR.MINOR.PATCH`; например `v2.0.2`. Полное имя APK diagnostic указывается в заголовке и описании релиза.
