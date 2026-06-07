cmake_minimum_required(VERSION 3.22.1)
project(uac2_descriptor_test LANGUAGES CXX)

add_executable(uac2_descriptor_test
    uac2_descriptor_test.cpp
    ../../main/cpp/uac2/uac2_descriptor.cpp
)

target_include_directories(uac2_descriptor_test PRIVATE
    ../../main/cpp
)

target_compile_features(uac2_descriptor_test PRIVATE cxx_std_17)
enable_testing()
add_test(NAME uac2_descriptor_test COMMAND uac2_descriptor_test)
