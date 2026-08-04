# 开发模式运行：out/classes（含最新编译）优先，game.jar 提供 libGDX/LWJGL 等依赖
# 用 cmd 调用避免 PowerShell 对分号的解析问题
cmd /c 'java --enable-native-access=ALL-UNNAMED -cp "out\classes;game.jar" Main'
