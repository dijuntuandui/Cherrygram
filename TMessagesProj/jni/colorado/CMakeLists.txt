cmake_minimum_required(VERSION 3.6.0)
# Aiqiyi: stub CMake for missing upstream 'colorado' module.
# NOTE: include() does NOT change CMAKE_CURRENT_SOURCE_DIR, so use
# CMAKE_CURRENT_LIST_DIR to refer to this directory correctly.
add_library(colorado STATIC ${CMAKE_CURRENT_LIST_DIR}/colorado.cpp)
set_target_properties(colorado PROPERTIES ANDROID_ARM_MODE arm)
target_include_directories(colorado PUBLIC ${CMAKE_CURRENT_LIST_DIR})
