<div align="center">

# Otto Matic — порт для Android

**Неофициальный нативный порт 3D-экшена _Otto Matic_ на Android**

[🇬🇧 English](README.md) · **🇷🇺 Русский**

[![Build Android APK](https://github.com/nullch/ottomatic-android/actions/workflows/build.yml/badge.svg)](https://github.com/nullch/ottomatic-android/actions/workflows/build.yml)
[![Лицензия: CC BY-NC-SA 4.0](https://img.shields.io/badge/license-CC%20BY--NC--SA%204.0-lightgrey.svg)](LICENSE.md)
![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B-3DDC84.svg)
![ABI arm64-v8a](https://img.shields.io/badge/ABI-arm64--v8a-blue.svg)

[Telegram: @nullch](https://t.me/nullch) · [Канал: @nullchdays](https://t.me/nullchdays)

</div>

---

## Скриншоты

<!--
  СЮДА ВСТАВИТЬ СКРИНШОТ.
  Замените файл docs/screenshots/screenshot.png своим (с тем же именем)
  или поменяйте путь ниже. Лучше всего горизонтальный 16:9 (например, 1280x720).
  Чтобы добавить ещё, положите файлы в docs/screenshots/ и добавьте строки <img>.
-->
<p align="center">
  <img src="docs/screenshots/screenshot.png" alt="Otto Matic на Android" width="720">
</p>

---

## О проекте

Otto Matic — 3D-экшен от Pangea Software (2001). За основу порта взята
[современная версия для ПК](https://github.com/jorio/OttoMatic) от Iliyas Jorio.
Этот репозиторий добавляет к ней слой для Android: нативную сборку на SDL3, трансляцию
OpenGL → GLES2, экранный геймпад и русскую локализацию.

## Возможности

- Нативная сборка под Android (C/C++ через NDK, SDL3 для окна, ввода и звука).
- Фиксированный конвейер OpenGL игры транслируется в OpenGL ES 2.0 через [gl4es](https://github.com/ptitSeb/gl4es).
- Экранный сенсорный геймпад. Прячется сам, когда подключён физический геймпад.
- Bluetooth- и USB-геймпады через SDL3.
- 7 языков: English, Français, Deutsch, Español, Italiano, Svenska, Русский.
  Язык выбирается при первом запуске; в растровый шрифт игры добавлена кириллица
  (см. [`tools/fontgen`](tools/fontgen/README.md)).

**Ограничения:** собирается только `arm64-v8a`, минимальная версия Android — 7.0 (API 24),
готового APK в репозитории нет — собирать нужно самому (инструкция ниже).

## Сборка

### Что нужно

| Инструмент      | Версия                         |
|-----------------|--------------------------------|
| Git             | любой свежий                   |
| JDK             | 17                             |
| Android SDK     | API 35                         |
| Android NDK     | 27.0.12077973                  |
| CMake           | 3.22.1                         |
| Android Studio  | Jellyfish (2024.1.1) или новее |

SDK, NDK и CMake можно поставить прямо из Android Studio (SDK Manager).

### Шаги

**1. Склонируйте репозиторий**

```bash
git clone https://github.com/nullch/ottomatic-android.git
cd ottomatic-android
```

**2. Скачайте зависимости и примените патчи**

Скрипт клонирует в `third_party/` оригинальную игру (ради `Data/` и библиотеки Pomme),
SDL3 и gl4es на зафиксированных коммитах, применяет патчи из `patches/` и копирует
шрифт из `game-data-overlay/` поверх игровых данных.

```bash
# Linux / macOS
./setup.sh

# Windows (cmd или PowerShell)
setup.bat
```

**3. Соберите**

Откройте папку в Android Studio (**File → Open**), дождитесь синхронизации Gradle и нажмите **Run**.
Или соберите из командной строки:

```bash
# Linux / macOS
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug
```

APK появится в `app/build/outputs/apk/debug/app-debug.apk`.
Установить: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

> Первая сборка долгая: компилируются SDL3, gl4es и вся игра.

## Данные игры

Игровые данные (`Data/`) берутся из оригинального репозитория и упаковываются в APK при сборке.
При первом запуске приложение распаковывает их во внутреннее хранилище.

Если хотите использовать свою изменённую папку `Data/`, положите её сюда
(внутри должен быть файл `System/gamecontrollerdb.txt`):

```
/sdcard/Android/data/ru.nullch.ottomaticfork/files/Data/
```

## Структура проекта

```
ottomatic-android/
├── app/                 Android-модуль (Gradle, Java-лаунчер, экранный геймпад, CMake)
├── game/                Исходный код игры (src/ оригинала с правками под Android)
├── game-data-overlay/   Файлы, которые копируются поверх Data/ оригинала (кириллический шрифт)
├── patches/             Патчи для gl4es и Pomme
├── tools/fontgen/       Скрипты генерации кириллического шрифта
├── docs/                Скриншоты и картинки для документации
├── licenses/            Тексты лицензий сторонних компонентов
├── setup.sh / setup.bat Скачивание зависимостей и применение патчей
└── third_party/         Создаётся скриптом setup (SDL3, gl4es, оригинал игры) — в репозиторий не входит
```

## Авторство и лицензия

Otto Matic заново выпущена Iliyas Jorio на <https://github.com/jorio/OttoMatic>
под лицензией [CC BY-NC-SA 4.0](https://github.com/jorio/OttoMatic/blob/master/LICENSE.md)
с разрешения Pangea Software, Inc.

© 2001 Pangea Software, Inc. Otto Matic — торговая марка Pangea Software, Inc.
Часть кода © 2021 Iliyas Jorio. Часть, относящаяся к порту на Android, © 2026 nullch.

Этот порт — неофициальный фанатский проект, он не связан с Pangea Software, Inc. и не одобрен ею.
Распространяется на тех же условиях, под лицензией [CC BY-NC-SA 4.0](LICENSE.md):
**только некоммерческое использование**, с указанием авторства, производные работы
распространяются под той же лицензией. В оригинал внесены изменения
(порт на Android, трансляция в GLES2, сенсорное управление, русская локализация).

Сторонние компоненты (SDL3, gl4es, Pomme, шрифт Liberation) перечислены в [THIRD_PARTY.md](THIRD_PARTY.md).
См. также [NOTICE.md](NOTICE.md).

## Контакты

- Telegram: [@nullch](https://t.me/nullch)
- Telegram-канал: [@nullchdays](https://t.me/nullchdays)
- Баги и идеи: [GitHub Issues](https://github.com/nullch/ottomatic-android/issues)
