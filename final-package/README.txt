================================================================================
                    LSPatch - 无需 Root 的 Xposed 框架
================================================================================
版本: 0.6 (build 348)
构建日期: 2024-06-13

[核心文件]
  lspatch.jar              - APK 修补工具 (主要程序)
  manager.apk              - LSPatch 管理器应用

[资源文件]
  loader.dex               - Xposed 框架运行时代码 (已嵌入 jar)
  metaloader.dex           - Meta 加载器入口 (已嵌入 jar)
  libs/*.so                - 四种 ABI 的原生 Hook 库 (已嵌入 jar)

[使用方法]
  1. 修补 APK 并嵌入模块:
     java -jar lspatch.jar target.apk --embed module1.apk module2.apk

  2. 修补 APK 使用管理器模式:
     java -jar lspatch.jar target.apk --manager

  3. 常用参数:
     -o <dir>               - 输出目录 (默认: 当前目录)
     -f                     - 强制覆盖已存在文件
     -d                     - 设置为 debuggable
     -r                     - 允许降级安装 (将 versionCode 设为 1)
     -v                     - 详细输出
     -l <level>             - 签名绕过级别 (0=关闭, 1=pm, 2=pm+openat)
     -k <path> <pass> <alias> <aliasPass>  - 自定义签名密钥

[支持的 ABI]
  arm64-v8a, armeabi-v7a, x86, x86_64

[输出文件]
  target-348-lspatched.apk - 已修补的 APK 可直接安装使用

================================================================================
