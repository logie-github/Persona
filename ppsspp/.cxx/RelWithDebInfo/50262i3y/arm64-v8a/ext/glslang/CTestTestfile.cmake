# CMake generated Testfile for 
# Source directory: /home/user/Persona/build/ppsspp-src/ext/glslang
# Build directory: /home/user/Persona/ppsspp/.cxx/RelWithDebInfo/50262i3y/arm64-v8a/ext/glslang
# 
# This file includes the relevant testing commands required for 
# testing this directory and lists subdirectories to be tested as well.
add_test(glslang-testsuite "bash" "runtests" "/home/user/Persona/ppsspp/.cxx/RelWithDebInfo/50262i3y/arm64-v8a/ext/glslang/localResults" "/home/user/Persona/ppsspp/.cxx/RelWithDebInfo/50262i3y/arm64-v8a/ext/glslang/StandAlone/glslangValidator" "/home/user/Persona/ppsspp/.cxx/RelWithDebInfo/50262i3y/arm64-v8a/ext/glslang/StandAlone/spirv-remap")
set_tests_properties(glslang-testsuite PROPERTIES  WORKING_DIRECTORY "/home/user/Persona/build/ppsspp-src/ext/glslang/Test/" _BACKTRACE_TRIPLES "/home/user/Persona/build/ppsspp-src/ext/glslang/CMakeLists.txt;351;add_test;/home/user/Persona/build/ppsspp-src/ext/glslang/CMakeLists.txt;0;")
subdirs("External")
subdirs("glslang")
subdirs("OGLCompilersDLL")
subdirs("SPIRV")
subdirs("gtests")
