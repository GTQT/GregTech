# 核心mod 加载失败 / 模组不显示问题

## 问题现象

启动 `runClient` 后:

1. 日志报错,核心mod 插件类无法加载:

   ```
   [main/ERROR] [FML]: Coremod GregTechMixinLoadingPlugin: Unable to class load the plugin gregtech.mixins.GregTechMixinLoadingPlugin
   java.lang.ClassNotFoundException: gregtech.mixins.GregTechMixinLoadingPlugin
       at java.net.URLClassLoader.findClass(URLClassLoader.java:387)
       at net.minecraft.launchwrapper.LaunchClassLoader.findClass(LaunchClassLoader.java:117)
       ...
       at net.minecraftforge.fml.relauncher.CoreModManager.loadCoreMod(CoreModManager.java:527)
   ```

2. 进入游戏后,mod 列表里**看不到本模组(GregTech)**,模组完全不生效。

## 根本原因

模组自身的编译产物(`build/classes`、`build/resources` 等)处于**陈旧 / 损坏的残留状态**,导致启动早期 FML 通过 JVM 参数加载核心mod 时找不到 `gregtech.mixins.GregTechMixinLoadingPlugin` 类。

本项目核心mod 的加载链路如下:

- `build.gradle` 为 `runClient` / `runServer` 注入 JVM 参数:
  `-Dfml.coreMods.load=gregtech.mixins.GregTechMixinLoadingPlugin`
- 该类实现 `IFMLLoadingPlugin` 与 MixinBooter 的 `IEarlyMixinLoader`,负责在启动早期排队 mixin 配置(`mixins.gregtech.forge.json`、`mixins.gregtech.minecraft.json`)。
- 一旦该核心mod 类加载失败,模组的 mixin 与注册流程无法正常进行,连带整个 mod 无法注册,因此 mod 列表中不显示。

> 注意:类文件本身其实存在(已编译),但由于构建缓存状态陈旧,启动时的 classpath 未能正确加载到它,属于典型的“编译残留”问题。

## 解决办法

清理模组编译残留并重新编译。**关键点:必须保留 `build/rfg`**,该目录存放 Minecraft 反编译产物(`recompiled_minecraft-1.12.2.jar`、`mcp_patched_minecraft-sources.jar`、`minecraft-src`、`launcher-src`),重建极其耗时。而本项目 `build.gradle` 未定制 `clean` 任务,`gradlew clean` 会连同 `build/rfg` 一起删除。

### 操作步骤

在项目根目录 `E:\模组开发\GTQT\GregTech` 下依次执行:

```powershell
# 1. 停止 Gradle 守护进程,释放文件锁、清空内存中的陈旧编译状态
./gradlew --stop

# 2. 将昂贵的 Minecraft 反编译缓存临时移出 build 目录
Move-Item "build/rfg" "rfg_cache_backup" -Force

# 3. 标准清理:删除 build/classes、build/resources、build/libs、build/tmp、build/generated 等所有模组编译残留
./gradlew clean

# 4. 把 rfg 缓存放回原位(若 build 目录已被删空需先重建)
if (-not (Test-Path "build")) { New-Item -ItemType Directory -Path "build" | Out-Null }
Move-Item "rfg_cache_backup" "build/rfg" -Force

# 5. 重新编译模组代码与资源
./gradlew classes processResources
```

完成后,在 IntelliJ 中**刷新 / Reload Gradle 项目**,再重新运行 `runClient` 即可。

## 验证结果

重新编译后(约 3 分钟,生成约 3194 个 class),再次 `runClient`,日志显示核心mod 正常加载,不再报 `ClassNotFoundException`:

```
[main/INFO] [FML]: Found a command line coremod : gregtech.mixins.GregTechMixinLoadingPlugin
[main/INFO] [FML]: Ignoring missing certificate for coremod GregTechMixinLoadingPlugin (...), as this is probably a dev workspace
[main/INFO] [GradleStart]: Injecting location in coremod gregtech.mixins.GregTechMixinLoadingPlugin
[main/INFO] [MixinBooter]: Loading early loader gregtech.mixins.GregTechMixinLoadingPlugin for its mixins.
[main/INFO] [MixinBooter]: Adding [mixins.gregtech.forge.json] mixin configuration.
[main/INFO] [MixinBooter]: Adding [mixins.gregtech.minecraft.json] mixin configuration.
```

模组正常注册并显示在 mod 列表中,问题解决。

## 快速核对清单

排查同类问题时,可确认以下新鲜产物是否都已生成:

- `build/classes/java/main/gregtech/mixins/GregTechMixinLoadingPlugin.class`(核心mod 插件)
- `build/classes/java/main/gregtech/GregTechMod.class`(`@Mod` 主类)
- `build/resources/main/mcmod.info`
- `build/resources/main/mixins.gregtech.*.json`(全部 mixin 配置)

## 经验总结

- 本项目使用 RetroFuturaGradle(RFG),清理编译缓存时务必**先移出 `build/rfg` 再 `clean`**,避免重复反编译 Minecraft 浪费大量时间。
- 清理前先 `./gradlew --stop`,可避免文件被守护进程占用导致删除失败,同时清除内存中的陈旧状态。
- 核心mod / mixin 相关的“模组不显示”“`ClassNotFoundException`”问题,多数由构建缓存陈旧引起,清理重编译是首选排查手段。
