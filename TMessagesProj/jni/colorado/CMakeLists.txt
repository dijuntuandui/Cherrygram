cmake_minimum_required(VERSION 3.6.0)
# Aiqiyi: stub CMake for missing upstream 'colorado' module.
add_library(colorado STATIC ${CMAKE_CURRENT_SOURCE_DIR}/colorado.cpp)
set_target_properties(colorado PROPERTIES ANDROID_ARM_MODE arm)
target_include_directories(colorado PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
