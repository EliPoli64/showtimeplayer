cmake_minimum_required(VERSION 3.22.1)
project(showtimeplayer_native)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)

set(log-lib log)

if (CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a" OR CMAKE_ANDROID_ARCH_ABI STREQUAL "x86_64")
  set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")
  set(CMAKE_MODULE_LINKER_FLAGS "${CMAKE_MODULE_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")
endif()

add_library(
    showtimeplayer_native
    SHARED
    AudioEngineNative.cpp
    PitchSpeedProcessor.cpp
    MetronomeSynthesizer.cpp
)

target_include_directories(showtimeplayer_native PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

target_link_libraries(showtimeplayer_native ${log-lib} android oboe::oboe)
