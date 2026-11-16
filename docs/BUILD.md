# 构建（BUILD）

> 结论先行：**`gradle build` 现在能在这台机器上跑通，19 秒，全程离线，不依赖 `maven.neoforged.net`。**
> 2026-11-15 Aki 拍板「用镜像源」后实测发现「纯镜像 + ModDevGradle」不可行，改用下面这条路。

## 1. 为什么不能只用镜像

ModDevGradle 需要 `net.neoforged:neoform-runtime`（反编译 + 打补丁那套工具链）。该构件**只在 `maven.neoforged.net` 上**：

| 镜像 | `neoform-runtime-2.0.31.pom` |
|---|---|
| `maven.aliyun.com/repository/public` | 404 |
| `mirrors.cloud.tencent.com/nexus/repository/maven-public` | 404 |
| `repo.huaweicloud.com/repository/maven` | 404 |
| `mirrors.tuna.tsinghua.edu.cn/maven` | 404 |
| `bmclapi2.bangbang93.com/maven` | 404（连 `maven-metadata.xml` 都没有） |
| `maven.neoforged.net`（官方） | 连接超时（DNS 被污染到境外 CDN 段） |

`neoforge` 本体有镜像（bmclapi 302 → 可用），但构建工具链没有。所以「换镜像 + 装 MDG」这条路到此为止——不是配置问题，是构件不存在。

## 2. 实际方案：镜像源 + 服务端 libraries 直编

关键事实：**NeoForge 从 1.20.2 起取消 reobf**。mod jar 里的类就是 Mojang 官方名，运行时不需要重映射。所以「已打补丁的 Minecraft + NeoForge API」+ `javac` 产出的 jar 就是合法产物，MDG 那套只在开发环境（反编译看源码、`runClient`）才有价值。

`build.gradle` 因此变成：

```gradle
dependencies {
    compileOnly files(   // 顺序有意义：server-srg 必须排最前
        ".../net/minecraft/server/1.21.1-20240808.144430/server-1.21.1-20240808.144430-srg.jar",
        ".../net/neoforged/neoforge/21.1.257/neoforge-21.1.257-universal.jar",
        ".../net/neoforged/neoforge/21.1.257/neoforge-21.1.257-server.jar"
    )
    compileOnly fileTree(dir: neoforgeLibs, include: ['**/*.jar'], exclude: [...])  // gson / joml / log4j…
}
```

- `neoforgeLibs` 默认 `E:/server-test/libraries`，在 `gradle.properties` 的 `neoforge_libraries` 改；指向正式服 `E:/server-1.21.1/libraries` 也行。
- `repositories` 保留阿里 / 腾讯 / 华为 / Maven Central，给以后真要引的库型前置（GeckoLib / Curios…）用。
- 旧的 MDG 脚本留在 `build-mdg.gradle.disabled`，等哪天 `neoform-runtime` 有国内镜像或能开代理，再切回去跑 `runServer` / 看反编译源码。

## 3. 实测记录（2026-11-15 11:25）

```
$ gradle build --offline --console=plain
> Task :compileJava
> Task :processResources
> Task :jar
BUILD SUCCESSFUL in 19s
```

产物 `build/libs/redtale_project-0.1.0.jar`（44 KB，22 个 class + 展开过变量的 `META-INF/neoforge.mods.toml`），直接丢进 `E:\server-test\mods` 后 **`Done (2.353s)` 正常启动**，`RedtaleProject` 加载 1 条脚本。

## 4. 何时需要 MDG（记在这，不要重新踩坑）

| 需求 | 现在怎么办 |
|---|---|
| 看 Minecraft / NeoForge 源码 | 反编译 `server-srg.jar`（`D:\redtale-work\decompiled`），或用 IDE 的 `attach sources` |
| `runClient` 调试客户端 | **做不了**，需要 MDG + NFRT。客户端相关验证走「打进整合包直接开游戏」 |
| 引第三方库型前置（GeckoLib 等） | 从正式服 `mods/` 复制 jar 到 `libs/`，`compileOnly fileTree('libs')`；**不要**加 maven 依赖（它们大多不在 Central） |
| 发布正式包 | 就是 `gradle build`，产物即发布包（NeoForge 1.21.1 无需 remap） |

## 5. 已知坑

1. **classpath 顺序**：`libraries` 里有两份 `net.minecraft` 类（`server-srg` 和别处），`server-srg` 必须排最前，否则 javac 报「找不到 overworld()」。
2. **`--offline`**：没有外部依赖时加它能省掉一次仓库探测（不加也能过，只是慢几秒）。
3. **Gradle CLI**：本机没装，用 `D:\redtale-work\tools\gradle-8.8\bin\gradle`。wrapper 也能用（指向腾讯镜像的发行版）。
