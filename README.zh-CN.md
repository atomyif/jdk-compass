# JDK Compass

[English](README.md)

一个用于排查 Java 环境不一致问题的命令行工具：项目要求 Java 21，终端却在跑 Java 17，或者 `JAVA_HOME` 指向另一个 JDK 时，给出具体原因和处理建议。

**当前状态：0.1.0 初期 MVP，尚未提供 GitHub Release 安装包。** 可按下方说明从源码构建。初始代码由 AI 辅助开发；后续贡献需要理解、审查并在真实项目中验证。

## 运行

准备完整的 JDK 17 或更新版本。在项目根目录执行：

```sh
java Build.java
java -jar build/jdk-compass.jar --project examples/java17
```

构建会自动编译、运行测试并生成 JAR，不需要安装 Maven、Gradle，也不会下载依赖。

检查你自己的项目：

```sh
java -jar build/jdk-compass.jar --project /你的项目路径 --expect 21
java -jar build/jdk-compass.jar --project /你的项目路径 --expect 21 --json
```

路径含空格时请加引号。`--expect` 是明确指定的 Java 主版本，会覆盖自动识别结果。`--strict` 会让警告也返回失败状态，适合 CI。

在编辑器中打开项目目录，设置项目 SDK 为 JDK 17+，在终端执行 `java Build.java` 即可。

## 已实现

- 读取项目根目录的 `.java-version`、`.sdkmanrc` 和 `.tool-versions`。
- 发现多份版本声明冲突，不静默选用其中一个。
- 比较项目要求、当前运行 JVM 和 `JAVA_HOME` 的 Java 主版本。
- 检查 JDK 目录、`java`、`javac` 以及 `release` 文件。
- 当 `JAVA_HOME` 与实际运行环境不是同一个目录时给出警告。
- 输出文本或 JSON 报告；报告不包含环境变量原文及绝对路径。

退出码：`0` 无错误，`1` 检查失败，`2` 命令参数错误。没有找到支持的版本声明时会警告，不代表项目不需要特定版本。

## 使用边界

诊断逻辑只读配置，不联网，不运行项目脚本，不切换 JDK，不修改 IDE。Java 启动器可能在本工具运行前加载并打印 JVM 环境变量，因此分享终端原始输出前仍需自行检查。

当前不解析 Maven、Gradle 和编辑器的项目 SDK；只比较主版本，不比较补丁版本或厂商。`latest`、多个候选版本等动态声明暂不支持。即使两套 JDK 的主版本相同，只要目录不同，也会提示检查。

本地已验证 macOS；Windows/Linux 的工作流已准备，但要等公开仓库中的 CI 实际通过后才能称已验证。

## 开发与贡献

修改代码后运行 `java Build.java`，完成编译和测试。提交问题时请提供最小复现、实际输出和预期输出。

- [贡献指南](CONTRIBUTING.md)
- [开发与维护路线图](docs/roadmap.md)
- [构建与发布](docs/publishing.md)
- [报告格式](docs/report-format.md)

源码使用 [MIT 许可证](LICENSE)。
