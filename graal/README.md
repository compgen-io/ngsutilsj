GraalVM native-image config

This directory holds GraalVM configuration files (for example:
reflect-config.json, resource-config.json, jni-config.json).

Build:
  ant native-build -Dnative.os=macos -Dnative.arch=aarch64

Notes:
- Set GRAALVM_HOME to point at a GraalVM Community install, or ensure
  native-image is on PATH.
- Extra native-image flags can be passed via -Dnative.image.args="...".

Targets (run on the matching host OS/arch):
- ant native.macos.aarch64  -> dist/ngsutilsj.macos_aarch64
- ant native.macos.x86_64   -> dist/ngsutilsj.macos_x86_64
- ant native.linux.x86_64   -> dist/ngsutilsj.linux_x86_64
- ant native.linux.aarch64  -> dist/ngsutilsj.linux_aarch64
- ant native.windows.x86_64 -> dist/ngsutilsj.windows_x86_64.exe
