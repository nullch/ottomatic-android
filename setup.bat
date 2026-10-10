@echo off
setlocal
cd /d "%~dp0"
if not exist "third_party" mkdir third_party

if not exist "third_party\OttoMatic" (
  git clone --recurse-submodules https://github.com/jorio/OttoMatic.git third_party\OttoMatic
  git -C third_party\OttoMatic checkout 636056a92c1f276a5af5c3dc7df5c3cb952fd47a
)

if exist "patches\pomme-cmixer.patch" (
  git -C third_party\OttoMatic\extern\Pomme apply ..\..\..\..\patches\pomme-cmixer.patch 2>NUL
)

if not exist "third_party\SDL" (
  git clone https://github.com/libsdl-org/SDL.git third_party\SDL
  git -C third_party\SDL checkout 402fc52af4e731184ad6a704068b5ccd27d8f1b8
)

if not exist "third_party\gl4es" (
  git clone https://github.com/ptitSeb/gl4es.git third_party\gl4es
  git -C third_party\gl4es checkout ec16bedd8819c475326f4f1a3063772c6d986e06
)

if exist "patches\gl4es-android.patch" (
  git -C third_party\gl4es apply ..\..\patches\gl4es-android.patch 2>NUL
)

if exist "game-data-overlay\System" (
  if not exist "third_party\OttoMatic\Data\System" mkdir third_party\OttoMatic\Data\System
  xcopy /E /Y "game-data-overlay\System\*" "third_party\OttoMatic\Data\System\"
)

echo.
echo Done. Open this folder in Android Studio and let Gradle sync.
pause
