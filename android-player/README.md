# Russian IPTV Player

Простой Android IPTV-плеер для M3U/M3U8: URL или локальный файл, категории, поиск, HLS/TS-воспроизведение, логотипы из метаданных плейлиста и EPG XMLTV для текущей передачи.

## Сборка APK

Откройте каталог `android-player` в Android Studio и выполните `Build > Build APK(s)`.

Минимальная версия Android: 6.0 (API 23). Плеер использует AndroidX Media3/ExoPlayer. Приложение не содержит каналов внутри APK: по умолчанию загружает публичный `playlist.m3u` репозитория, но URL можно заменить.
