cmake_minimum_required(VERSION 3.18.1)

# An executable, but named lib*.so: AGP only packages lib*.so files from the
# native build, and Android's linker runs a PIE ELF just fine regardless of name.
add_executable(lidled lidled.c)
set_target_properties(lidled PROPERTIES
    OUTPUT_NAME "liblidled"
    SUFFIX ".so"
    POSITION_INDEPENDENT_CODE ON)
target_compile_options(lidled PRIVATE -O2 -Wall -Wextra -fstack-protector-strong)
target_link_options(lidled PRIVATE -pie -Wl,--gc-sections -Wl,-z,relro,-z,now)
