# JDK Compass

[English](README.md)

一个用于排查 Java 环境不一致问题的命令行工具：项目要求 Java 21，终端却在跑 Java 17，或者 `JAVA_HOME` 指向另一个 JDK 时，给出具体原因和处理建议。

**当前状态：0.5.0 初期 MVP，尚未提供 GitHub Release 安装包。** 可按下方说明从源码构建。初始代码由 AI 辅助开发；后续贡献需要理解、审查并在真实项目中验证。

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

### 比较运行 JVM 与所选 JDK 的版本

即使项目没有版本配置，也会比较当前 JVM 与所选 JDK 的 Java 主版本。例如工具运行在 Java 17 上，而 `JAVA_HOME` 指向 Java 21 时，会输出 `JDK_RUNTIME_VERSION_MISMATCH`，列出两个主版本并给出调整建议。

该检查同样适用于 `--java-home`，指定 `--expect` 或项目版本文件冲突时仍会执行。考虑到用户可能有意检查另一套 JDK，版本差异作为警告，`--strict` 下视为失败；原有的项目版本错误和目录差异警告仍会保留。无法读取有效版本元数据时报告 `JDK_RELEASE_UNREADABLE`，不进行该比较。检查只比较主版本，不运行所选 JDK。

### 检查 JDK 架构

工具会自动比较 `JAVA_HOME`（或 `--java-home`）中 JDK 声明的架构与当前 Java 进程报告的架构：

```sh
java -jar build/jdk-compass.jar --project /你的项目路径 --java-home /你的JDK路径 --json
```

支持将 `amd64`、`x86_64`、`x64` 视为同一架构，将 `aarch64`、`arm64` 视为同一架构，并识别 `x86` 与 `i386` 至 `i686` 的常见名称，忽略大小写。两边均可识别时输出 `JDK_ARCHITECTURE`；不同则增加 `JDK_ARCHITECTURE_MISMATCH` 警告。单独的架构警告默认不导致失败，`--strict` 下返回退出码 `1`。

缺少或无法识别架构信息时跳过比较，不回显原始值；没有架构诊断不代表匹配。检查依据是 JDK 的 `release` 文件和运行进程的 `os.arch`，不检测物理硬件、模拟运行状态或目标程序能否启动。`--expect` 只覆盖 Java 版本要求，不会关闭架构检查。

### 从子目录查找配置

使用 `--search-parents`，可以在 `src` 等子目录中找到上层项目的版本配置：

```sh
java -jar build/jdk-compass.jar --project /你的项目路径/src --search-parents --json
```

从选定目录开始，沿规范化路径逐级向上，找到任一受支持文件名所在的最近目录后停止。遇到 `.git` 目录、工作树的 `.git` 文件或文件系统根目录时，也会停止向上查找，但仍检查该目录中的配置。不同目录的配置不会合并；最近的配置无效或没有 Java 声明时，不会回退到更上层。`--expect` 会直接跳过配置查找。

来源显示为 `../.java-version` 等相对路径，不输出绝对路径。工具不执行 Git、不读取 `.git` 文件指向的位置、不搜索同级目录。父目录按路径层级查找，文件访问仍跟随符号链接，因此该边界不构成文件系统隔离。

### 导出 Markdown 报告

添加 `--markdown` 可生成包含检查结果、严重级别、诊断代码、原因和处理建议的表格，方便贴到 Issue 或 PR：

```sh
java -jar build/jdk-compass.jar --project /你的项目路径 --expect 21 --markdown > jdk-report.md
```

文件保存由终端重定向完成。Markdown 模式保留原有退出码和 `--strict` 行为；有错误时仍会输出报告。`--markdown` 和 `--json` 不能同时使用，冲突时返回退出码 `2`，只向标准错误输出提示。

## 已实现

- 默认读取选定目录的 `.java-version`、`.sdkmanrc` 和 `.tool-versions`；可用 `--search-parents` 向上查找最近的配置目录。
- 发现多份版本声明冲突，不静默选用其中一个。
- 比较项目要求、当前运行 JVM 和 `JAVA_HOME` 的 Java 主版本。
- 没有项目版本声明时，也能提示当前 JVM 与所选 JDK 的主版本差异。
- 检查 JDK 目录、`java`、`javac` 以及 `release` 文件。
- 比较可识别的 JDK 架构与当前 Java 进程架构，提示不一致。
- 当 `JAVA_HOME` 与实际运行环境不是同一个目录时给出警告。
- 输出文本、JSON 或 Markdown 报告；报告不包含环境变量原文及绝对路径。

退出码：`0` 无错误，`1` 检查失败，`2` 命令参数错误。没有找到支持的版本声明时会警告，不代表项目不需要特定版本。

## 使用边界

诊断逻辑只读配置，不联网，不运行项目脚本，不切换 JDK，不修改 IDE。Java 启动器可能在本工具运行前加载并打印 JVM 环境变量，因此分享终端原始输出前仍需自行检查。

当前不解析 Maven、Gradle 和编辑器的项目 SDK；只比较主版本，不比较补丁版本或厂商。`latest`、多个候选版本等动态声明暂不支持。即使两套 JDK 的主版本相同，只要目录不同，也会提示检查。

首次 GitHub Actions 验证已通过 macOS、Windows、Linux 与 Java 17、21、25 的九种组合。后续提交的验证状态见 [CI](https://github.com/atomyif/jdk-compass/actions)。

## 开发与贡献

修改代码后运行 `java Build.java`，完成编译和测试。提交问题时请提供最小复现、实际输出和预期输出。

- [贡献指南](CONTRIBUTING.md)
- [开发与维护路线图](docs/roadmap.md)
- [构建与发布](docs/publishing.md)
- [报告格式](docs/report-format.md)

源码使用 [MIT 许可证](LICENSE)。
