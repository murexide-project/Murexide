cmake_minimum_required(VERSION 3.22.1)

project(murexide_litehtml LANGUAGES C CXX)

set(LITEHTML_BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(LITEHTML_ENABLE_LINT OFF CACHE BOOL "" FORCE)

set(LITEHTML_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/litehtml")
if(NOT EXISTS "${LITEHTML_SOURCE_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "litehtml submodule is missing. Run: git submodule update --init --recursive")
endif()

add_subdirectory("${LITEHTML_SOURCE_DIR}" "${CMAKE_BINARY_DIR}/litehtml" EXCLUDE_FROM_ALL)

if(TARGET litehtml)
    target_compile_options(litehtml PRIVATE
        -Os
        -fvisibility=hidden
        -ffunction-sections
        -fdata-sections
    )
endif()

add_library(murexide_litehtml SHARED litehtml_jni.cpp)

target_compile_features(murexide_litehtml PRIVATE cxx_std_17)
target_compile_options(murexide_litehtml PRIVATE
    -Wall
    -Wextra
    -Wpedantic
    -Os
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
)
target_link_options(murexide_litehtml PRIVATE
    -Wl,--gc-sections
    -Wl,--strip-all
)
target_link_libraries(murexide_litehtml PRIVATE litehtml log android)