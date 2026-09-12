cmake_minimum_required(VERSION 3.22.1)
project(showtimeplayer_native)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)

add_library(
    showtimeplayer_native
    SHARED
    AudioEngineNative.cpp
    PitchSpeedProcessor.cpp
    MetronomeSynthesizer.cpp
)

target_include_directories(showtimeplayer_native PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

find_library(log-lib log)
target_link_libraries(showtimeplayer_native ${log-lib} android oboe::oboe)
