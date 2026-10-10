#!/usr/bin/env bash
# Run once after cloning this project:  ./setup.sh
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p third_party

# Upstream game: we need Data/ and the Pomme submodule from here.
if [ ! -d third_party/OttoMatic ]; then
  git clone --recurse-submodules https://github.com/jorio/OttoMatic.git third_party/OttoMatic
  git -C third_party/OttoMatic checkout 636056a92c1f276a5af5c3dc7df5c3cb952fd47a 2>/dev/null || true
else
  git -C third_party/OttoMatic submodule update --init --recursive
fi

if [ -f patches/pomme-cmixer.patch ]; then
  git -C third_party/OttoMatic/extern/Pomme apply ../../../../patches/pomme-cmixer.patch 2>/dev/null || true
fi

# SDL3 (needed: CMake sources + Java sources for SDLActivity)
if [ ! -d third_party/SDL ]; then
  git clone https://github.com/libsdl-org/SDL.git third_party/SDL
  git -C third_party/SDL checkout 402fc52af4e731184ad6a704068b5ccd27d8f1b8 2>/dev/null || true
fi

# gl4es: fixed-function OpenGL -> GLES2
if [ ! -d third_party/gl4es ]; then
  git clone https://github.com/ptitSeb/gl4es.git third_party/gl4es
  git -C third_party/gl4es checkout ec16bedd8819c475326f4f1a3063772c6d986e06 2>/dev/null || true
fi

if [ -f patches/gl4es-android.patch ]; then
  git -C third_party/gl4es apply ../../patches/gl4es-android.patch 2>/dev/null || true
fi

# Copy game-data-overlay over third_party/OttoMatic/Data/System
if [ -d game-data-overlay/System ]; then
  mkdir -p third_party/OttoMatic/Data/System
  cp -rf game-data-overlay/System/* third_party/OttoMatic/Data/System/
fi

echo
echo "Done. Open this folder in Android Studio (File > Open) and let Gradle sync."
echo "(Android Studio will offer to install NDK 27 / CMake 3.22 if missing.)"
