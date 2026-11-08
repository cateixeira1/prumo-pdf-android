# Biblioteca nativa do Prumo: a ligação com o PDFium (pdfium-binaries, de bblanchon, versão fixa em
# app/build.gradle.kts e baixada com conferência de SHA-256).
cmake_minimum_required(VERSION 3.22.1)
project(prumo LANGUAGES CXX)

set(PDFIUM_ROOT "${PDFIUM_DIR}/${ANDROID_ABI}")

add_library(pdfium SHARED IMPORTED)
set_target_properties(pdfium PROPERTIES IMPORTED_LOCATION "${PDFIUM_ROOT}/lib/libpdfium.so")

add_library(prumo SHARED prumo_pdfium.cpp)
set_target_properties(prumo PROPERTIES CXX_STANDARD 17 CXX_STANDARD_REQUIRED ON)
target_include_directories(prumo PRIVATE "${PDFIUM_ROOT}/include")
target_compile_options(prumo PRIVATE -Wall -O2 -fvisibility=hidden)
target_link_options(prumo PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(prumo pdfium jnigraphics log)
