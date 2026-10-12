# 构建与发布

## 构建

在项目根目录使用完整的 JDK 17 或更新版本执行：

```sh
java Build.java
```

构建会编译源码、运行测试并生成 `build/jdk-compass.jar`。CI 执行构建和测试，不自动发布。

## 验证产物

```sh
java -jar build/jdk-compass.jar --version
java -jar build/jdk-compass.jar --project examples/java17 --expect 17 --json
```

第二条命令应使用 JDK 17 运行，并确保所选 JDK 与运行环境一致。也可以用实际项目验证，将项目路径和预期主版本替换为对应值。

## 发布版本

- 在 `Main.VERSION` 中更新版本号，并同步 `CHANGELOG.md`。
- 确认 GitHub Actions 矩阵通过，文档中的平台支持范围与验证结果一致。
- 从对应提交构建 JAR，生成 SHA-256 校验值。
- 为该提交创建版本标签，并在 GitHub Release 中附上 JAR、校验值和变更说明。

仅提交项目源文件；`build/`、本地工具配置、个人报告和凭据不应纳入源码仓库。
