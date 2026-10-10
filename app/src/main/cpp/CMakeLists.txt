cmake_minimum_required(VERSION 3.22)
project(ottomatic_android C CXX)

set(CMAKE_C_STANDARD 17)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

get_filename_component(ROOT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../.." ABSOLUTE)
set(SDL_DIR   "${ROOT_DIR}/third_party/SDL")
set(GL4ES_DIR "${ROOT_DIR}/third_party/gl4es")
set(OTTO_DIR  "${ROOT_DIR}/third_party/OttoMatic")     # upstream checkout: Data/ + extern/Pomme
set(GAME_SRC  "${ROOT_DIR}/game/src")                  # patched copy of upstream src/

foreach(d SDL_DIR GL4ES_DIR OTTO_DIR)
    if(NOT EXISTS "${${d}}")
        message(FATAL_ERROR "${${d}} is missing - run ./setup.sh (or setup.bat) first")
    endif()
endforeach()
if(NOT EXISTS "${OTTO_DIR}/extern/Pomme/CMakeLists.txt")
    message(FATAL_ERROR "Pomme submodule is empty. Run: git -C third_party/OttoMatic submodule update --init --recursive")
endif()

# ---------------------------------------------------------------- SDL3 --
set(SDL_SHARED ON  CACHE BOOL "" FORCE)
set(SDL_STATIC OFF CACHE BOOL "" FORCE)
set(SDL_TEST_LIBRARY OFF CACHE BOOL "" FORCE)
set(SDL_TESTS OFF CACHE BOOL "" FORCE)
add_subdirectory("${SDL_DIR}" "${CMAKE_BINARY_DIR}/sdl" EXCLUDE_FROM_ALL)
# Let sub-projects that call find_package(SDL3) find the in-tree build
set(SDL3_DIR "${CMAKE_BINARY_DIR}/sdl" CACHE PATH "" FORCE)

# --------------------------------------------------------------- gl4es --
# Translates the game's fixed-function OpenGL (glBegin/glEnd, lights, fog, texgen...) to GLES2.
# NOTE: option names follow gl4es' CMakeLists; verify them if the build complains.
set(NOX11 ON CACHE BOOL "" FORCE)
set(STATICLIB ON CACHE BOOL "" FORCE)
set(NO_INIT_CONSTRUCTOR ON CACHE BOOL "" FORCE)     # we call initialize_gl4es() ourselves
set(USE_ANDROID_LOG ON CACHE BOOL "" FORCE)
set(DEFAULT_ES 2 CACHE STRING "" FORCE)
add_subdirectory("${GL4ES_DIR}" "${CMAKE_BINARY_DIR}/gl4es" EXCLUDE_FROM_ALL)

# --------------------------------------------------------------- Pomme --
set(POMME_NO_GRAPHICS true)
set(POMME_NO_INPUT    true)
set(POMME_NO_MP3      true)
set(POMME_NO_QD3D     true)
set(POMME_NO_VIDEO    true)
add_compile_definitions(POMME_CASE_SENSITIVE_FSSPEC=1)
add_subdirectory("${OTTO_DIR}/extern/Pomme" "${CMAKE_BINARY_DIR}/pomme")

# ---------------------------------------------------------------- game --
file(GLOB_RECURSE GAME_SOURCES CONFIGURE_DEPENDS
     "${GAME_SRC}/*.c" "${GAME_SRC}/*.cpp")

# Must be a shared library called "main": SDLActivity loads libmain.so and calls SDL_main()
add_library(main SHARED ${GAME_SOURCES})

target_include_directories(main PRIVATE "${GAME_SRC}/Headers" "${GL4ES_DIR}/include")

target_compile_definitions(main PRIVATE GL_SILENCE_DEPRECATION)

target_compile_options(main PRIVATE
    -fexceptions                 # Pomme throws C++ exceptions through the game's C frames
    -Wno-multichar
    -Wno-unknown-pragmas
    -Wno-unused-parameter
    -Wno-implicit-function-declaration
    -Wno-incompatible-pointer-types
    -Wno-int-conversion
)

if(TARGET GL)
    set(GL_LIB GL)
elseif(TARGET GL4ES)
    set(GL_LIB GL4ES)
else()
    message(FATAL_ERROR "gl4es target not found - check third_party/gl4es/CMakeLists.txt for the library target name")
endif()

target_link_libraries(main PRIVATE Pomme SDL3::SDL3 ${GL_LIB} log android m)
